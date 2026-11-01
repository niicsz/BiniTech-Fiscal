package com.binitech.fiscal.adapters.inbound.scheduling;

import com.binitech.fiscal.application.usecases.ReturnProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class ReturnRetryScheduler {

  private static final Logger log = LoggerFactory.getLogger(ReturnRetryScheduler.class);
  private static final int BATCH = 10;

  private final ReturnProcessor processor;

  public ReturnRetryScheduler(ReturnProcessor processor) {
    this.processor = processor;
  }

  @Scheduled(fixedDelayString = "${fiscal.return-retry-interval:5000}")
  public void processDue() {
    try {
      processor.processDue(BATCH);
    } catch (RuntimeException e) {
      log.error("Falha no ciclo de reprocessamento de devoluções fiscais: {}", e.getMessage(), e);
    }
  }
}
