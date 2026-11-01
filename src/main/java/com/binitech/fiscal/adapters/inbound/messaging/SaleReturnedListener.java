package com.binitech.fiscal.adapters.inbound.messaging;

import com.binitech.fiscal.application.ports.inbound.ReturnUseCase;
import com.binitech.fiscal.config.RabbitConfiguration;
import com.binitech.fiscal.domain.ReturnSnapshot;
import com.binitech.fiscal.domain.SaleSnapshot;
import com.binitech.fiscal.domain.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class SaleReturnedListener {

  private static final Logger log = LoggerFactory.getLogger(SaleReturnedListener.class);

  private final ReturnUseCase returns;

  public SaleReturnedListener(ReturnUseCase returns) {
    this.returns = returns;
  }

  @RabbitListener(queues = RabbitConfiguration.RETURNS_QUEUE)
  public void onSaleReturned(SaleReturnedMessage message) {
    try {
      returns.registerReturn(message.toSnapshot());
    } catch (BusinessException e) {
      log.warn(
          "Devolução descartada pelo serviço fiscal: returnId={} motivo={}",
          message.returnId(),
          e.getMessage());
      throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
    }
  }

  public record SaleReturnedMessage(
      String tenantId,
      String saleId,
      String returnId,
      Instant occurredAt,
      boolean fullReturn,
      String reason,
      String consumerCpf,
      String consumerName,
      List<SaleCompletedListener.Item> items) {

    ReturnSnapshot toSnapshot() {
      return new ReturnSnapshot(
          tenantId,
          saleId,
          returnId,
          occurredAt,
          fullReturn,
          reason,
          consumerCpf,
          consumerName,
          items == null
              ? List.of()
              : items.stream()
                  .map(
                      item ->
                          new SaleSnapshot.Item(
                              item.productId(),
                              item.description(),
                              item.barcode(),
                              item.ncm(),
                              item.cfop(),
                              item.csosn(),
                              item.unit(),
                              item.quantity(),
                              item.unitPrice(),
                              item.total()))
                  .toList());
    }
  }
}
