package com.binitech.fiscal.application.ports.outbound;

import com.binitech.fiscal.domain.FiscalDocument;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface FiscalDocumentRepositoryPort {
  FiscalDocument save(FiscalDocument document);

  Optional<FiscalDocument> findByTenantIdAndSaleId(String tenantId, String saleId);

  Optional<FiscalDocument> insertIfAbsent(FiscalDocument document);

  Optional<FiscalDocument> claimNextDue(Instant now, Duration lease);

  Optional<FiscalDocument> claim(String id, Instant now, Duration lease);
}
