package com.binitech.fiscal.adapters.outbound.persistence;

import com.binitech.fiscal.application.ports.outbound.ReturnDocumentRepositoryPort;
import com.binitech.fiscal.domain.ReturnDocument;
import com.binitech.fiscal.domain.ReturnStatus;
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

public class MongoReturnDocumentRepository implements ReturnDocumentRepositoryPort {

  static final String COLLECTION = "fiscal_returns";
  private static final String TENANT_ID = "tenantId";
  private static final String STATUS = "status";
  private static final String LOCKED_UNTIL = "lockedUntil";
  private static final String NEXT_ATTEMPT_AT = "nextAttemptAt";
  private static final String VERSION = "version";
  private static final List<String> WORKABLE =
      List.of(
          ReturnStatus.WAITING_ORIGINAL.name(),
          ReturnStatus.PENDING.name(),
          ReturnStatus.REJECTED.name());

  private final MongoTemplate mongo;

  public MongoReturnDocumentRepository(MongoTemplate mongo) {
    this.mongo = mongo;
  }

  public void ensureIndexes() {
    var ops = mongo.indexOps(COLLECTION);
    ops.createIndex(
        new Index()
            .on(TENANT_ID, Sort.Direction.ASC)
            .on("returnId", Sort.Direction.ASC)
            .unique()
            .named("uk_tenant_return"));
    ops.createIndex(
        new Index()
            .on(TENANT_ID, Sort.Direction.ASC)
            .on("saleId", Sort.Direction.ASC)
            .named("idx_tenant_sale"));
    ops.createIndex(
        new Index()
            .on(STATUS, Sort.Direction.ASC)
            .on(NEXT_ATTEMPT_AT, Sort.Direction.ASC)
            .named("idx_status_next_attempt"));
  }

  @Override
  public ReturnDocument save(ReturnDocument document) {
    if (document.getId() == null) {
      document.setId(new ObjectId().toHexString());
      document.setVersion(0L);
      mongo.insert(toBson(document), COLLECTION);
      return document;
    }
    long current = document.getVersion() == null ? 0L : document.getVersion();
    Document bson = toBson(document);
    bson.remove("_id");
    bson.put(VERSION, current + 1);
    var result =
        mongo.replace(
            Query.query(Criteria.where("_id").is(document.getId()).and(VERSION).is(current)),
            bson,
            ReplaceOptions.none(),
            COLLECTION);
    if (result.getMatchedCount() == 0) {
      throw new OptimisticLockingFailureException(
          "Devolução fiscal alterada por outro processo: " + document.getId());
    }
    document.setVersion(current + 1);
    return document;
  }

  @Override
  public Optional<ReturnDocument> insertIfAbsent(ReturnDocument document) {
    try {
      return Optional.of(save(document));
    } catch (DuplicateKeyException e) {
      document.setId(null);
      return Optional.empty();
    }
  }

  @Override
  public Optional<ReturnDocument> findByTenantIdAndReturnId(String tenantId, String returnId) {
    return Optional.ofNullable(
        mongo.findOne(
            Query.query(Criteria.where(TENANT_ID).is(tenantId).and("returnId").is(returnId)),
            ReturnDocument.class,
            COLLECTION));
  }

  @Override
  public List<ReturnDocument> findByTenantIdAndSaleId(String tenantId, String saleId) {
    return mongo.find(
        Query.query(Criteria.where(TENANT_ID).is(tenantId).and("saleId").is(saleId))
            .with(Sort.by(Sort.Direction.ASC, "createdAt")),
        ReturnDocument.class,
        COLLECTION);
  }

  @Override
  public Optional<ReturnDocument> claimNextDue(Instant now, Duration lease) {
    Query query =
        Query.query(
                Criteria.where(STATUS)
                    .in(WORKABLE)
                    .and(NEXT_ATTEMPT_AT)
                    .lte(now)
                    .orOperator(
                        Criteria.where(LOCKED_UNTIL).is(null),
                        Criteria.where(LOCKED_UNTIL).lt(now)))
            .with(Sort.by(Sort.Direction.ASC, NEXT_ATTEMPT_AT));
    return Optional.ofNullable(lock(query, now, lease));
  }

  @Override
  public Optional<ReturnDocument> claim(String id, Instant now, Duration lease) {
    Query query =
        Query.query(
            Criteria.where("_id")
                .is(id)
                .and(STATUS)
                .in(ReturnStatus.WAITING_ORIGINAL.name(), ReturnStatus.PENDING.name())
                .orOperator(
                    Criteria.where(LOCKED_UNTIL).is(null), Criteria.where(LOCKED_UNTIL).lt(now)));
    return Optional.ofNullable(lock(query, now, lease));
  }

  private ReturnDocument lock(Query query, Instant now, Duration lease) {
    return mongo.findAndModify(
        query,
        new Update().set(LOCKED_UNTIL, now.plus(lease)).inc(VERSION, 1),
        FindAndModifyOptions.options().returnNew(true),
        ReturnDocument.class,
        COLLECTION);
  }

  private Document toBson(ReturnDocument document) {
    Document bson = new Document();
    mongo.getConverter().write(document, bson);
    bson.remove("_class");
    return bson;
  }
}
