package com.binitech.fiscal.domain;

import com.binitech.fiscal.domain.exception.TenantRequiredException;

public record SessionIdentity(String userId, String username, String role, String tenantId) {
  public SessionIdentity {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("Sessão sem usuário.");
    }
    if (tenantId == null || tenantId.isBlank()) {
      throw new TenantRequiredException();
    }
  }

  public boolean isStoreAdmin() {
    return "TENANT_ADMIN".equals(role) || "ADMIN".equals(role);
  }
}
