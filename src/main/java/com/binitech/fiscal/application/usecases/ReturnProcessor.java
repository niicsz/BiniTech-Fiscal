package com.binitech.fiscal.application.usecases;

import com.binitech.fiscal.application.ports.outbound.FiscalDocumentRepositoryPort;
import com.binitech.fiscal.application.ports.outbound.IssuerRepositoryPort;
import com.binitech.fiscal.application.ports.outbound.NumberSequencePort;
import com.binitech.fiscal.application.ports.outbound.ReturnDocumentRepositoryPort;
import com.binitech.fiscal.application.ports.outbound.SecretCipherPort;
import com.binitech.fiscal.application.ports.outbound.SefazPort;
import com.binitech.fiscal.application.ports.outbound.SefazPort.Credentials;
import com.binitech.fiscal.domain.FiscalDocument;
import com.binitech.fiscal.domain.FiscalStatus;
import com.binitech.fiscal.domain.Issuer;
import com.binitech.fiscal.domain.PreparedNfce;
import com.binitech.fiscal.domain.ReturnDocument;
import com.binitech.fiscal.domain.ReturnStatus;
import com.binitech.fiscal.domain.SefazOutcome;
import com.binitech.fiscal.domain.exception.BusinessException;
import com.binitech.fiscal.domain.exception.SefazCommunicationException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReturnProcessor {

  public static final String NFE_MODEL = "55";
  static final String FULL_RETURN_CANCEL_REASON = "Devolucao total da mercadoria pelo consumidor";
  static final Duration LEASE = Duration.ofSeconds(90);
  static final Duration WAITING_ORIGINAL_RETRY = Duration.ofMinutes(2);
  static final Duration MISSING_ORIGINAL_LIMIT = Duration.ofHours(1);
  static final Duration NOT_READY_RETRY = Duration.ofMinutes(15);
  static final Duration NORMAL_REBUILD_AGE = Duration.ofMinutes(4);
  static final Duration VOID_AFTER = Duration.ofHours(24);
  private static final List<Duration> BACKOFF =
      List.of(
          Duration.ofSeconds(5),
          Duration.ofSeconds(15),
          Duration.ofSeconds(30),
          Duration.ofMinutes(1),
          Duration.ofMinutes(2),
          Duration.ofMinutes(5),
          Duration.ofMinutes(15));
  private static final Set<String> REBUILD_AND_RETRY = Set.of("704");
  private static final Set<FiscalStatus> NOTHING_TO_REVERSE =
      Set.of(
          FiscalStatus.AWAITING_REQUEST,
          FiscalStatus.REJECTED,
          FiscalStatus.DENIED,
          FiscalStatus.VOIDED);
  private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
  private static final Logger log = LoggerFactory.getLogger(ReturnProcessor.class);

  private final ReturnDocumentRepositoryPort returns;
  private final FiscalDocumentRepositoryPort documents;
  private final IssuerRepositoryPort issuers;
  private final NumberSequencePort sequences;
  private final SefazPort sefaz;
  private final SecretCipherPort cipher;
  private final Clock clock;
  private final Duration cancelWindow;
  private final SecureRandom random = new SecureRandom();

  public ReturnProcessor(
      ReturnDocumentRepositoryPort returns,
      FiscalDocumentRepositoryPort documents,
      IssuerRepositoryPort issuers,
      NumberSequencePort sequences,
      SefazPort sefaz,
      SecretCipherPort cipher,
      Clock clock,
      Duration cancelWindow) {
    this.returns = returns;
    this.documents = documents;
    this.issuers = issuers;
    this.sequences = sequences;
    this.sefaz = sefaz;
    this.cipher = cipher;
    this.clock = clock;
    this.cancelWindow = cancelWindow;
  }

  public int processDue(int maxDocuments) {
    int processed = 0;
    while (processed < maxDocuments) {
      Optional<ReturnDocument> claimed = returns.claimNextDue(clock.instant(), LEASE);
      if (claimed.isEmpty()) {
        break;
      }
      process(claimed.get());
      processed++;
    }
    return processed;
  }

  public void processNow(String id) {
    returns.claim(id, clock.instant(), LEASE).ifPresent(this::process);
  }

  void process(ReturnDocument document) {
    try {
      Optional<Issuer> issuer = issuers.findByTenantId(document.getTenantId());
      if (issuer.isEmpty()
          || !issuer.get().isReadyToIssue(LocalDate.now(clock.withZone(BRASILIA)))) {
        reschedule(
            document, NOT_READY_RETRY, "Configuração fiscal incompleta ou certificado A1 vencido.");
        return;
      }
      Credentials credentials = credentials(issuer.get());
      switch (document.getStatus()) {
        case WAITING_ORIGINAL -> resolveOriginal(issuer.get(), credentials, document);
        case PENDING -> processPending(issuer.get(), credentials, document);
        case REJECTED -> voidAbandonedNumber(issuer.get(), credentials, document);
        default -> release(document);
      }
    } catch (SefazCommunicationException e) {
      onUnavailable(document, e.isPossiblyDelivered(), e.getMessage());
    } catch (BusinessException e) {
      markRejected(document, SefazOutcome.of(SefazOutcome.Kind.REJECTED, "000", e.getMessage()));
    } catch (RuntimeException e) {
      log.error(
          "Falha inesperada ao processar a devolução fiscal: returnId={} erro={}",
          document.getReturnId(),
          e.getMessage(),
          e);
      reschedule(document, backoff(document.getAttempts() + 1), "Erro interno: " + e.getMessage());
    }
  }

  private void resolveOriginal(Issuer issuer, Credentials credentials, ReturnDocument document) {
    Optional<FiscalDocument> found =
        documents.findByTenantIdAndSaleId(document.getTenantId(), document.getSaleId());
    if (found.isEmpty()) {
      if (createdBefore(document, MISSING_ORIGINAL_LIMIT)) {
        finish(document, ReturnStatus.NOT_REQUIRED, "A venda não tem NFC-e no serviço fiscal.");
      } else {
        reschedule(
            document, WAITING_ORIGINAL_RETRY, "Aguardando a venda chegar ao serviço fiscal.");
      }
      return;
    }
    FiscalDocument original = found.get();
    if (NOTHING_TO_REVERSE.contains(original.getStatus())) {
      finish(
          document,
          ReturnStatus.NOT_REQUIRED,
          "A venda não tem NFC-e autorizada; não há nota de devolução a emitir.");
      return;
    }
    if (original.getStatus() == FiscalStatus.CANCELLED) {
      finish(document, ReturnStatus.NOT_REQUIRED, "A NFC-e da venda já está cancelada.");
      return;
    }
    if (original.getStatus() != FiscalStatus.AUTHORIZED) {
      reschedule(
          document,
          WAITING_ORIGINAL_RETRY,
          "Aguardando a NFC-e da venda ser autorizada pela SEFAZ.");
      return;
    }
    document.setReferencedChave(original.getChave());
    document.setReferencedNumero(original.getNumero());
    if (canCancelOriginal(document, original) && cancelOriginal(issuer, credentials, original)) {
      finish(
          document,
          ReturnStatus.NFCE_CANCELLED,
          "Devolução total dentro do prazo: a NFC-e da venda foi cancelada.");
      return;
    }
    document.setStatus(ReturnStatus.PENDING);
    document.setLastError(null);
    processPending(issuer, credentials, document);
  }

  private boolean canCancelOriginal(ReturnDocument document, FiscalDocument original) {
    if (!document.getSnapshot().fullReturn() || original.getAuthorizedAt() == null) {
      return false;
    }
    boolean onlyReturn =
        returns.findByTenantIdAndSaleId(document.getTenantId(), document.getSaleId()).stream()
            .allMatch(other -> other.getReturnId().equals(document.getReturnId()));
    return onlyReturn && original.getAuthorizedAt().plus(cancelWindow).isAfter(clock.instant());
  }

  private boolean cancelOriginal(Issuer issuer, Credentials credentials, FiscalDocument original) {
    SefazOutcome outcome =
        sefaz.cancel(
            issuer,
            credentials,
            original.getChave(),
            original.getProtocolo(),
            FULL_RETURN_CANCEL_REASON);
    if (outcome.kind() != SefazOutcome.Kind.AUTHORIZED) {
      log.info(
          "Cancelamento da NFC-e recusado, segue com NF-e de devolução: saleId={} cStat={}",
          original.getSaleId(),
          outcome.cStat());
      return false;
    }
    original.setStatus(FiscalStatus.CANCELLED);
    original.setCancelReason(FULL_RETURN_CANCEL_REASON);
    original.setCancelProtocol(outcome.protocolo());
    original.setCancelXml(outcome.xmlProc());
    original.setCancelledAt(clock.instant());
    original.setUpdatedAt(clock.instant());
    documents.save(original);
    log.info("NFC-e cancelada por devolução total: saleId={}", original.getSaleId());
    return true;
  }

  private void processPending(Issuer issuer, Credentials credentials, ReturnDocument document) {
    if (document.isDeliveryUncertain() && document.getChave() != null) {
      SefazOutcome consulted =
          sefaz.consultNfe(issuer, credentials, document.getChave(), document.getSignedXml());
      switch (consulted.kind()) {
        case AUTHORIZED -> {
          markAuthorized(document, consulted);
          return;
        }
        case UNAVAILABLE -> {
          onUnavailable(document, true, consulted.xMotivo());
          return;
        }
        default -> document.setDeliveryUncertain(false);
      }
    }
    ensureNumber(issuer, document);
    if (needsRebuild(document)) {
      prepare(issuer, credentials, document);
    }
    send(issuer, credentials, document);
  }

  private void send(Issuer issuer, Credentials credentials, ReturnDocument document) {
    document.setAttempts(document.getAttempts() + 1);
    document.setDeliveryUncertain(true);
    returns.save(touch(document));
    SefazOutcome outcome = sefaz.authorizeNfe(issuer, credentials, document.getSignedXml());
    document.setDeliveryUncertain(false);
    switch (outcome.kind()) {
      case AUTHORIZED -> markAuthorized(document, outcome);
      case DUPLICATE -> resolveDuplicate(issuer, credentials, document, outcome);
      case DENIED -> markRejected(document, outcome);
      case REJECTED -> {
        if (REBUILD_AND_RETRY.contains(outcome.cStat())) {
          document.setSignedXml(null);
          reschedule(document, Duration.ZERO, outcome.cStat() + " - " + outcome.xMotivo());
        } else {
          markRejected(document, outcome);
        }
      }
      case UNAVAILABLE, NOT_FOUND -> onUnavailable(document, false, outcome.xMotivo());
    }
  }

  private void resolveDuplicate(
      Issuer issuer, Credentials credentials, ReturnDocument document, SefazOutcome outcome) {
    document.setDeliveryUncertain(true);
    SefazOutcome consulted =
        sefaz.consultNfe(issuer, credentials, document.getChave(), document.getSignedXml());
    if (consulted.kind() == SefazOutcome.Kind.AUTHORIZED) {
      markAuthorized(document, consulted);
      return;
    }
    document.setDeliveryUncertain(false);
    markRejected(document, outcome);
  }

  private void ensureNumber(Issuer issuer, ReturnDocument document) {
    if (document.getNumero() != null && document.getEnvironment() == issuer.getEnvironment()) {
      return;
    }
    document.setEnvironment(issuer.getEnvironment());
    document.setSerie(issuer.getNfeSerie());
    document.setNumero(
        sequences.next(
            issuer.getTenantId(), issuer.getEnvironment(), NFE_MODEL, issuer.getNfeSerie()));
    document.setCnf(String.format("%08d", random.nextInt(100_000_000)));
    document.setSignedXml(null);
    returns.save(touch(document));
  }

  private boolean needsRebuild(ReturnDocument document) {
    if (document.getSignedXml() == null || document.getIssuedAt() == null) {
      return true;
    }
    return !document.isDeliveryUncertain()
        && document.getIssuedAt().plus(NORMAL_REBUILD_AGE).isBefore(clock.instant());
  }

  private void prepare(Issuer issuer, Credentials credentials, ReturnDocument document) {
    document.setIssuedAt(clock.instant());
    PreparedNfce prepared = sefaz.prepareReturn(issuer, credentials, document);
    document.setChave(prepared.chave());
    document.setSignedXml(prepared.signedXml());
    document.setTotal(document.getSnapshot().itemsTotal());
  }

  private void voidAbandonedNumber(
      Issuer issuer, Credentials credentials, ReturnDocument document) {
    boolean abandoned =
        document.getNumero() != null
            && document.getUpdatedAt() != null
            && document.getUpdatedAt().plus(VOID_AFTER).isBefore(clock.instant());
    if (!abandoned) {
      release(document);
      return;
    }
    SefazOutcome outcome =
        sefaz.voidNfeNumber(
            issuer,
            credentials,
            document.getSerie(),
            document.getNumero(),
            "Numeracao nao utilizada por rejeicao da NF-e de devolucao na SEFAZ");
    if (outcome.kind() == SefazOutcome.Kind.AUTHORIZED) {
      document.setStatus(ReturnStatus.VOIDED);
      document.setCStat(outcome.cStat());
      document.setXMotivo(outcome.xMotivo());
      document.setNextAttemptAt(null);
      document.setLockedUntil(null);
      returns.save(touch(document));
      return;
    }
    reschedule(document, Duration.ofHours(1), outcome.cStat() + " - " + outcome.xMotivo());
  }

  private void markAuthorized(ReturnDocument document, SefazOutcome outcome) {
    document.setStatus(ReturnStatus.AUTHORIZED);
    document.setProtocolo(outcome.protocolo());
    if (outcome.xmlProc() != null) {
      document.setAuthorizedXml(outcome.xmlProc());
    }
    document.setCStat(outcome.cStat());
    document.setXMotivo(outcome.xMotivo());
    document.setAuthorizedAt(clock.instant());
    document.setDeliveryUncertain(false);
    document.setLastError(null);
    document.setNextAttemptAt(null);
    document.setLockedUntil(null);
    returns.save(touch(document));
    log.info(
        "NF-e de devolução autorizada: returnId={} numero={} chave={}",
        document.getReturnId(),
        document.getNumero(),
        document.getChave());
  }

  private void markRejected(ReturnDocument document, SefazOutcome outcome) {
    document.setStatus(ReturnStatus.REJECTED);
    document.setCStat(outcome.cStat());
    document.setXMotivo(outcome.xMotivo());
    document.setLastError(null);
    document.setSignedXml(null);
    document.setNextAttemptAt(clock.instant().plus(VOID_AFTER).plusSeconds(60));
    document.setLockedUntil(null);
    returns.save(touch(document));
    log.warn(
        "NF-e de devolução rejeitada: returnId={} cStat={} motivo={}",
        document.getReturnId(),
        outcome.cStat(),
        outcome.xMotivo());
  }

  private void onUnavailable(ReturnDocument document, boolean possiblyDelivered, String reason) {
    document.setDeliveryUncertain(possiblyDelivered);
    reschedule(document, backoff(document.getAttempts()), reason);
  }

  private void finish(ReturnDocument document, ReturnStatus status, String message) {
    document.setStatus(status);
    document.setLastError(message);
    document.setNextAttemptAt(null);
    document.setLockedUntil(null);
    returns.save(touch(document));
  }

  private void reschedule(ReturnDocument document, Duration delay, String reason) {
    document.setLastError(reason);
    document.setNextAttemptAt(clock.instant().plus(delay));
    document.setLockedUntil(null);
    returns.save(touch(document));
  }

  private void release(ReturnDocument document) {
    document.setLockedUntil(null);
    returns.save(touch(document));
  }

  private boolean createdBefore(ReturnDocument document, Duration age) {
    return document.getCreatedAt() != null
        && document.getCreatedAt().plus(age).isBefore(clock.instant());
  }

  private ReturnDocument touch(ReturnDocument document) {
    document.setUpdatedAt(clock.instant());
    return document;
  }

  private Credentials credentials(Issuer issuer) {
    return new Credentials(
        cipher.decrypt(issuer.getCertificate().encryptedPfx()),
        new String(
            cipher.decrypt(issuer.getCertificate().encryptedPassword()), StandardCharsets.UTF_8));
  }

  static Duration backoff(int attempts) {
    int index = Math.max(0, Math.min(attempts - 1, BACKOFF.size() - 1));
    return BACKOFF.get(index);
  }
}
