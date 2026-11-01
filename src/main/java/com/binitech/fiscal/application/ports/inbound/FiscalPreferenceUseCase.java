package com.binitech.fiscal.application.ports.inbound;

import com.binitech.fiscal.domain.FiscalPreference;
import java.util.Optional;

public interface FiscalPreferenceUseCase {
  Optional<FiscalPreference> find(String tenantId);

  FiscalPreference decide(String tenantId, boolean nfceEnabled, String userId);
}
