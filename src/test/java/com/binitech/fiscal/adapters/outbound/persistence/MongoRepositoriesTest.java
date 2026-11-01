package com.binitech.fiscal.adapters.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.binitech.fiscal.domain.Address;
import com.binitech.fiscal.domain.FiscalDocument;
import com.binitech.fiscal.domain.FiscalStatus;
import com.binitech.fiscal.domain.Issuer;
import com.binitech.fiscal.domain.SaleSnapshot;
import com.binitech.fiscal.domain.StoredCertificate;
import com.binitech.fiscal.domain.TaxRegime;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;

@DataMongoTest
class MongoRepositoriesTest {

  @Autowired private MongoTemplate mongo;
  private MongoFiscalDocumentRepository documents;
  private MongoIssuerRepository issuers;
  private MongoNumberSequence sequences;
  private final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

  @BeforeEach
  void setUp() {
    mongo.getDb().drop();
    documents = new MongoFiscalDocumentRepository(mongo);
    documents.ensureIndexes();
    issuers = new MongoIssuerRepository(mongo);
    issuers.ensureIndexes();
    sequences = new MongoNumberSequence(mongo);
  }

  private FiscalDocument document(String saleId, FiscalStatus status, Instant nextAttemptAt) {
    FiscalDocument document = new FiscalDocument();
    document.setTenantId("t1");
    document.setSaleId(saleId);
    document.setStatus(status);
    document.setNextAttemptAt(nextAttemptAt);
    document.setSale(
        new SaleSnapshot(
            "t1",
            saleId,
            now,
            "52998224725",
            "João",
            List.of(
                new SaleSnapshot.Item(
                    "p1",
                    "Refrigerante",
                    "7894900011517",
                    "22021000",
                    "5102",
                    "102",
                    "UN",
                    new BigDecimal("2"),
                    new BigDecimal("5.50"),
                    new BigDecimal("11.00"))),
            List.of(new SaleSnapshot.Payment("PIX", new BigDecimal("11.00")))));
    document.setTotal(new BigDecimal("11.00"));
    return document;
  }

  @Test
  @DisplayName("Grava e lê a NFC-e com a venda, valores decimais e status")
  void roundTrip() {
    FiscalDocument saved = documents.save(document("s1", FiscalStatus.PENDING, now));

    FiscalDocument loaded = documents.findByTenantIdAndSaleId("t1", "s1").orElseThrow();

    assertThat(loaded.getId()).isEqualTo(saved.getId());
    assertThat(loaded.getStatus()).isEqualTo(FiscalStatus.PENDING);
    assertThat(loaded.getSale().items().get(0).total()).isEqualByComparingTo("11.00");
    assertThat(loaded.getSale().payments().get(0).method()).isEqualTo("PIX");
    assertThat(loaded.getSale().occurredAt()).isEqualTo(now);
    assertThat(loaded.getTotal()).isEqualByComparingTo("11.00");
  }

  @Test
  @DisplayName("A mesma venda nunca gera dois documentos fiscais")
  void insertIfAbsentIsIdempotent() {
    assertThat(documents.insertIfAbsent(document("s1", FiscalStatus.PENDING, now))).isPresent();
    assertThat(documents.insertIfAbsent(document("s1", FiscalStatus.PENDING, now))).isEmpty();
    assertThat(mongo.getCollection(MongoFiscalDocumentRepository.COLLECTION).countDocuments())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("Gravação com versão desatualizada é recusada")
  void optimisticLocking() {
    FiscalDocument saved = documents.save(document("s1", FiscalStatus.PENDING, now));
    FiscalDocument stale = documents.findByTenantIdAndSaleId("t1", "s1").orElseThrow();
    saved.setLastError("primeira");
    documents.save(saved);

    stale.setLastError("segunda");
    assertThatThrownBy(() -> documents.save(stale))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }

  @Test
  @DisplayName("Só pega documentos vencidos, livres e em status que precisam de trabalho")
  void claimsOnlyDueWork() {
    documents.save(document("future", FiscalStatus.PENDING, now.plusSeconds(60)));
    documents.save(document("done", FiscalStatus.AUTHORIZED, now.minusSeconds(60)));
    documents.save(document("due", FiscalStatus.PENDING, now.minusSeconds(5)));

    Optional<FiscalDocument> claimed = documents.claimNextDue(now, Duration.ofSeconds(90));

    assertThat(claimed).map(FiscalDocument::getSaleId).contains("due");
    assertThat(claimed.get().getLockedUntil()).isAfter(now);
    assertThat(documents.claimNextDue(now, Duration.ofSeconds(90))).isEmpty();
    assertThat(documents.claimNextDue(now.plusSeconds(120), Duration.ofSeconds(90)))
        .map(FiscalDocument::getSaleId)
        .isPresent();
  }

  @Test
  @DisplayName("Dois workers ao mesmo tempo nunca pegam o mesmo documento")
  void concurrentClaimsAreExclusive() throws Exception {
    for (int i = 0; i < 20; i++) {
      documents.save(document("s" + i, FiscalStatus.PENDING, now.minusSeconds(1)));
    }
    ConcurrentHashMap<String, Integer> claims = new ConcurrentHashMap<>();
    ExecutorService pool = Executors.newFixedThreadPool(8);
    for (int worker = 0; worker < 8; worker++) {
      pool.submit(
          () -> {
            Optional<FiscalDocument> claimed;
            while ((claimed = documents.claimNextDue(now, Duration.ofMinutes(5))).isPresent()) {
              claims.merge(claimed.get().getSaleId(), 1, Integer::sum);
            }
          });
    }
    pool.shutdown();
    assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

    assertThat(claims).hasSize(20);
    assertThat(claims.values()).allMatch(count -> count == 1);
  }

  @Test
  @DisplayName("Numeração é sequencial por loja, ambiente e série, sem repetir sob concorrência")
  void numberSequence() throws Exception {
    assertThat(sequences.next("t1", 2, 1)).isEqualTo(1);
    assertThat(sequences.next("t1", 2, 1)).isEqualTo(2);
    assertThat(sequences.next("t1", 1, 1)).isEqualTo(1);
    assertThat(sequences.next("t2", 2, 1)).isEqualTo(1);

    ConcurrentHashMap<Integer, Boolean> numbers = new ConcurrentHashMap<>();
    ExecutorService pool = Executors.newFixedThreadPool(8);
    for (int i = 0; i < 100; i++) {
      pool.submit(() -> numbers.put(sequences.next("t3", 2, 1), true));
    }
    pool.shutdown();
    assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    assertThat(numbers.keySet()).hasSize(100).contains(1, 100);
  }

  @Test
  @DisplayName("Emitente é gravado por loja e lido com endereço, regime e certificado")
  void issuerRoundTrip() {
    Issuer issuer = new Issuer();
    issuer.setTenantId("t1");
    issuer.setCnpj("11222333000181");
    issuer.setRegime(TaxRegime.MEI);
    issuer.setAddress(
        new Address("Rua", "1", null, "Centro", "3534401", "Osasco", "SP", "06020000"));
    issuer.setCertificate(new StoredCertificate("enc", "pwd", "LOJA", LocalDate.of(2027, 5, 1)));
    issuer.setSerie(1);
    issuers.save(issuer);
    issuer.setSerie(2);
    issuers.save(issuer);

    Issuer loaded = issuers.findByTenantId("t1").orElseThrow();
    assertThat(loaded.getRegime()).isEqualTo(TaxRegime.MEI);
    assertThat(loaded.getAddress().codigoMunicipio()).isEqualTo("3534401");
    assertThat(loaded.getCertificate().validUntil()).isEqualTo(LocalDate.of(2027, 5, 1));
    assertThat(loaded.getSerie()).isEqualTo(2);
    assertThat(mongo.getCollection(MongoIssuerRepository.COLLECTION).countDocuments()).isEqualTo(1);
  }

  @Test
  @DisplayName("Preferência de NFC-e é uma por loja e pode ser trocada")
  void preferenceRoundTrip() {
    MongoFiscalPreferenceRepository preferences = new MongoFiscalPreferenceRepository(mongo);
    preferences.ensureIndexes();
    assertThat(preferences.findByTenantId("t1")).isEmpty();

    preferences.save(new com.binitech.fiscal.domain.FiscalPreference("t1", true, now, "u1"));
    preferences.save(new com.binitech.fiscal.domain.FiscalPreference("t1", false, now, "u2"));

    var loaded = preferences.findByTenantId("t1").orElseThrow();
    assertThat(loaded.nfceEnabled()).isFalse();
    assertThat(loaded.decidedBy()).isEqualTo("u2");
    assertThat(mongo.getCollection(MongoFiscalPreferenceRepository.COLLECTION).countDocuments())
        .isEqualTo(1);
  }
}
