package com.binitech.fiscal.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReturnSnapshot(
    String tenantId,
    String saleId,
    String returnId,
    Instant occurredAt,
    boolean fullReturn,
    String reason,
    String consumerCpf,
    String consumerName,
    List<SaleSnapshot.Item> items) {

  public BigDecimal itemsTotal() {
    return items.stream().map(SaleSnapshot.Item::net).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
