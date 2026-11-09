package com.binitech.fiscal.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
  public static final String SALES_EXCHANGE = "binitech.sales";
  public static final String SALE_COMPLETED_KEY = "sale.completed";
  public static final String SALES_QUEUE = "fiscal.sales.completed";
  public static final String SALES_DEAD_QUEUE = "fiscal.sales.completed.dead";
  public static final String SALE_RETURNED_KEY = "sale.returned";
  public static final String RETURNS_QUEUE = "fiscal.sales.returned";
  public static final String RETURNS_DEAD_QUEUE = "fiscal.sales.returned.dead";

  @Bean
  TopicExchange salesExchange() {
    return new TopicExchange(SALES_EXCHANGE, true, false);
  }

  @Bean
  Queue fiscalSalesQueue() {
    return QueueBuilder.durable(SALES_QUEUE)
        .deadLetterExchange("")
        .deadLetterRoutingKey(SALES_DEAD_QUEUE)
        .build();
  }

  @Bean
  Queue fiscalSalesDeadQueue() {
    return QueueBuilder.durable(SALES_DEAD_QUEUE).build();
  }

  @Bean
  Binding fiscalSalesBinding(Queue fiscalSalesQueue, TopicExchange salesExchange) {
    return BindingBuilder.bind(fiscalSalesQueue).to(salesExchange).with(SALE_COMPLETED_KEY);
  }

  @Bean
  Queue fiscalReturnsQueue() {
    return QueueBuilder.durable(RETURNS_QUEUE)
        .deadLetterExchange("")
        .deadLetterRoutingKey(RETURNS_DEAD_QUEUE)
        .build();
  }

  @Bean
  Queue fiscalReturnsDeadQueue() {
    return QueueBuilder.durable(RETURNS_DEAD_QUEUE).build();
  }

  @Bean
  Binding fiscalReturnsBinding(Queue fiscalReturnsQueue, TopicExchange salesExchange) {
    return BindingBuilder.bind(fiscalReturnsQueue).to(salesExchange).with(SALE_RETURNED_KEY);
  }

  @Bean
  JacksonJsonMessageConverter fiscalMessageConverter() {
    return new JacksonJsonMessageConverter();
  }
}
