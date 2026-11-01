package com.binitech.fiscal.adapters.outbound.persistence;

import com.binitech.fiscal.application.ports.outbound.FiscalPreferenceRepositoryPort;
import com.binitech.fiscal.domain.FiscalPreference;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public class MongoFiscalPreferenceRepository implements FiscalPreferenceRepositoryPort {

  static final String COLLECTION = "fiscal_preferences";
  private static final String TENANT_ID = "tenantId";

  private final MongoTemplate mongo;

  public MongoFiscalPreferenceRepository(MongoTemplate mongo) {
    this.mongo = mongo;
  }

  public void ensureIndexes() {
    mongo
        .indexOps(COLLECTION)
        .createIndex(new Index().on(TENANT_ID, Sort.Direction.ASC).unique().named("uk_tenant"));
  }

  @Override
  public FiscalPreference save(FiscalPreference preference) {
    mongo.upsert(
        Query.query(Criteria.where(TENANT_ID).is(preference.tenantId())),
        new Update()
            .set("nfceEnabled", preference.nfceEnabled())
            .set("decidedAt", preference.decidedAt())
            .set("decidedBy", preference.decidedBy()),
        COLLECTION);
    return preference;
  }

  @Override
  public Optional<FiscalPreference> findByTenantId(String tenantId) {
    return Optional.ofNullable(
        mongo.findOne(
            Query.query(Criteria.where(TENANT_ID).is(tenantId)),
            FiscalPreference.class,
            COLLECTION));
  }
}
