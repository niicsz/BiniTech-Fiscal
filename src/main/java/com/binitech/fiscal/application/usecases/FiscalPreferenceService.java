package com.binitech.fiscal.application.usecases;

import com.binitech.fiscal.application.ports.inbound.FiscalPreferenceUseCase;
import com.binitech.fiscal.application.ports.outbound.FiscalPreferenceRepositoryPort;
import com.binitech.fiscal.domain.FiscalPreference;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FiscalPreferenceService implements FiscalPreferenceUseCase {

  private static final Logger log = LoggerFactory.getLogger(FiscalPreferenceService.class);

  private final FiscalPreferenceRepositoryPort preferences;
  private final Clock clock;

  public FiscalPreferenceService(FiscalPreferenceRepositoryPort preferences, Clock clock) {
    this.preferences = preferences;
    this.clock = clock;
  }

  @Override
  public Optional<FiscalPreference> find(String tenantId) {
    return preferences.findByTenantId(tenantId);
  }

  @Override
  public FiscalPreference decide(String tenantId, boolean nfceEnabled, String userId) {
    FiscalPreference saved =
        preferences.save(new FiscalPreference(tenantId, nfceEnabled, clock.instant(), userId));
    log.info(
        "Emissão de NFC-e {} para tenantId={}", nfceEnabled ? "ativada" : "desativada", tenantId);
    return saved;
  }
}
