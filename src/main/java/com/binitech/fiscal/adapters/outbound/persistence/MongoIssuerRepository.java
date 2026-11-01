package com.binitech.fiscal.adapters.outbound.persistence;

import com.binitech.fiscal.application.ports.outbound.IssuerRepositoryPort;
import com.binitech.fiscal.domain.Issuer;
import java.util.Optional;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public class MongoIssuerRepository implements IssuerRepositoryPort {

  static final String COLLECTION = "fiscal_issuers";

  private final MongoTemplate mongo;

  public MongoIssuerRepository(MongoTemplate mongo) {
    this.mongo = mongo;
  }

  public void ensureIndexes() {
    mongo
        .indexOps(COLLECTION)
        .createIndex(new Index().on("tenantId", Sort.Direction.ASC).unique().named("uk_tenant"));
  }

  @Override
  public Issuer save(Issuer issuer) {
    Document bson = new Document();
    mongo.getConverter().write(issuer, bson);
    bson.remove("_class");
    bson.remove("_id");
    mongo.upsert(
        Query.query(Criteria.where("tenantId").is(issuer.getTenantId())),
        Update.fromDocument(new Document("$set", bson)),
        COLLECTION);
    return issuer;
  }

  @Override
  public Optional<Issuer> findByTenantId(String tenantId) {
    return Optional.ofNullable(
        mongo.findOne(
            Query.query(Criteria.where("tenantId").is(tenantId)), Issuer.class, COLLECTION));
  }
}
