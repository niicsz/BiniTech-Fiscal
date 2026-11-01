package com.binitech.fiscal.adapters.inbound.web;

import com.binitech.fiscal.adapters.inbound.web.generated.api.PreferencesApi;
import com.binitech.fiscal.adapters.inbound.web.generated.model.FiscalPreferencesDTO;
import com.binitech.fiscal.adapters.inbound.web.generated.model.UpdateFiscalPreferencesDTO;
import com.binitech.fiscal.application.ports.inbound.FiscalPreferenceUseCase;
import com.binitech.fiscal.domain.SessionIdentity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PreferencesController implements PreferencesApi {

  private final FiscalPreferenceUseCase preferences;

  public PreferencesController(FiscalPreferenceUseCase preferences) {
    this.preferences = preferences;
  }

  @Override
  public ResponseEntity<FiscalPreferencesDTO> getFiscalPreferences() {
    return ResponseEntity.ok(
        WebMapper.preferences(preferences.find(CurrentSession.identity().tenantId()).orElse(null)));
  }

  @Override
  public ResponseEntity<FiscalPreferencesDTO> updateFiscalPreferences(
      UpdateFiscalPreferencesDTO updateFiscalPreferencesDTO) {
    SessionIdentity admin = CurrentSession.storeAdmin();
    return ResponseEntity.ok(
        WebMapper.preferences(
            preferences.decide(
                admin.tenantId(),
                Boolean.TRUE.equals(updateFiscalPreferencesDTO.getNfceEnabled()),
                admin.userId())));
  }
}
