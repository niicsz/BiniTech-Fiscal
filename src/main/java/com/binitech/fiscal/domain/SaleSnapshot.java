package com.binitech.fiscal.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SaleSnapshot(
    String tenantId,
    String saleId,
    Instant occurredAt,
    String consumerCpf,
    String consumerName,
    List<Item> items,
    List<Payment> payments) {

  public record Item(
      String code,
      String description,
      String barcode,
      String ncm,
      String cfop,
      String csosn,
      String unit,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal total) {}

  public record Payment(String method, BigDecimal amount) {}

  public BigDecimal itemsTotal() {
    return items.stream().map(Item::total).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
