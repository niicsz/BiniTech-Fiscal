package com.binitech.fiscal.application.ports.outbound;

import com.binitech.fiscal.domain.Issuer;
import java.util.Optional;

public interface IssuerRepositoryPort {
  Issuer save(Issuer issuer);

  Optional<Issuer> findByTenantId(String tenantId);
}
