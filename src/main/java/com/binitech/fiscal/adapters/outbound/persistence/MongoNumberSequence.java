package com.binitech.fiscal.adapters.outbound.persistence;

import com.binitech.fiscal.application.ports.outbound.NumberSequencePort;
import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public class MongoNumberSequence implements NumberSequencePort {

  static final String COLLECTION = "fiscal_sequences";
  private static final int MAX_NUMBER = 999_999_999;

  private final MongoTemplate mongo;

  public MongoNumberSequence(MongoTemplate mongo) {
    this.mongo = mongo;
  }

  @Override
  public int next(String tenantId, int environment, String model, int serie) {
    String key = tenantId + ":" + environment + ":" + model + ":" + serie;
    Document updated =
        mongo.findAndModify(
            Query.query(Criteria.where("_id").is(key)),
            new Update().inc("last", 1),
            FindAndModifyOptions.options().upsert(true).returnNew(true),
            Document.class,
            COLLECTION);
    int number = ((Number) updated.get("last")).intValue();
    if (number > MAX_NUMBER) {
      throw new IllegalStateException("Numeração da série " + serie + " esgotada.");
    }
    return number;
  }
}
