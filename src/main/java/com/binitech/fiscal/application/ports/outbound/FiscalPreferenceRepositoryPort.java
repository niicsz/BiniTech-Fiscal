package com.binitech.fiscal.application.ports.outbound;

import com.binitech.fiscal.domain.FiscalPreference;
import java.util.Optional;

public interface FiscalPreferenceRepositoryPort {
  FiscalPreference save(FiscalPreference preference);

  Optional<FiscalPreference> findByTenantId(String tenantId);
}
