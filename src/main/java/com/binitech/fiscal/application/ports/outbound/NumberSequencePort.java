package com.binitech.fiscal.application.ports.outbound;

public interface NumberSequencePort {
  int next(String tenantId, int environment, int serie);
}
