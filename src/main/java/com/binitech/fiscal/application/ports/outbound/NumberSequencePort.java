package com.binitech.fiscal.application.ports.outbound;

public interface NumberSequencePort {
  int next(String tenantId, int environment, String model, int serie);

  default int next(String tenantId, int environment, int serie) {
    return next(tenantId, environment, "65", serie);
  }
}
