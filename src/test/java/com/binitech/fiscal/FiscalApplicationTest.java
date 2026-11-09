package com.binitech.fiscal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "fiscal.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "pdv.service-url=http://localhost:1",
      "spring.rabbitmq.listener.simple.auto-startup=false",
      "fiscal.retry-interval=600000",
      "management.health.rabbit.enabled=false"
    })
@AutoConfigureTestRestTemplate
class FiscalApplicationTest {

  @Autowired private TestRestTemplate rest;

  @Test
  @DisplayName("A aplicação sobe, expõe o health e protege a API fiscal")
  void startsAndProtectsApi() {
    assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(rest.getForEntity("/api/fiscal/issuer", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
