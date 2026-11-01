package com.binitech.fiscal.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.binitech.fiscal.application.ports.outbound.IssuerRepositoryPort;
import com.binitech.fiscal.application.ports.outbound.ReturnDocumentRepositoryPort;
import com.binitech.fiscal.application.usecases.NfceProcessorTest.FakeSefaz;
import com.binitech.fiscal.application.usecases.NfceProcessorTest.InMemoryDocuments;
import com.binitech.fiscal.application.usecases.NfceProcessorTest.MutableClock;
import com.binitech.fiscal.application.usecases.NfceProcessorTest.PlainCipher;
import com.binitech.fiscal.domain.Address;
import com.binitech.fiscal.domain.FiscalDocument;
import com.binitech.fiscal.domain.FiscalStatus;
import com.binitech.fiscal.domain.Issuer;
import com.binitech.fiscal.domain.ReturnDocument;
import com.binitech.fiscal.domain.ReturnSnapshot;
import com.binitech.fiscal.domain.ReturnStatus;
import com.binitech.fiscal.domain.SaleSnapshot;
import com.binitech.fiscal.domain.SefazOutcome;
import com.binitech.fiscal.domain.SefazOutcome.Kind;
import com.binitech.fiscal.domain.StoredCertificate;
import com.binitech.fiscal.domain.TaxRegime;
import com.binitech.fiscal.domain.exception.BusinessException;
import com.binitech.fiscal.domain.exception.SefazCommunicationException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReturnProcessorTest {

  private static final String NFCE_KEY = "35261011222333000181650010000000421123456786";

  private MutableClock clock;
  private InMemoryDocuments documents;
  private InMemoryReturns returns;
  private FakeSefaz sefaz;
  private Issuer issuer;
  private final List<String> sequenceKeys = new ArrayList<>();
  private ReturnProcessor processor;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-11-01T15:00:00Z"));
    documents = new InMemoryDocuments();
    returns = new InMemoryReturns();
    sefaz = new FakeSefaz();
    issuer = issuer();
    IssuerRepositoryPort issuers =
        new IssuerRepositoryPort() {
          @Override
          public Issuer save(Issuer value) {
            return value;
          }

          @Override
          public Optional<Issuer> findByTenantId(String tenantId) {
            return Optional.ofNullable(issuer);
          }
        };
    int[] sequence = {0};
    processor =
        new ReturnProcessor(
            new ReturnProcessor.Stores(
                returns,
                documents,
                issuers,
                (tenant, env, model, serie) -> {
                  sequenceKeys.add(model + ":" + serie);
                  return ++sequence[0];
                }),
            sefaz,
            new PlainCipher(),
            clock,
            Duration.ofMinutes(30));
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
    issuer.setNfeSerie(3);
    issuer.setEnvironment(2);
    issuer.setCertificate(
        new StoredCertificate("pfx", "senha", "LOJA", LocalDate.of(2027, 12, 31)));
    return issuer;
  }

  private FiscalDocument original(FiscalStatus status, Instant authorizedAt) {
    FiscalDocument document = new FiscalDocument();
    document.setTenantId("t1");
    document.setSaleId("s1");
    document.setStatus(status);
    document.setChave(NFCE_KEY);
    document.setNumero(42);
    document.setProtocolo("135260000000001");
    document.setAuthorizedAt(authorizedAt);
    return documents.save(document);
  }

  private ReturnDocument waiting(String returnId, boolean fullReturn) {
    ReturnDocument document = new ReturnDocument();
    document.setTenantId("t1");
    document.setSaleId("s1");
    document.setReturnId(returnId);
    document.setStatus(ReturnStatus.WAITING_ORIGINAL);
    document.setNextAttemptAt(clock.instant());
    document.setCreatedAt(clock.instant());
    document.setSnapshot(
        new ReturnSnapshot(
            "t1",
            "s1",
            returnId,
            clock.instant(),
            fullReturn,
            "Defeito",
            null,
            null,
            List.of(
                new SaleSnapshot.Item(
                    "p1",
                    "Arroz",
                    null,
                    "10063021",
                    "5102",
                    "102",
                    "UN",
                    BigDecimal.ONE,
                    new BigDecimal("27.90"),
                    new BigDecimal("27.90")))));
    return returns.save(document);
  }

  private static SefazOutcome authorized() {
    return new SefazOutcome(
        Kind.AUTHORIZED, "100", "Autorizado o uso da NF-e", "135260000000777", null, "<nfeProc/>");
  }

  private ReturnDocument run(ReturnDocument document) {
    processor.processDue(10);
    return returns.byId.get(document.getId());
  }

  @Test
  @DisplayName("Devolução parcial gera NF-e modelo 55 na série própria referenciando a NFC-e")
  void partialReturnIssuesNfe() {
    original(FiscalStatus.AUTHORIZED, clock.instant().minusSeconds(60));
    ReturnDocument document = waiting("r1", false);
    sefaz.authorizeResults.add(authorized());

    ReturnDocument result = run(document);

    assertThat(result.getStatus()).isEqualTo(ReturnStatus.AUTHORIZED);
    assertThat(result.getReferencedChave()).isEqualTo(NFCE_KEY);
    assertThat(result.getReferencedNumero()).isEqualTo(42);
    assertThat(result.getSerie()).isEqualTo(3);
    assertThat(result.getNumero()).isEqualTo(1);
    assertThat(result.getProtocolo()).isEqualTo("135260000000777");
    assertThat(result.getAuthorizedXml()).isEqualTo("<nfeProc/>");
    assertThat(sequenceKeys).containsExactly("55:3");
    assertThat(sefaz.cancelCalls).isZero();
  }

  @Test
  @DisplayName("Devolução total dentro do prazo cancela a NFC-e em vez de emitir NF-e")
  void fullReturnInsideWindowCancels() {
    FiscalDocument nfce = original(FiscalStatus.AUTHORIZED, clock.instant().minusSeconds(600));
    ReturnDocument document = waiting("r1", true);
    sefaz.cancelResult =
        new SefazOutcome(Kind.AUTHORIZED, "135", "Evento registrado", "1352", null, "<proc/>");

    ReturnDocument result = run(document);

    assertThat(result.getStatus()).isEqualTo(ReturnStatus.NFCE_CANCELLED);
    assertThat(documents.byId.get(nfce.getId()).getStatus()).isEqualTo(FiscalStatus.CANCELLED);
    assertThat(documents.byId.get(nfce.getId()).getCancelProtocol()).isEqualTo("1352");
    assertThat(sefaz.authorizeCalls).isZero();
    assertThat(sequenceKeys).isEmpty();
  }

  @Test
  @DisplayName("Se a SEFAZ recusar o cancelamento, emite a NF-e de devolução")
  void cancelRefusedFallsBackToNfe() {
    FiscalDocument nfce = original(FiscalStatus.AUTHORIZED, clock.instant().minusSeconds(600));
    ReturnDocument document = waiting("r1", true);
    sefaz.cancelResult = SefazOutcome.of(Kind.REJECTED, "501", "Prazo de cancelamento superior");
    sefaz.authorizeResults.add(authorized());

    ReturnDocument result = run(document);

    assertThat(result.getStatus()).isEqualTo(ReturnStatus.AUTHORIZED);
    assertThat(documents.byId.get(nfce.getId()).getStatus()).isEqualTo(FiscalStatus.AUTHORIZED);
    assertThat(sefaz.cancelCalls).isEqualTo(1);
  }

  @Test
  @DisplayName("Fora do prazo, ou depois de outra devolução, não tenta cancelar a NFC-e")
  void noCancelOutsideWindowOrAfterPartial() {
    original(FiscalStatus.AUTHORIZED, clock.instant().minus(Duration.ofMinutes(31)));
    ReturnDocument late = waiting("r1", true);
    sefaz.authorizeResults.add(authorized());
    assertThat(run(late).getStatus()).isEqualTo(ReturnStatus.AUTHORIZED);

    documents.byId.clear();
    returns.byId.clear();
    original(FiscalStatus.AUTHORIZED, clock.instant());
    ReturnDocument partial = waiting("r2", false);
    partial.setStatus(ReturnStatus.AUTHORIZED);
    partial.setNextAttemptAt(null);
    returns.save(partial);
    ReturnDocument rest = waiting("r3", true);
    sefaz.authorizeResults.add(authorized());
    assertThat(run(rest).getStatus()).isEqualTo(ReturnStatus.AUTHORIZED);
    assertThat(sefaz.cancelCalls).isZero();
  }

  @Test
  @DisplayName("Espera a NFC-e da venda ser autorizada antes de emitir a devolução")
  void waitsForOriginal() {
    FiscalDocument nfce = original(FiscalStatus.CONTINGENCY, null);
    ReturnDocument document = waiting("r1", false);

    ReturnDocument result = run(document);
    assertThat(result.getStatus()).isEqualTo(ReturnStatus.WAITING_ORIGINAL);
    assertThat(result.getNextAttemptAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(2)));
    assertThat(sefaz.authorizeCalls).isZero();

    nfce.setStatus(FiscalStatus.AUTHORIZED);
    nfce.setAuthorizedAt(clock.instant());
    documents.save(nfce);
    clock.advance(Duration.ofMinutes(3));
    sefaz.authorizeResults.add(authorized());
    assertThat(run(document).getStatus()).isEqualTo(ReturnStatus.AUTHORIZED);
  }

  @Test
  @DisplayName("Sem NFC-e autorizada não há documento a emitir")
  void notRequiredWithoutAuthorizedNfce() {
    original(FiscalStatus.AWAITING_REQUEST, null);
    assertThat(run(waiting("r1", false)).getStatus()).isEqualTo(ReturnStatus.NOT_REQUIRED);

    documents.byId.clear();
    original(FiscalStatus.CANCELLED, null);
    assertThat(run(waiting("r2", false)).getStatus()).isEqualTo(ReturnStatus.NOT_REQUIRED);

    documents.byId.clear();
    ReturnDocument orphan = waiting("r3", false);
    assertThat(run(orphan).getStatus()).isEqualTo(ReturnStatus.WAITING_ORIGINAL);
    clock.advance(Duration.ofMinutes(61));
    assertThat(run(orphan).getStatus()).isEqualTo(ReturnStatus.NOT_REQUIRED);
    assertThat(sefaz.authorizeCalls).isZero();
  }

  @Test
  @DisplayName("SEFAZ fora do ar: tenta de novo e consulta a chave antes de reenviar")
  void retriesAndConsultsWhenUnavailable() {
    original(FiscalStatus.AUTHORIZED, clock.instant().minusSeconds(60));
    ReturnDocument document = waiting("r1", false);
    sefaz.authorizeErrors.add(new SefazCommunicationException("timeout", true, null));

    ReturnDocument first = run(document);
    assertThat(first.getStatus()).isEqualTo(ReturnStatus.PENDING);
    assertThat(first.isDeliveryUncertain()).isTrue();
    assertThat(first.getChave()).isNotNull();

    clock.advance(Duration.ofMinutes(1));
    sefaz.consultResults.add(authorized());
    ReturnDocument second = run(document);
    assertThat(second.getStatus()).isEqualTo(ReturnStatus.AUTHORIZED);
    assertThat(sefaz.authorizeCalls).isEqualTo(1);
    assertThat(sefaz.consultCalls).isEqualTo(1);
    assertThat(sequenceKeys).hasSize(1);
  }

  @Test
  @DisplayName("Rejeição fica registrada; o retry reaproveita o número e inutiliza se abandonado")
  void rejectionRetryAndVoid() {
    original(FiscalStatus.AUTHORIZED, clock.instant().minusSeconds(60));
    ReturnDocument document = waiting("r1", false);
    sefaz.authorizeResults.add(SefazOutcome.of(Kind.REJECTED, "225", "Falha no schema"));

    ReturnDocument rejected = run(document);
    assertThat(rejected.getStatus()).isEqualTo(ReturnStatus.REJECTED);
    assertThat(rejected.getCStat()).isEqualTo("225");
    Integer numero = rejected.getNumero();

    ReturnService service =
        new ReturnService(
            returns, null, ServicesTest.danfe(new byte[] {1}), processor, Runnable::run, clock);
    sefaz.authorizeResults.add(authorized());
    service.retry("t1", "r1");
    ReturnDocument retried = returns.byId.get(document.getId());
    assertThat(retried.getStatus()).isEqualTo(ReturnStatus.AUTHORIZED);
    assertThat(retried.getNumero()).isEqualTo(numero);
    assertThat(service.danfe("t1", "r1")).containsExactly(1);

    ReturnDocument other = waiting("r2", false);
    sefaz.authorizeResults.add(SefazOutcome.of(Kind.REJECTED, "225", "Falha no schema"));
    run(other);
    clock.advance(Duration.ofHours(25));
    sefaz.voidResult = new SefazOutcome(Kind.AUTHORIZED, "102", "Inutilizado", "1", null, null);
    assertThat(run(other).getStatus()).isEqualTo(ReturnStatus.VOIDED);
  }

  @Test
  @DisplayName("Registro é idempotente, ignora loja sem dados fiscais e valida a mensagem")
  void serviceRegistration() {
    List<Runnable> scheduled = new ArrayList<>();
    Executor executor = scheduled::add;
    Issuer[] current = {issuer};
    IssuerRepositoryPort issuers =
        new IssuerRepositoryPort() {
          @Override
          public Issuer save(Issuer value) {
            return value;
          }

          @Override
          public Optional<Issuer> findByTenantId(String tenantId) {
            return Optional.ofNullable(current[0]);
          }
        };
    ReturnService service =
        new ReturnService(
            returns, issuers, ServicesTest.danfe(new byte[0]), processor, executor, clock);
    ReturnSnapshot snapshot = waiting("tmp", false).getSnapshot();
    returns.byId.clear();
    ReturnSnapshot message =
        new ReturnSnapshot(
            "t1",
            "s1",
            "r9",
            clock.instant(),
            false,
            null,
            "529.982.247-25",
            null,
            snapshot.items());

    service.registerReturn(message);
    service.registerReturn(message);

    assertThat(returns.byId).hasSize(1);
    assertThat(scheduled).hasSize(1);
    ReturnDocument saved = returns.byId.values().iterator().next();
    assertThat(saved.getConsumerCpf()).isEqualTo("52998224725");
    assertThat(saved.getStatus()).isEqualTo(ReturnStatus.WAITING_ORIGINAL);
    assertThatThrownBy(() -> service.danfe("t1", "r9")).isInstanceOf(BusinessException.class);

    current[0] = null;
    ReturnSnapshot otherStore =
        new ReturnSnapshot(
            "t2", "s2", "r10", clock.instant(), false, null, null, null, snapshot.items());
    service.registerReturn(otherStore);
    assertThat(returns.byId).hasSize(1);

    ReturnSnapshot empty =
        new ReturnSnapshot("t1", "s1", "r11", clock.instant(), false, null, null, null, List.of());
    assertThatThrownBy(() -> service.registerReturn(empty)).isInstanceOf(BusinessException.class);
  }

  static class InMemoryReturns implements ReturnDocumentRepositoryPort {
    final Map<String, ReturnDocument> byId = new HashMap<>();

    @Override
    public ReturnDocument save(ReturnDocument document) {
      if (document.getId() == null) {
        document.setId(UUID.randomUUID().toString());
      }
      byId.put(document.getId(), document);
      return document;
    }

    @Override
    public Optional<ReturnDocument> insertIfAbsent(ReturnDocument document) {
      if (findByTenantIdAndReturnId(document.getTenantId(), document.getReturnId()).isPresent()) {
        return Optional.empty();
      }
      return Optional.of(save(document));
    }

    @Override
    public Optional<ReturnDocument> findByTenantIdAndReturnId(String tenantId, String returnId) {
      return byId.values().stream()
          .filter(d -> d.getTenantId().equals(tenantId) && d.getReturnId().equals(returnId))
          .findFirst();
    }

    @Override
    public List<ReturnDocument> findByTenantIdAndSaleId(String tenantId, String saleId) {
      return byId.values().stream()
          .filter(d -> d.getTenantId().equals(tenantId) && d.getSaleId().equals(saleId))
          .toList();
    }

    @Override
    public Optional<ReturnDocument> claimNextDue(Instant now, Duration lease) {
      Optional<ReturnDocument> next =
          byId.values().stream()
              .filter(
                  d ->
                      List.of(
                                  ReturnStatus.WAITING_ORIGINAL,
                                  ReturnStatus.PENDING,
                                  ReturnStatus.REJECTED)
                              .contains(d.getStatus())
                          && d.getNextAttemptAt() != null
                          && !d.getNextAttemptAt().isAfter(now)
                          && (d.getLockedUntil() == null || d.getLockedUntil().isBefore(now)))
              .min(Comparator.comparing(ReturnDocument::getNextAttemptAt));
      next.ifPresent(d -> d.setLockedUntil(now.plus(lease)));
      return next;
    }

    @Override
    public Optional<ReturnDocument> claim(String id, Instant now, Duration lease) {
      ReturnDocument document = byId.get(id);
      if (document == null
          || !List.of(ReturnStatus.WAITING_ORIGINAL, ReturnStatus.PENDING)
              .contains(document.getStatus())) {
        return Optional.empty();
      }
      document.setLockedUntil(now.plus(lease));
      return Optional.of(document);
    }
  }
}
