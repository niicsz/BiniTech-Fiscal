package com.binitech.fiscal.application.ports.outbound;

import com.binitech.fiscal.domain.ReturnDocument;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReturnDocumentRepositoryPort {

  ReturnDocument save(ReturnDocument document);

  Optional<ReturnDocument> insertIfAbsent(ReturnDocument document);

  Optional<ReturnDocument> findByTenantIdAndReturnId(String tenantId, String returnId);

  List<ReturnDocument> findByTenantIdAndSaleId(String tenantId, String saleId);

  Optional<ReturnDocument> claimNextDue(Instant now, Duration lease);

  Optional<ReturnDocument> claim(String id, Instant now, Duration lease);
}
