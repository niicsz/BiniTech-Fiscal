package com.binitech.fiscal.application.usecases;

import com.binitech.fiscal.application.ports.outbound.FiscalDocumentRepositoryPort;
import com.binitech.fiscal.application.ports.outbound.IssuerRepositoryPort;
import com.binitech.fiscal.application.ports.outbound.NumberSequencePort;
import com.binitech.fiscal.application.ports.outbound.SecretCipherPort;
import com.binitech.fiscal.application.ports.outbound.SefazPort;
import com.binitech.fiscal.application.ports.outbound.SefazPort.Credentials;
import com.binitech.fiscal.domain.FiscalDocument;
import com.binitech.fiscal.domain.FiscalStatus;
import com.binitech.fiscal.domain.Issuer;
import com.binitech.fiscal.domain.PreparedNfce;
import com.binitech.fiscal.domain.SefazOutcome;
import com.binitech.fiscal.domain.exception.BusinessException;
import com.binitech.fiscal.domain.exception.SefazCommunicationException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NfceProcessor {

  public static final String TP_EMIS_NORMAL = "1";
  public static final String TP_EMIS_OFFLINE = "9";
  static final Duration LEASE = Duration.ofSeconds(90);
  static final Duration NORMAL_REBUILD_AGE = Duration.ofMinutes(4);
  static final Duration CONTINGENCY_RETRY = Duration.ofMinutes(2);
  static final Duration NOT_READY_RETRY = Duration.ofMinutes(15);
  static final Duration VOID_AFTER = Duration.ofHours(24);
  static final int CONTINGENCY_AFTER_ATTEMPTS = 3;
  static final Duration CONTINGENCY_AFTER = Duration.ofSeconds(15);
  private static final List<Duration> BACKOFF =
      List.of(
          Duration.ofSeconds(2),
          Duration.ofSeconds(5),
          Duration.ofSeconds(15),
          Duration.ofSeconds(30),
          Duration.ofMinutes(1),
          Duration.ofMinutes(2),
          Duration.ofMinutes(5));
  private static final Set<String> REBUILD_AND_RETRY = Set.of("704");
  private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
  private static final Logger log = LoggerFactory.getLogger(NfceProcessor.class);

  private final FiscalDocumentRepositoryPort documents;
  private final IssuerRepositoryPort issuers;
  private final NumberSequencePort sequences;
  private final SefazPort sefaz;
  private final SecretCipherPort cipher;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public NfceProcessor(
      FiscalDocumentRepositoryPort documents,
      IssuerRepositoryPort issuers,
      NumberSequencePort sequences,
      SefazPort sefaz,
      SecretCipherPort cipher,
      Clock clock) {
    this.documents = documents;
    this.issuers = issuers;
    this.sequences = sequences;
    this.sefaz = sefaz;
    this.cipher = cipher;
    this.clock = clock;
  }

  public int processDue(int maxDocuments) {
    int processed = 0;
    while (processed < maxDocuments) {
      Optional<FiscalDocument> claimed = documents.claimNextDue(clock.instant(), LEASE);
      if (claimed.isEmpty()) {
        break;
      }
      process(claimed.get());
      processed++;
    }
    return processed;
  }

  public void processNow(String documentId) {
    documents.claim(documentId, clock.instant(), LEASE).ifPresent(this::process);
  }

  void process(FiscalDocument document) {
    try {
      Optional<Issuer> issuer = issuers.findByTenantId(document.getTenantId());
      if (issuer.isEmpty()
          || !issuer.get().isReadyToIssue(LocalDate.now(clock.withZone(BRASILIA)))) {
        postpone(
            document, "Configuração fiscal incompleta ou certificado A1 vencido.", NOT_READY_RETRY);
        return;
      }
      Credentials credentials = credentials(issuer.get());
      switch (document.getStatus()) {
        case PENDING -> processPending(issuer.get(), credentials, document);
        case CONTINGENCY -> transmitContingency(issuer.get(), credentials, document);
        case REJECTED -> voidAbandonedNumber(issuer.get(), credentials, document);
        default -> release(document);
      }
    } catch (SefazCommunicationException e) {
      onUnavailable(document, e.isPossiblyDelivered(), e.getMessage());
    } catch (BusinessException e) {
      markRejected(document, SefazOutcome.of(SefazOutcome.Kind.REJECTED, "000", e.getMessage()));
    } catch (RuntimeException e) {
      log.error(
          "Falha inesperada ao processar NFC-e: saleId={} erro={}",
          document.getSaleId(),
          e.getMessage(),
          e);
      postpone(document, "Erro interno: " + e.getMessage(), backoff(document.getAttempts() + 1));
    }
  }

  private void processPending(Issuer issuer, Credentials credentials, FiscalDocument document) {
    if (document.isDeliveryUncertain() && document.getChave() != null) {
      Optional<SefazOutcome> consultedOutcome =
          consultKeepingUncertainty(issuer, credentials, document);
      if (consultedOutcome.isEmpty()) {
        return;
      }
      SefazOutcome consulted = consultedOutcome.get();
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
      prepare(issuer, credentials, document, TP_EMIS_NORMAL);
    }
    send(issuer, credentials, document);
  }

  private void send(Issuer issuer, Credentials credentials, FiscalDocument document) {
    document.setAttempts(document.getAttempts() + 1);
    document.setDeliveryUncertain(true);
    documents.save(touch(document));
    SefazOutcome outcome;
    try {
      outcome = sefaz.authorize(issuer, credentials, document.getSignedXml());
    } catch (SefazCommunicationException e) {
      onUnavailable(document, e.isPossiblyDelivered(), e.getMessage());
      return;
    }
    document.setDeliveryUncertain(false);
    handleOutcome(issuer, credentials, document, outcome);
  }

  private Optional<SefazOutcome> consultKeepingUncertainty(
      Issuer issuer, Credentials credentials, FiscalDocument document) {
    try {
      return Optional.of(
          sefaz.consult(issuer, credentials, document.getChave(), document.getSignedXml()));
    } catch (SefazCommunicationException e) {
      onUnavailable(document, true, e.getMessage());
      return Optional.empty();
    }
  }

  private void transmitContingency(
      Issuer issuer, Credentials credentials, FiscalDocument document) {
    if (document.isDeliveryUncertain()) {
      Optional<SefazOutcome> consultedOutcome =
          consultKeepingUncertainty(issuer, credentials, document);
      if (consultedOutcome.isEmpty()) {
        return;
      }
      SefazOutcome consulted = consultedOutcome.get();
      if (consulted.kind() == SefazOutcome.Kind.AUTHORIZED) {
        markAuthorized(document, consulted);
        return;
      }
      if (consulted.kind() == SefazOutcome.Kind.UNAVAILABLE) {
        reschedule(document, CONTINGENCY_RETRY, consulted.xMotivo());
        return;
      }
      document.setDeliveryUncertain(false);
    }
    send(issuer, credentials, document);
  }

  private void handleOutcome(
      Issuer issuer, Credentials credentials, FiscalDocument document, SefazOutcome outcome) {
    switch (outcome.kind()) {
      case AUTHORIZED -> markAuthorized(document, outcome);
      case DUPLICATE -> {
        document.setDeliveryUncertain(true);
        consultKeepingUncertainty(issuer, credentials, document)
            .ifPresent(
                consulted -> {
                  if (consulted.kind() == SefazOutcome.Kind.AUTHORIZED) {
                    markAuthorized(document, consulted);
                  } else {
                    document.setDeliveryUncertain(false);
                    markRejected(document, outcome);
                  }
                });
      }
      case DENIED -> finish(document, FiscalStatus.DENIED, outcome);
      case REJECTED -> {
        if (document.getStatus() == FiscalStatus.PENDING
            && REBUILD_AND_RETRY.contains(outcome.cStat())) {
          document.setSignedXml(null);
          reschedule(document, Duration.ZERO, outcome.cStat() + " - " + outcome.xMotivo());
        } else {
          markRejected(document, outcome);
        }
      }
      case UNAVAILABLE, NOT_FOUND -> onUnavailable(document, false, outcome.xMotivo());
    }
  }

  private void onUnavailable(FiscalDocument document, boolean possiblyDelivered, String reason) {
    Instant now = clock.instant();
    if (document.getUnavailableSince() == null) {
      document.setUnavailableSince(now);
    }
    document.setDeliveryUncertain(possiblyDelivered);
    document.setLastError(reason);
    if (document.getStatus() == FiscalStatus.CONTINGENCY) {
      reschedule(document, CONTINGENCY_RETRY, reason);
      return;
    }
    if (shouldEnterContingency(document, now)) {
      try {
        enterContingency(document);
        return;
      } catch (RuntimeException e) {
        log.error(
            "Falha ao emitir NFC-e em contingência: saleId={} erro={}",
            document.getSaleId(),
            e.getMessage());
      }
    }
    reschedule(document, backoff(document.getAttempts()), reason);
  }

  private boolean shouldEnterContingency(FiscalDocument document, Instant now) {
    if (document.isDeliveryUncertain() || document.getStatus() != FiscalStatus.PENDING) {
      return false;
    }
    Optional<Issuer> issuer = issuers.findByTenantId(document.getTenantId());
    if (issuer.isEmpty() || !issuer.get().isContingencyEnabled()) {
      return false;
    }
    Duration down = Duration.between(document.getUnavailableSince(), now);
    return document.getAttempts() >= CONTINGENCY_AFTER_ATTEMPTS
        || down.compareTo(CONTINGENCY_AFTER) >= 0;
  }

  private void enterContingency(FiscalDocument document) {
    Issuer issuer = issuers.findByTenantId(document.getTenantId()).orElseThrow();
    Credentials credentials = credentials(issuer);
    prepare(issuer, credentials, document, TP_EMIS_OFFLINE);
    document.setStatus(FiscalStatus.CONTINGENCY);
    document.setAttempts(0);
    document.setDeliveryUncertain(false);
    log.warn(
        "SEFAZ indisponível; NFC-e emitida em contingência offline: saleId={} chave={}",
        document.getSaleId(),
        document.getChave());
    reschedule(
        document, CONTINGENCY_RETRY, "Emitida em contingência offline (SEFAZ indisponível).");
  }

  private void voidAbandonedNumber(
      Issuer issuer, Credentials credentials, FiscalDocument document) {
    Instant now = clock.instant();
    boolean abandoned =
        document.getNumero() != null
            && document.getUpdatedAt() != null
            && document.getUpdatedAt().plus(VOID_AFTER).isBefore(now);
    if (!abandoned) {
      release(document);
      return;
    }
    SefazOutcome outcome =
        sefaz.voidNumber(
            issuer,
            credentials,
            document.getSerie(),
            document.getNumero(),
            "Numeracao nao utilizada por rejeicao da NFC-e na SEFAZ");
    if (outcome.kind() == SefazOutcome.Kind.AUTHORIZED) {
      document.setStatus(FiscalStatus.VOIDED);
      document.setCStat(outcome.cStat());
      document.setXMotivo(outcome.xMotivo());
      document.setNextAttemptAt(null);
      document.setLockedUntil(null);
      documents.save(touch(document));
      return;
    }
    reschedule(document, Duration.ofHours(1), outcome.cStat() + " - " + outcome.xMotivo());
  }

  private void ensureNumber(Issuer issuer, FiscalDocument document) {
    if (document.getNumero() != null && document.getEnvironment() == issuer.getEnvironment()) {
      return;
    }
    document.setEnvironment(issuer.getEnvironment());
    document.setSerie(issuer.getSerie());
    document.setNumero(
        sequences.next(issuer.getTenantId(), issuer.getEnvironment(), issuer.getSerie()));
    document.setCnf(String.format("%08d", random.nextInt(100_000_000)));
    document.setSignedXml(null);
    documents.save(touch(document));
  }

  private boolean needsRebuild(FiscalDocument document) {
    if (document.getSignedXml() == null || document.getIssuedAt() == null) {
      return true;
    }
    return !document.isDeliveryUncertain()
        && document.getIssuedAt().plus(NORMAL_REBUILD_AGE).isBefore(clock.instant());
  }

  private void prepare(
      Issuer issuer, Credentials credentials, FiscalDocument document, String tpEmis) {
    document.setTpEmis(tpEmis);
    document.setIssuedAt(clock.instant());
    PreparedNfce prepared = sefaz.prepare(issuer, credentials, document);
    document.setChave(prepared.chave());
    document.setSignedXml(prepared.signedXml());
    document.setQrCode(prepared.qrCode());
    document.setUrlChave(prepared.urlChave());
    document.setTotal(document.getSale().itemsTotal());
  }

  private void markAuthorized(FiscalDocument document, SefazOutcome outcome) {
    document.setStatus(FiscalStatus.AUTHORIZED);
    document.setProtocolo(outcome.protocolo());
    if (outcome.xmlProc() != null) {
      document.setAuthorizedXml(outcome.xmlProc());
    }
    document.setCStat(outcome.cStat());
    document.setXMotivo(outcome.xMotivo());
    document.setAuthorizedAt(clock.instant());
    document.setDeliveryUncertain(false);
    document.setUnavailableSince(null);
    document.setLastError(null);
    document.setNextAttemptAt(null);
    document.setLockedUntil(null);
    documents.save(touch(document));
    log.info(
        "NFC-e autorizada: saleId={} numero={} chave={}",
        document.getSaleId(),
        document.getNumero(),
        document.getChave());
  }

  private void markRejected(FiscalDocument document, SefazOutcome outcome) {
    boolean wasContingency = document.getStatus() == FiscalStatus.CONTINGENCY;
    document.setStatus(FiscalStatus.REJECTED);
    document.setCStat(outcome.cStat());
    document.setXMotivo(outcome.xMotivo());
    document.setLastError(
        wasContingency
            ? "NFC-e emitida em contingência foi rejeitada na transmissão. Corrija e emita de novo."
            : null);
    if (!wasContingency) {
      document.setSignedXml(null);
    }
    document.setNextAttemptAt(clock.instant().plus(VOID_AFTER).plusSeconds(60));
    document.setLockedUntil(null);
    documents.save(touch(document));
    log.warn(
        "NFC-e rejeitada: saleId={} cStat={} motivo={}",
        document.getSaleId(),
        outcome.cStat(),
        outcome.xMotivo());
  }

  private void finish(FiscalDocument document, FiscalStatus status, SefazOutcome outcome) {
    document.setStatus(status);
    document.setCStat(outcome.cStat());
    document.setXMotivo(outcome.xMotivo());
    document.setNextAttemptAt(null);
    document.setLockedUntil(null);
    documents.save(touch(document));
  }

  private void postpone(FiscalDocument document, String reason, Duration delay) {
    document.setLastError(reason);
    reschedule(document, delay, reason);
  }

  private void reschedule(FiscalDocument document, Duration delay, String reason) {
    document.setLastError(reason);
    document.setNextAttemptAt(clock.instant().plus(delay));
    document.setLockedUntil(null);
    documents.save(touch(document));
  }

  private void release(FiscalDocument document) {
    document.setLockedUntil(null);
    documents.save(touch(document));
  }

  private FiscalDocument touch(FiscalDocument document) {
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
