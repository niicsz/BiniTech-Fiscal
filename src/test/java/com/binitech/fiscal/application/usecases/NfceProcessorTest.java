package com.binitech.fiscal.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.binitech.fiscal.application.ports.outbound.FiscalDocumentRepositoryPort;
import com.binitech.fiscal.application.ports.outbound.IssuerRepositoryPort;
import com.binitech.fiscal.application.ports.outbound.SecretCipherPort;
import com.binitech.fiscal.application.ports.outbound.SefazPort;
import com.binitech.fiscal.domain.Address;
import com.binitech.fiscal.domain.CertificateInfo;
import com.binitech.fiscal.domain.FiscalDocument;
import com.binitech.fiscal.domain.FiscalStatus;
import com.binitech.fiscal.domain.Issuer;
import com.binitech.fiscal.domain.PreparedNfce;
import com.binitech.fiscal.domain.SaleSnapshot;
import com.binitech.fiscal.domain.SefazOutcome;
import com.binitech.fiscal.domain.SefazOutcome.Kind;
import com.binitech.fiscal.domain.StoredCertificate;
import com.binitech.fiscal.domain.TaxRegime;
import com.binitech.fiscal.domain.exception.BusinessException;
import com.binitech.fiscal.domain.exception.SefazCommunicationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NfceProcessorTest {

  private MutableClock clock;
  private InMemoryDocuments documents;
  private Issuer issuer;
  private FakeSefaz sefaz;
  private NfceProcessor processor;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-10-31T15:00:00Z"));
    documents = new InMemoryDocuments();
    issuer = issuer();
    sefaz = new FakeSefaz();
    IssuerRepositoryPort issuers =
        new IssuerRepositoryPort() {
          @Override
          public Issuer save(Issuer value) {
            return value;
          }

          @Override
          public Optional<Issuer> findByTenantId(String tenantId) {
            return Optional.of(issuer);
          }
        };
    int[] sequence = {0};
    processor =
        new NfceProcessor(
            documents,
            issuers,
            (tenant, env, serie) -> ++sequence[0],
            sefaz,
            new PlainCipher(),
            clock);
  }

  private static Issuer issuer() {
    Issuer issuer = new Issuer();
    issuer.setTenantId("t1");
    issuer.setCnpj("11222333000181");
    issuer.setInscricaoEstadual("123456789110");
    issuer.setRegime(TaxRegime.SIMPLES_NACIONAL);
    issuer.setAddress(
        new Address("Rua", "1", null, "Centro", "3534401", "Osasco", "SP", "06020000"));
    issuer.setSerie(1);
    issuer.setEnvironment(2);
    issuer.setContingencyEnabled(true);
    issuer.setCertificate(
        new StoredCertificate("pfx", "senha", "LOJA", LocalDate.of(2027, 12, 31)));
    return issuer;
  }

  private FiscalDocument pending() {
    FiscalDocument document = new FiscalDocument();
    document.setTenantId("t1");
    document.setSaleId("s1");
    document.setStatus(FiscalStatus.PENDING);
    document.setEnvironment(2);
    document.setNextAttemptAt(clock.instant());
    document.setSale(
        new SaleSnapshot(
            "t1",
            "s1",
            clock.instant(),
            null,
            null,
            List.of(
                new SaleSnapshot.Item(
                    "p1",
                    "Item",
                    null,
                    "22021000",
                    null,
                    null,
                    "UN",
                    BigDecimal.ONE,
                    BigDecimal.TEN,
                    BigDecimal.TEN)),
            List.of(new SaleSnapshot.Payment("CASH", BigDecimal.TEN))));
    return documents.save(document);
  }

  private FiscalDocument reload(FiscalDocument document) {
    return documents.byId.get(document.getId());
  }

  @Test
  @DisplayName("Autoriza na primeira tentativa, numera e guarda o protocolo")
  void authorizesOnFirstAttempt() {
    FiscalDocument document = pending();
    sefaz.authorizeResults.add(authorized());

    processor.processDue(10);

    FiscalDocument result = reload(document);
    assertThat(result.getStatus()).isEqualTo(FiscalStatus.AUTHORIZED);
    assertThat(result.getNumero()).isEqualTo(1);
    assertThat(result.getProtocolo()).isEqualTo("135260000000001");
    assertThat(result.getAuthorizedXml()).isEqualTo("<nfeProc/>");
    assertThat(result.isDeliveryUncertain()).isFalse();
    assertThat(result.getLockedUntil()).isNull();
  }

  @Test
  @DisplayName("Timeout depois do envio consulta a chave antes de reenviar e não duplica a nota")
  void ambiguousTimeoutConsultsBeforeResending() {
    FiscalDocument document = pending();
    sefaz.authorizeErrors.add(new SefazCommunicationException("timeout", true, null));
    sefaz.consultResults.add(authorized());

    processor.processDue(10);
    FiscalDocument afterTimeout = reload(document);
    assertThat(afterTimeout.getStatus()).isEqualTo(FiscalStatus.PENDING);
    assertThat(afterTimeout.isDeliveryUncertain()).isTrue();

    clock.advance(Duration.ofSeconds(3));
    processor.processDue(10);

    FiscalDocument result = reload(document);
    assertThat(result.getStatus()).isEqualTo(FiscalStatus.AUTHORIZED);
    assertThat(sefaz.authorizeCalls).isEqualTo(1);
    assertThat(sefaz.consultCalls).isEqualTo(1);
    assertThat(result.getNumero()).isEqualTo(1);
  }

  @Test
  @DisplayName("Consulta que não encontra a nota libera o reenvio com a mesma chave")
  void consultNotFoundResendsSameKey() {
    FiscalDocument document = pending();
    sefaz.authorizeErrors.add(new SefazCommunicationException("timeout", true, null));
    sefaz.consultResults.add(SefazOutcome.of(Kind.NOT_FOUND, "217", "NF-e nao consta"));
    sefaz.authorizeResults.add(authorized());

    processor.processDue(10);
    String firstKey = reload(document).getChave();
    clock.advance(Duration.ofSeconds(3));
    processor.processDue(10);

    FiscalDocument result = reload(document);
    assertThat(result.getStatus()).isEqualTo(FiscalStatus.AUTHORIZED);
    assertThat(result.getChave()).isEqualTo(firstKey);
    assertThat(sefaz.prepareCalls).isEqualTo(1);
  }

  @Test
  @DisplayName("SEFAZ fora do ar sem envio entra em contingência offline e transmite depois")
  void unavailableEntersContingencyAndTransmitsLater() {
    FiscalDocument document = pending();
    for (int i = 0; i < 3; i++) {
      sefaz.authorizeErrors.add(new SefazCommunicationException("connection refused", false, null));
    }

    for (int i = 0; i < 3; i++) {
      processor.processDue(10);
      clock.advance(Duration.ofSeconds(20));
    }

    FiscalDocument offline = reload(document);
    assertThat(offline.getStatus()).isEqualTo(FiscalStatus.CONTINGENCY);
    assertThat(offline.getTpEmis()).isEqualTo("9");
    assertThat(offline.getChave()).endsWith("9");
    assertThat(offline.getNumero()).isEqualTo(1);

    assertThat(sefaz.authorizeCalls).isEqualTo(2);
    sefaz.authorizeErrors.clear();
    sefaz.authorizeResults.add(authorized());
    clock.advance(Duration.ofMinutes(3));
    processor.processDue(10);

    FiscalDocument result = reload(document);
    assertThat(result.getStatus()).isEqualTo(FiscalStatus.AUTHORIZED);
    assertThat(result.getTpEmis()).isEqualTo("9");
  }

  @Test
  @DisplayName("Sem contingência habilitada continua tentando com espera crescente")
  void withoutContingencyKeepsRetrying() {
    issuer.setContingencyEnabled(false);
    FiscalDocument document = pending();
    for (int i = 0; i < 5; i++) {
      sefaz.authorizeResults.add(SefazOutcome.of(Kind.UNAVAILABLE, "108", "Servico paralisado"));
    }

    processor.processDue(10);
    Instant firstRetry = reload(document).getNextAttemptAt();
    clock.advance(Duration.ofSeconds(3));
    processor.processDue(10);
    Instant secondRetry = reload(document).getNextAttemptAt();

    assertThat(reload(document).getStatus()).isEqualTo(FiscalStatus.PENDING);
    assertThat(Duration.between(clock.instant(), secondRetry))
        .isGreaterThan(Duration.between(clock.instant().minusSeconds(3), firstRetry));
  }

  @Test
  @DisplayName("Duplicidade (539) é resolvida consultando a chave na SEFAZ")
  void duplicateResolvedByConsult() {
    FiscalDocument document = pending();
    sefaz.authorizeResults.add(SefazOutcome.of(Kind.DUPLICATE, "539", "Duplicidade de NF-e"));
    sefaz.consultResults.add(authorized());

    processor.processDue(10);

    assertThat(reload(document).getStatus()).isEqualTo(FiscalStatus.AUTHORIZED);
  }

  @Test
  @DisplayName("Rejeição guarda o motivo, mantém o número e permite reemitir")
  void rejectionKeepsNumber() {
    FiscalDocument document = pending();
    sefaz.authorizeResults.add(SefazOutcome.of(Kind.REJECTED, "778", "NCM inexistente"));

    processor.processDue(10);

    FiscalDocument result = reload(document);
    assertThat(result.getStatus()).isEqualTo(FiscalStatus.REJECTED);
    assertThat(result.getCStat()).isEqualTo("778");
    assertThat(result.getNumero()).isEqualTo(1);
    assertThat(result.getSignedXml()).isNull();
  }

  @Test
  @DisplayName("Rejeição 704 (emissão atrasada) remonta o XML e tenta de novo sozinha")
  void lateIssueRebuildsAndRetries() {
    FiscalDocument document = pending();
    sefaz.authorizeResults.add(SefazOutcome.of(Kind.REJECTED, "704", "NFC-e com data atrasada"));
    sefaz.authorizeResults.add(authorized());

    processor.processDue(10);
    processor.processDue(10);

    assertThat(reload(document).getStatus()).isEqualTo(FiscalStatus.AUTHORIZED);
    assertThat(sefaz.prepareCalls).isEqualTo(2);
  }

  @Test
  @DisplayName("Erro de dados na montagem (XSD/NCM) rejeita sem ficar em loop")
  void preparationErrorRejects() {
    FiscalDocument document = pending();
    sefaz.prepareError = new BusinessException("XML da NFC-e inválido: cProd");

    processor.processDue(10);

    FiscalDocument result = reload(document);
    assertThat(result.getStatus()).isEqualTo(FiscalStatus.REJECTED);
    assertThat(result.getXMotivo()).contains("cProd");
    assertThat(sefaz.authorizeCalls).isZero();
  }

  @Test
  @DisplayName("Número de nota rejeitada e abandonada é inutilizado depois de 24h")
  void abandonedRejectionIsVoided() {
    FiscalDocument document = pending();
    sefaz.authorizeResults.add(SefazOutcome.of(Kind.REJECTED, "778", "NCM inexistente"));
    processor.processDue(10);

    sefaz.voidResult = SefazOutcome.of(Kind.AUTHORIZED, "102", "Inutilizacao homologada");
    clock.advance(Duration.ofHours(25));
    processor.processDue(10);

    assertThat(reload(document).getStatus()).isEqualTo(FiscalStatus.VOIDED);
  }

  @Test
  @DisplayName("Loja sem certificado válido adia a emissão sem gastar número")
  void notReadyPostpones() {
    issuer.setCertificate(null);
    FiscalDocument document = pending();

    processor.processDue(10);

    FiscalDocument result = reload(document);
    assertThat(result.getStatus()).isEqualTo(FiscalStatus.PENDING);
    assertThat(result.getNumero()).isNull();
    assertThat(result.getNextAttemptAt()).isAfter(clock.instant().plusSeconds(600));
  }

  private static SefazOutcome authorized() {
    return new SefazOutcome(
        Kind.AUTHORIZED,
        "100",
        "Autorizado o uso da NF-e",
        "135260000000001",
        "2026-10-31T12:00:01-03:00",
        "<nfeProc/>");
  }

  static class FakeSefaz implements SefazPort {
    final Deque<SefazOutcome> authorizeResults = new ArrayDeque<>();
    final Deque<SefazCommunicationException> authorizeErrors = new ArrayDeque<>();
    final Deque<SefazOutcome> consultResults = new ArrayDeque<>();
    SefazOutcome voidResult;
    RuntimeException prepareError;
    int authorizeCalls;
    int consultCalls;
    int prepareCalls;

    @Override
    public CertificateInfo inspectCertificate(byte[] pfx, String password) {
      return null;
    }

    @Override
    public PreparedNfce prepare(Issuer issuer, Credentials credentials, FiscalDocument document) {
      prepareCalls++;
      if (prepareError != null) {
        throw prepareError;
      }
      String chave =
          "3526101122233300018165001"
              + String.format("%09d", document.getNumero())
              + document.getTpEmis()
              + document.getCnf();
      return new PreparedNfce(
          chave.substring(0, 43) + document.getTpEmis(), "<signed/>", "qr", "url");
    }

    @Override
    public SefazOutcome authorize(Issuer issuer, Credentials credentials, String signedXml) {
      authorizeCalls++;
      if (!authorizeErrors.isEmpty()) {
        throw authorizeErrors.poll();
      }
      return authorizeResults.poll();
    }

    @Override
    public SefazOutcome consult(Issuer issuer, Credentials credentials, String chave, String xml) {
      consultCalls++;
      return consultResults.poll();
    }

    @Override
    public SefazOutcome cancel(
        Issuer issuer, Credentials credentials, String chave, String protocolo, String reason) {
      return null;
    }

    @Override
    public SefazOutcome voidNumber(
        Issuer issuer, Credentials credentials, int serie, int numero, String reason) {
      return voidResult;
    }

    @Override
    public SefazOutcome serviceStatus(Issuer issuer, Credentials credentials) {
      return null;
    }
  }

  static class InMemoryDocuments implements FiscalDocumentRepositoryPort {
    final Map<String, FiscalDocument> byId = new HashMap<>();

    @Override
    public FiscalDocument save(FiscalDocument document) {
      if (document.getId() == null) {
        document.setId(UUID.randomUUID().toString());
      }
      byId.put(document.getId(), document);
      return document;
    }

    @Override
    public Optional<FiscalDocument> findByTenantIdAndSaleId(String tenantId, String saleId) {
      return byId.values().stream()
          .filter(d -> d.getTenantId().equals(tenantId) && d.getSaleId().equals(saleId))
          .findFirst();
    }

    @Override
    public Optional<FiscalDocument> insertIfAbsent(FiscalDocument document) {
      if (findByTenantIdAndSaleId(document.getTenantId(), document.getSaleId()).isPresent()) {
        return Optional.empty();
      }
      return Optional.of(save(document));
    }

    @Override
    public Optional<FiscalDocument> claimNextDue(Instant now, Duration lease) {
      List<FiscalDocument> due = new ArrayList<>(byId.values());
      due.sort(
          Comparator.comparing(
              FiscalDocument::getNextAttemptAt, Comparator.nullsLast(Comparator.naturalOrder())));
      for (FiscalDocument document : due) {
        boolean workable =
            document.getStatus() == FiscalStatus.PENDING
                || document.getStatus() == FiscalStatus.CONTINGENCY
                || document.getStatus() == FiscalStatus.REJECTED;
        boolean free = document.getLockedUntil() == null || document.getLockedUntil().isBefore(now);
        if (workable
            && free
            && document.getNextAttemptAt() != null
            && !document.getNextAttemptAt().isAfter(now)) {
          document.setLockedUntil(now.plus(lease));
          return Optional.of(document);
        }
      }
      return Optional.empty();
    }

    @Override
    public Optional<FiscalDocument> claim(String id, Instant now, Duration lease) {
      return Optional.ofNullable(byId.get(id));
    }
  }

  static class PlainCipher implements SecretCipherPort {
    @Override
    public String encrypt(byte[] plain) {
      return new String(plain);
    }

    @Override
    public byte[] decrypt(String cipherText) {
      return cipherText.getBytes();
    }
  }

  static class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return Clock.fixed(now, zone);
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
