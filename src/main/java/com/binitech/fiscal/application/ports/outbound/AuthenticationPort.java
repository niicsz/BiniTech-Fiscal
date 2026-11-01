package com.binitech.fiscal.application.ports.outbound;

import com.binitech.fiscal.domain.SessionIdentity;

public interface AuthenticationPort {
  SessionIdentity session(String accessToken);
}
