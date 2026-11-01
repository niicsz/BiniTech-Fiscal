package com.binitech.fiscal.application.usecases;

import com.binitech.fiscal.application.ports.inbound.NfceUseCase;
import com.binitech.fiscal.application.ports.outbound.DanfePort;
import com.binitech.fiscal.application.ports.outbound.FiscalDocumentRepositoryPort;
import com.binitech.fiscal.application.ports.outbound.IssuerRepositoryPort;
import com.binitech.fiscal.application.ports.outbound.SecretCipherPort;
import com.binitech.fiscal.application.ports.outbound.SefazPort;
import com.binitech.fiscal.application.ports.outbound.SefazPort.Credentials;
import com.binitech.fiscal.domain.FiscalDocument;
import com.binitech.fiscal.domain.FiscalStatus;
import com.binitech.fiscal.domain.Issuer;
import com.binitech.fiscal.domain.SaleSnapshot;
import com.binitech.fiscal.domain.SefazOutcome;
import com.binitech.fiscal.domain.exception.BusinessException;
import com.binitech.fiscal.domain.exception.ResourceNotFoundException;
import com.binitech.fiscal.domain.exception.SefazCommunicationException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NfceService implements NfceUseCase {

  private static final Logger log = LoggerFactory.getLogger(NfceService.class);
  private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
  private static final int MIN_CANCEL_REASON = 15;
  private static final int MAX_CANCEL_REASON = 255;

  private final FiscalDocumentRepositoryPort documents;
  private final IssuerRepositoryPort issuers;
  private final SefazPort sefaz;
  private final DanfePort danfe;
  private final SecretCipherPort cipher;
  private final NfceProcessor processor;
  private final Executor executor;
  private final Clock clock;

  public NfceService(
      FiscalDocumentRepositoryPort documents,
      IssuerRepositoryPort issuers,
      SefazPort sefaz,
      DanfePort danfe,
      SecretCipherPort cipher,
      NfceProcessor processor,
      Executor executor,
      Clock clock) {
    this.documents = documents;
    this.issuers = issuers;
    this.sefaz = sefaz;
    this.danfe = danfe;
    this.cipher = cipher;
    this.processor = processor;
    this.executor = executor;
    this.clock = clock;
  }

  @Override
  public void registerSale(SaleSnapshot sale) {
    validate(sale);
    Optional<Issuer> issuer = issuers.findByTenantId(sale.tenantId());
    if (issuer.isEmpty()) {
      return;
    }
    FiscalDocument document = new FiscalDocument();
    document.setTenantId(sale.tenantId());
    document.setSaleId(sale.saleId());
    document.setSale(sale);
    document.setConsumerCpf(cpfOrNull(sale.consumerCpf()));
    document.setTotal(sale.itemsTotal());
    document.setEnvironment(issuer.get().getEnvironment());
    boolean autoIssue = issuer.get().isAutoIssue() && isReady(issuer.get());
    document.setStatus(autoIssue ? FiscalStatus.PENDING : FiscalStatus.AWAITING_REQUEST);
    document.setNextAttemptAt(autoIssue ? clock.instant() : null);
    document.setCreatedAt(clock.instant());
    document.setUpdatedAt(clock.instant());
    documents
        .insertIfAbsent(document)
        .filter(inserted -> inserted.getStatus() == FiscalStatus.PENDING)
        .ifPresent(this::processAsync);
  }

  @Override
  public FiscalDocument requestIssue(String tenantId, String saleId, String consumerCpf) {
    Issuer issuer =
        issuers
            .findByTenantId(tenantId)
            .filter(this::isReady)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "Configure os dados fiscais e um certificado A1 válido antes de emitir"
                            + " NFC-e."));
    FiscalDocument document =
        documents
            .findByTenantIdAndSaleId(tenantId, saleId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Venda ainda não recebida pelo serviço fiscal. Tente de novo em alguns"
                            + " segundos."));
    if (document.getStatus() != FiscalStatus.AWAITING_REQUEST
        && document.getStatus() != FiscalStatus.REJECTED) {
      return document;
    }
    if (document.getStatus() == FiscalStatus.REJECTED
        && document.getTpEmis() != null
        && NfceProcessor.TP_EMIS_OFFLINE.equals(document.getTpEmis())) {
      throw new BusinessException(
          "Esta NFC-e foi emitida em contingência e rejeitada. Fale com o suporte para regularizar.");
    }
    String cpf = normalizeCpf(consumerCpf);
    if (cpf != null) {
      document.setConsumerCpf(cpf);
      document.setSignedXml(null);
    }
    if (document.getStatus() == FiscalStatus.REJECTED) {
      document.setSignedXml(null);
    }
    document.setStatus(FiscalStatus.PENDING);
    document.setEnvironment(issuer.getEnvironment());
    document.setAttempts(0);
    document.setUnavailableSince(null);
    document.setCStat(null);
    document.setXMotivo(null);
    document.setLastError(null);
    document.setNextAttemptAt(clock.instant());
    document.setUpdatedAt(clock.instant());
    FiscalDocument saved = documents.save(document);
    processAsync(saved);
    return saved;
  }

  @Override
  public Optional<FiscalDocument> find(String tenantId, String saleId) {
    return documents.findByTenantIdAndSaleId(tenantId, saleId);
  }

  @Override
  public FiscalDocument cancel(String tenantId, String saleId, String reason) {
    String motivo = reason == null ? "" : reason.trim().replaceAll("\\s+", " ");
    if (motivo.length() < MIN_CANCEL_REASON || motivo.length() > MAX_CANCEL_REASON) {
      throw new BusinessException(
          "Informe o motivo do cancelamento com 15 a 255 caracteres (exigência da SEFAZ).");
    }
    Issuer issuer =
        issuers
            .findByTenantId(tenantId)
            .filter(this::isReady)
            .orElseThrow(() -> new BusinessException("Certificado A1 ausente ou vencido."));
    FiscalDocument document = require(tenantId, saleId);
    if (document.getStatus() == FiscalStatus.CANCELLED) {
      return document;
    }
    if (document.getStatus() != FiscalStatus.AUTHORIZED) {
      throw new BusinessException("Só é possível cancelar uma NFC-e autorizada pela SEFAZ.");
    }
    SefazOutcome outcome;
    try {
      outcome =
          sefaz.cancel(
              issuer, credentials(issuer), document.getChave(), document.getProtocolo(), motivo);
    } catch (SefazCommunicationException e) {
      throw new BusinessException("SEFAZ indisponível no momento. Tente cancelar de novo.");
    }
    if (outcome.kind() != SefazOutcome.Kind.AUTHORIZED) {
      throw new BusinessException(
          "A SEFAZ recusou o cancelamento: " + outcome.cStat() + " - " + outcome.xMotivo());
    }
    document.setStatus(FiscalStatus.CANCELLED);
    document.setCancelReason(motivo);
    document.setCancelProtocol(outcome.protocolo());
    document.setCancelXml(outcome.xmlProc());
    document.setCancelledAt(clock.instant());
    document.setUpdatedAt(clock.instant());
    log.info("NFC-e cancelada: saleId={} chave={}", saleId, document.getChave());
    return documents.save(document);
  }

  @Override
  public byte[] danfe(String tenantId, String saleId) {
    FiscalDocument document = require(tenantId, saleId);
    String xml =
        switch (document.getStatus()) {
          case AUTHORIZED, CANCELLED ->
              document.getAuthorizedXml() != null
                  ? document.getAuthorizedXml()
                  : document.getSignedXml();
          case CONTINGENCY -> document.getSignedXml();
          default -> null;
        };
    if (xml == null) {
      throw new BusinessException("A NFC-e ainda não foi autorizada pela SEFAZ.");
    }
    return danfe.render(xml, document.getUrlChave());
  }

  private void processAsync(FiscalDocument document) {
    executor.execute(
        () -> {
          try {
            processor.processNow(document.getId());
          } catch (RuntimeException e) {
            log.error("Falha ao processar NFC-e: saleId={}", document.getSaleId(), e);
          }
        });
  }

  private FiscalDocument require(String tenantId, String saleId) {
    return documents
        .findByTenantIdAndSaleId(tenantId, saleId)
        .orElseThrow(() -> new ResourceNotFoundException("NFC-e não encontrada para esta venda."));
  }

  private boolean isReady(Issuer issuer) {
    return issuer.isReadyToIssue(LocalDate.now(clock.withZone(BRASILIA)));
  }

  private Credentials credentials(Issuer issuer) {
    return new Credentials(
        cipher.decrypt(issuer.getCertificate().encryptedPfx()),
        new String(
            cipher.decrypt(issuer.getCertificate().encryptedPassword()), StandardCharsets.UTF_8));
  }

  private void validate(SaleSnapshot sale) {
    if (sale == null
        || isBlank(sale.tenantId())
        || isBlank(sale.saleId())
        || sale.items() == null
        || sale.items().isEmpty()) {
      throw new BusinessException("Venda inválida para emissão fiscal.");
    }
    for (SaleSnapshot.Item item : sale.items()) {
      if (item.quantity() == null
          || item.quantity().signum() <= 0
          || item.total() == null
          || item.total().compareTo(BigDecimal.ZERO) <= 0) {
        throw new BusinessException("Item com quantidade ou valor inválido na venda.");
      }
    }
  }

  private static String cpfOrNull(String value) {
    try {
      return normalizeCpf(value);
    } catch (BusinessException e) {
      return null;
    }
  }

  static String normalizeCpf(String value) {
    if (value == null) {
      return null;
    }
    String digits = value.replaceAll("\\D", "");
    if (digits.isEmpty()) {
      return null;
    }
    if (!validCpf(digits)) {
      throw new BusinessException("CPF do consumidor inválido.");
    }
    return digits;
  }

  static boolean validCpf(String cpf) {
    if (!cpf.matches("\\d{11}") || cpf.chars().distinct().count() == 1) {
      return false;
    }
    for (int length = 9; length <= 10; length++) {
      int sum = 0;
      for (int i = 0; i < length; i++) {
        sum += (cpf.charAt(i) - '0') * (length + 1 - i);
      }
      if ((sum * 10) % 11 % 10 != cpf.charAt(length) - '0') {
        return false;
      }
    }
    return true;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
