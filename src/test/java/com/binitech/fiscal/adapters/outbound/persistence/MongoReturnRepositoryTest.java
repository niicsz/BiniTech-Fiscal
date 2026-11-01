package com.binitech.fiscal.adapters.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.binitech.fiscal.domain.ReturnDocument;
import com.binitech.fiscal.domain.ReturnSnapshot;
import com.binitech.fiscal.domain.ReturnStatus;
import com.binitech.fiscal.domain.SaleSnapshot;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;

@DataMongoTest
class MongoReturnRepositoryTest {

  @Autowired private MongoTemplate mongo;
  private MongoReturnDocumentRepository returns;
  private MongoNumberSequence sequences;
  private final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

  @BeforeEach
  void setUp() {
    mongo.getDb().drop();
    returns = new MongoReturnDocumentRepository(mongo);
    returns.ensureIndexes();
    sequences = new MongoNumberSequence(mongo);
  }

  private ReturnDocument document(String returnId, ReturnStatus status, Instant nextAttemptAt) {
    ReturnDocument document = new ReturnDocument();
    document.setTenantId("t1");
    document.setSaleId("s1");
    document.setReturnId(returnId);
    document.setStatus(status);
    document.setNextAttemptAt(nextAttemptAt);
    document.setCreatedAt(now);
    document.setTotal(new BigDecimal("5.50"));
    document.setSnapshot(
        new ReturnSnapshot(
            "t1",
            "s1",
            returnId,
            now,
            true,
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
                    new BigDecimal("5.50"),
                    new BigDecimal("5.50")))));
    return document;
  }

  @Test
  @DisplayName("A mesma devolução não é gravada duas vezes e volta inteira do banco")
  void idempotentInsertAndRoundTrip() {
    assertThat(returns.insertIfAbsent(document("r1", ReturnStatus.WAITING_ORIGINAL, now)))
        .isPresent();
    assertThat(returns.insertIfAbsent(document("r1", ReturnStatus.WAITING_ORIGINAL, now)))
        .isEmpty();
    returns.save(document("r2", ReturnStatus.AUTHORIZED, null));

    ReturnDocument loaded = returns.findByTenantIdAndReturnId("t1", "r1").orElseThrow();
    assertThat(loaded.getSnapshot().fullReturn()).isTrue();
    assertThat(loaded.getSnapshot().items().get(0).total()).isEqualByComparingTo("5.50");
    assertThat(loaded.getTotal()).isEqualByComparingTo("5.50");
    assertThat(returns.findByTenantIdAndSaleId("t1", "s1")).hasSize(2);
    assertThat(returns.findByTenantIdAndSaleId("t2", "s1")).isEmpty();
  }

  @Test
  @DisplayName("Claim respeita status, horário e lease; gravação usa versão otimista")
  void claimAndOptimisticLock() {
    ReturnDocument due = returns.save(document("r1", ReturnStatus.PENDING, now.minusSeconds(1)));
    returns.save(document("r2", ReturnStatus.PENDING, now.plusSeconds(60)));
    returns.save(document("r3", ReturnStatus.NOT_REQUIRED, now.minusSeconds(5)));

    ReturnDocument claimed = returns.claimNextDue(now, Duration.ofSeconds(90)).orElseThrow();
    assertThat(claimed.getReturnId()).isEqualTo("r1");
    assertThat(returns.claimNextDue(now, Duration.ofSeconds(90))).isEmpty();
    assertThat(returns.claim(due.getId(), now, Duration.ofSeconds(90))).isEmpty();

    assertThatThrownBy(() -> returns.save(due))
        .isInstanceOf(OptimisticLockingFailureException.class);
    claimed.setStatus(ReturnStatus.AUTHORIZED);
    returns.save(claimed);
    assertThat(returns.findByTenantIdAndReturnId("t1", "r1").orElseThrow().getStatus())
        .isEqualTo(ReturnStatus.AUTHORIZED);
  }

  @Test
  @DisplayName("Retry limpa erro e XML anteriores no banco")
  void savingClearsNullFields() {
    ReturnDocument saved = returns.save(document("r9", ReturnStatus.REJECTED, now));
    saved.setLastError("Falha de comunicação");
    saved.setCStat("539");
    saved.setSignedXml("<NFe/>");
    returns.save(saved);

    saved.setLastError(null);
    saved.setCStat(null);
    saved.setSignedXml(null);
    saved.setStatus(ReturnStatus.PENDING);
    returns.save(saved);

    ReturnDocument loaded = returns.findByTenantIdAndReturnId("t1", "r9").orElseThrow();
    assertThat(loaded.getStatus()).isEqualTo(ReturnStatus.PENDING);
    assertThat(loaded.getLastError()).isNull();
    assertThat(loaded.getCStat()).isNull();
    assertThat(loaded.getSignedXml()).isNull();
    assertThat(loaded.getSnapshot().items()).hasSize(1);
  }

  @Test
  @DisplayName("Numeração da NF-e (55) é separada da NFC-e (65)")
  void separateSequences() {
    assertThat(sequences.next("t1", 2, 1)).isEqualTo(1);
    assertThat(sequences.next("t1", 2, 1)).isEqualTo(2);
    assertThat(sequences.next("t1", 2, "55", 1)).isEqualTo(1);
    assertThat(sequences.next("t1", 2, "55", 1)).isEqualTo(2);
    assertThat(sequences.next("t1", 2, 1)).isEqualTo(3);
  }
}
