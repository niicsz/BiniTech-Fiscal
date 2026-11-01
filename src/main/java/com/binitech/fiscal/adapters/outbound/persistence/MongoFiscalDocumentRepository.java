package com.binitech.fiscal.adapters.outbound.persistence;

import com.binitech.fiscal.application.ports.outbound.FiscalDocumentRepositoryPort;
import com.binitech.fiscal.domain.FiscalDocument;
import com.binitech.fiscal.domain.FiscalStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ReplaceOptions;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public class MongoFiscalDocumentRepository implements FiscalDocumentRepositoryPort {

  static final String COLLECTION = "fiscal_documents";
  private static final List<String> WORKABLE =
      List.of(
          FiscalStatus.PENDING.name(),
          FiscalStatus.CONTINGENCY.name(),
          FiscalStatus.REJECTED.name());

  private final MongoTemplate mongo;

  public MongoFiscalDocumentRepository(MongoTemplate mongo) {
    this.mongo = mongo;
  }

  public void ensureIndexes() {
    var ops = mongo.indexOps(COLLECTION);
    ops.createIndex(
        new Index()
            .on("tenantId", Sort.Direction.ASC)
            .on("saleId", Sort.Direction.ASC)
            .unique()
            .named("uk_tenant_sale"));
    ops.createIndex(
        new Index()
            .on("status", Sort.Direction.ASC)
            .on("nextAttemptAt", Sort.Direction.ASC)
            .named("idx_status_next_attempt"));
  }

  @Override
  public FiscalDocument save(FiscalDocument document) {
    if (document.getId() == null) {
      document.setId(new ObjectId().toHexString());
      document.setVersion(0L);
      mongo.insert(toBson(document), COLLECTION);
      return document;
    }
    long current = document.getVersion() == null ? 0L : document.getVersion();
    Document bson = toBson(document);
    bson.remove("_id");
    bson.put("version", current + 1);
    var result =
        mongo.replace(
            Query.query(Criteria.where("_id").is(document.getId()).and("version").is(current)),
            bson,
            ReplaceOptions.none(),
            COLLECTION);
    if (result.getMatchedCount() == 0) {
      throw new OptimisticLockingFailureException(
          "NFC-e alterada por outro processo: " + document.getId());
    }
    document.setVersion(current + 1);
    return document;
  }

  @Override
  public Optional<FiscalDocument> findByTenantIdAndSaleId(String tenantId, String saleId) {
    return Optional.ofNullable(
        mongo.findOne(
            Query.query(Criteria.where("tenantId").is(tenantId).and("saleId").is(saleId)),
            FiscalDocument.class,
            COLLECTION));
  }

  @Override
  public Optional<FiscalDocument> insertIfAbsent(FiscalDocument document) {
    try {
      return Optional.of(save(document));
    } catch (DuplicateKeyException e) {
      document.setId(null);
      return Optional.empty();
    }
  }

  @Override
  public Optional<FiscalDocument> claimNextDue(Instant now, Duration lease) {
    Query query =
        Query.query(
                Criteria.where("status")
                    .in(WORKABLE)
                    .and("nextAttemptAt")
                    .lte(now)
                    .orOperator(
                        Criteria.where("lockedUntil").is(null),
                        Criteria.where("lockedUntil").lt(now)))
            .with(Sort.by(Sort.Direction.ASC, "nextAttemptAt"));
    return Optional.ofNullable(lock(query, now, lease));
  }

  @Override
  public Optional<FiscalDocument> claim(String id, Instant now, Duration lease) {
    Query query =
        Query.query(
            Criteria.where("_id")
                .is(id)
                .and("status")
                .in(FiscalStatus.PENDING.name(), FiscalStatus.CONTINGENCY.name())
                .orOperator(
                    Criteria.where("lockedUntil").is(null), Criteria.where("lockedUntil").lt(now)));
    return Optional.ofNullable(lock(query, now, lease));
  }

  private FiscalDocument lock(Query query, Instant now, Duration lease) {
    return mongo.findAndModify(
        query,
        new Update().set("lockedUntil", now.plus(lease)).inc("version", 1),
        FindAndModifyOptions.options().returnNew(true),
        FiscalDocument.class,
        COLLECTION);
  }

  private Document toBson(FiscalDocument document) {
    Document bson = new Document();
    mongo.getConverter().write(document, bson);
    bson.remove("_class");
    return bson;
  }
}
