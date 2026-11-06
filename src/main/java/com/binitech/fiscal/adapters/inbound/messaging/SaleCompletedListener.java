package com.binitech.fiscal.adapters.inbound.messaging;

import com.binitech.fiscal.application.ports.inbound.NfceUseCase;
import com.binitech.fiscal.config.RabbitConfiguration;
import com.binitech.fiscal.domain.SaleSnapshot;
import com.binitech.fiscal.domain.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class SaleCompletedListener {

  private static final Logger log = LoggerFactory.getLogger(SaleCompletedListener.class);

  private final NfceUseCase nfce;

  public SaleCompletedListener(NfceUseCase nfce) {
    this.nfce = nfce;
  }

  @RabbitListener(queues = RabbitConfiguration.SALES_QUEUE)
  public void onSaleCompleted(SaleCompletedMessage message) {
    try {
      nfce.registerSale(message.toSnapshot());
    } catch (BusinessException e) {
      log.warn(
          "Venda descartada pelo serviço fiscal: saleId={} motivo={}",
          message.saleId(),
          e.getMessage());
      throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
    }
  }

  public record SaleCompletedMessage(
      String tenantId,
      String saleId,
      Instant occurredAt,
      String consumerCpf,
      String consumerName,
      List<Item> items,
      List<Payment> payments) {

    SaleSnapshot toSnapshot() {
      return new SaleSnapshot(
          tenantId,
          saleId,
          occurredAt,
          consumerCpf,
          consumerName,
          items == null ? List.of() : items.stream().map(Item::toSnapshot).toList(),
          payments == null
              ? List.of()
              : payments.stream()
                  .map(payment -> new SaleSnapshot.Payment(payment.method(), payment.amount()))
                  .toList());
    }
  }

  public record Item(
      String productId,
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

    SaleSnapshot.Item toSnapshot() {
      return new SaleSnapshot.Item(
          productId,
          description,
          barcode,
          ncm,
          cfop,
          csosn,
          unit,
          quantity,
          unitPrice,
          total,
          discount,
          surcharge);
    }
  }

  public record Payment(String method, BigDecimal amount) {}
}
