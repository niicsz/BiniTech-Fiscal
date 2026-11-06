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
      BigDecimal total,
      BigDecimal discount,
      BigDecimal surcharge) {

    public BigDecimal discountOrZero() {
      return discount == null ? BigDecimal.ZERO : discount;
    }

    public BigDecimal surchargeOrZero() {
      return surcharge == null ? BigDecimal.ZERO : surcharge;
    }

    public BigDecimal net() {
      return total.subtract(discountOrZero()).add(surchargeOrZero());
    }

    public boolean hasValidAmounts() {
      return quantity != null
          && quantity.signum() > 0
          && total != null
          && total.signum() > 0
          && discountOrZero().signum() >= 0
          && surchargeOrZero().signum() >= 0
          && discountOrZero().compareTo(total) <= 0;
    }
  }

  public record Payment(String method, BigDecimal amount) {}

  public BigDecimal itemsTotal() {
    return items.stream().map(Item::net).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
