package com.binitech.fiscal.adapters.inbound.web;

import com.binitech.fiscal.domain.SessionIdentity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

final class CurrentSession {

  private CurrentSession() {}

  static SessionIdentity identity() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof SessionIdentity id)) {
      throw new AccessDeniedException("Sessão inválida.");
    }
    return id;
  }

  static SessionIdentity storeAdmin() {
    SessionIdentity identity = identity();
    if (!identity.isStoreAdmin()) {
      throw new AccessDeniedException("Apenas o administrador da loja pode alterar dados fiscais.");
    }
    return identity;
  }
}
