package com.binitech.fiscal.adapters.outbound.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.binitech.fiscal.domain.SessionIdentity;
import com.binitech.fiscal.domain.exception.BusinessException;
import com.binitech.fiscal.domain.exception.ExternalServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpAuthenticationAdapterTest {

  private MockRestServiceServer server;
  private HttpAuthenticationAdapter adapter;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://pdv");
    server = MockRestServiceServer.bindTo(builder).build();
    adapter = new HttpAuthenticationAdapter(builder.build());
  }

  @Test
  @DisplayName("Busca a sessão no PDV e usa o papel do cadastro do PDV")
  void readsRoleFromPdv() {
    server
        .expect(requestTo("http://pdv/api/auth/me"))
        .andExpect(header("Authorization", "Bearer token-1"))
        .andRespond(
            withSuccess(
                "{\"userId\":\"u1\",\"username\":\"teste\",\"role\":\"TENANT_ADMIN\",\"tenantId\":\"t1\"}",
                MediaType.APPLICATION_JSON));

    SessionIdentity identity = adapter.session("token-1");

    assertThat(identity.role()).isEqualTo("TENANT_ADMIN");
    assertThat(identity.isStoreAdmin()).isTrue();
    assertThat(identity.tenantId()).isEqualTo("t1");
  }

  @Test
  @DisplayName("401 do PDV vira sessão inválida e 5xx vira serviço indisponível")
  void errors() {
    server
        .expect(requestTo("http://pdv/api/auth/me"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
    assertThatThrownBy(() -> adapter.session("x")).isInstanceOf(BusinessException.class);

    server.reset();
    server
        .expect(requestTo("http://pdv/api/auth/me"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    assertThatThrownBy(() -> adapter.session("x"))
        .isInstanceOf(ExternalServiceUnavailableException.class);
  }
}
