package com.binitech.fiscal.adapters.inbound.scheduling;

import com.binitech.fiscal.application.usecases.NfceProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class NfceRetryScheduler {

  private static final Logger log = LoggerFactory.getLogger(NfceRetryScheduler.class);
  private static final int BATCH = 25;

  private final NfceProcessor processor;

  public NfceRetryScheduler(NfceProcessor processor) {
    this.processor = processor;
  }

  @Scheduled(fixedDelayString = "${fiscal.retry-interval:2000}")
  public void processDue() {
    try {
      processor.processDue(BATCH);
    } catch (RuntimeException e) {
      log.error("Falha no ciclo de reprocessamento de NFC-e: {}", e.getMessage(), e);
    }
  }
}
