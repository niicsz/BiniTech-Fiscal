package com.binitech.fiscal.application.usecases;

import com.binitech.fiscal.application.ports.inbound.ReturnUseCase;
import com.binitech.fiscal.application.ports.outbound.DanfePort;
import com.binitech.fiscal.application.ports.outbound.IssuerRepositoryPort;
import com.binitech.fiscal.application.ports.outbound.ReturnDocumentRepositoryPort;
import com.binitech.fiscal.domain.ReturnDocument;
import com.binitech.fiscal.domain.ReturnSnapshot;
import com.binitech.fiscal.domain.ReturnStatus;
import com.binitech.fiscal.domain.SaleSnapshot;
import com.binitech.fiscal.domain.exception.BusinessException;
import com.binitech.fiscal.domain.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReturnService implements ReturnUseCase {

  private static final Logger log = LoggerFactory.getLogger(ReturnService.class);

  private final ReturnDocumentRepositoryPort returns;
  private final IssuerRepositoryPort issuers;
  private final DanfePort danfe;
  private final ReturnProcessor processor;
  private final Executor executor;
  private final Clock clock;

  public ReturnService(
      ReturnDocumentRepositoryPort returns,
      IssuerRepositoryPort issuers,
      DanfePort danfe,
      ReturnProcessor processor,
      Executor executor,
      Clock clock) {
    this.returns = returns;
    this.issuers = issuers;
    this.danfe = danfe;
    this.processor = processor;
    this.executor = executor;
    this.clock = clock;
  }

  @Override
  public void registerReturn(ReturnSnapshot snapshot) {
    validate(snapshot);
    if (issuers.findByTenantId(snapshot.tenantId()).isEmpty()) {
      return;
    }
    ReturnDocument document = new ReturnDocument();
    document.setTenantId(snapshot.tenantId());
    document.setSaleId(snapshot.saleId());
    document.setReturnId(snapshot.returnId());
    document.setSnapshot(snapshot);
    document.setConsumerCpf(cpfOrNull(snapshot.consumerCpf()));
    document.setTotal(snapshot.itemsTotal());
    document.setStatus(ReturnStatus.WAITING_ORIGINAL);
    document.setNextAttemptAt(clock.instant());
    document.setCreatedAt(clock.instant());
    document.setUpdatedAt(clock.instant());
    returns.insertIfAbsent(document).ifPresent(this::processAsync);
  }

  @Override
  public List<ReturnDocument> list(String tenantId, String saleId) {
    return returns.findByTenantIdAndSaleId(tenantId, saleId);
  }

  @Override
  public ReturnDocument retry(String tenantId, String returnId) {
    ReturnDocument document = require(tenantId, returnId);
    switch (document.getStatus()) {
      case REJECTED, VOIDED -> {
        if (document.getStatus() == ReturnStatus.VOIDED) {
          document.setNumero(null);
        }
        document.setStatus(ReturnStatus.PENDING);
        document.setSignedXml(null);
        document.setAttempts(0);
        document.setCStat(null);
        document.setXMotivo(null);
        document.setLastError(null);
      }
      case WAITING_ORIGINAL, PENDING -> document.setLastError(null);
      default -> {
        return document;
      }
    }
    document.setNextAttemptAt(clock.instant());
    document.setUpdatedAt(clock.instant());
    ReturnDocument saved = returns.save(document);
    processAsync(saved);
    return saved;
  }

  @Override
  public byte[] danfe(String tenantId, String returnId) {
    ReturnDocument document = require(tenantId, returnId);
    if (document.getStatus() != ReturnStatus.AUTHORIZED) {
      throw new BusinessException("A NF-e de devolução ainda não foi autorizada pela SEFAZ.");
    }
    String xml =
        document.getAuthorizedXml() != null ? document.getAuthorizedXml() : document.getSignedXml();
    return danfe.renderNfe(xml);
  }

  private ReturnDocument require(String tenantId, String returnId) {
    return returns
        .findByTenantIdAndReturnId(tenantId, returnId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Devolução não encontrada no serviço fiscal."));
  }

  private void processAsync(ReturnDocument document) {
    executor.execute(
        () -> {
          try {
            processor.processNow(document.getId());
          } catch (RuntimeException e) {
            log.error(
                "Falha ao processar a devolução fiscal: returnId={}", document.getReturnId(), e);
          }
        });
  }

  private static void validate(ReturnSnapshot snapshot) {
    if (snapshot == null
        || isBlank(snapshot.tenantId())
        || isBlank(snapshot.saleId())
        || isBlank(snapshot.returnId())
        || snapshot.items() == null
        || snapshot.items().isEmpty()) {
      throw new BusinessException("Devolução inválida para o serviço fiscal.");
    }
    for (SaleSnapshot.Item item : snapshot.items()) {
      if (item.quantity() == null
          || item.quantity().signum() <= 0
          || item.total() == null
          || item.total().compareTo(BigDecimal.ZERO) <= 0) {
        throw new BusinessException("Item com quantidade ou valor inválido na devolução.");
      }
    }
  }

  private static String cpfOrNull(String value) {
    if (value == null) {
      return null;
    }
    String digits = value.replaceAll("\\D", "");
    return NfceService.validCpf(digits) ? digits : null;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
