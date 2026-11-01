package com.binitech.fiscal.adapters.inbound.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.binitech.fiscal.application.ports.inbound.FiscalPreferenceUseCase;
import com.binitech.fiscal.application.ports.inbound.IssuerUseCase;
import com.binitech.fiscal.application.ports.inbound.IssuerUseCase.IssuerCommand;
import com.binitech.fiscal.application.ports.inbound.NfceUseCase;
import com.binitech.fiscal.application.ports.outbound.AuthenticationPort;
import com.binitech.fiscal.domain.Address;
import com.binitech.fiscal.domain.FiscalDocument;
import com.binitech.fiscal.domain.FiscalPreference;
import com.binitech.fiscal.domain.FiscalStatus;
import com.binitech.fiscal.domain.Issuer;
import com.binitech.fiscal.domain.SessionIdentity;
import com.binitech.fiscal.domain.StoredCertificate;
import com.binitech.fiscal.domain.TaxRegime;
import com.binitech.fiscal.domain.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "fiscal.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "auth.service-url=http://localhost:1",
      "spring.rabbitmq.listener.simple.auto-startup=false",
      "fiscal.retry-interval=600000",
      "management.health.rabbit.enabled=false"
    })
@AutoConfigureMockMvc
class FiscalApiContractTest {

  private static final String ADMIN = "Bearer admin";
  private static final String OPERATOR = "Bearer operator";

  @Autowired private MockMvc mvc;
  @MockitoBean private AuthenticationPort authentication;
  @MockitoBean private IssuerUseCase issuers;
  @MockitoBean private NfceUseCase nfce;
  @MockitoBean private FiscalPreferenceUseCase preferences;

  @BeforeEach
  void sessions() {
    when(authentication.session("admin"))
        .thenReturn(new SessionIdentity("u1", "loja", "TENANT_ADMIN", "t1"));
    when(authentication.session("operator"))
        .thenReturn(new SessionIdentity("u2", "caixa", "OPERATOR", "t1"));
  }

  private Issuer issuer() {
    Issuer issuer = new Issuer();
    issuer.setTenantId("t1");
    issuer.setCnpj("11222333000181");
    issuer.setRazaoSocial("Loja Teste Ltda");
    issuer.setInscricaoEstadual("123456789110");
    issuer.setRegime(TaxRegime.MEI);
    issuer.setAddress(
        new Address("Rua", "1", null, "Centro", "3534401", "Osasco", "SP", "06020000"));
    issuer.setSerie(1);
    issuer.setEnvironment(2);
    issuer.setAutoIssue(true);
    issuer.setCertificate(new StoredCertificate("x", "y", "LOJA", LocalDate.of(2099, 1, 1)));
    return issuer;
  }

  private FiscalDocument authorized() {
    FiscalDocument document = new FiscalDocument();
    document.setSaleId("s1");
    document.setStatus(FiscalStatus.AUTHORIZED);
    document.setEnvironment(2);
    document.setNumero(42);
    document.setChave("35261011222333000181650010000000421123456789");
    document.setCStat("100");
    document.setXMotivo("Autorizado o uso da NF-e");
    document.setTotal(new BigDecimal("10.00"));
    document.setTpEmis("1");
    document.setAuthorizedAt(Instant.parse("2026-10-31T15:00:00Z"));
    return document;
  }

  @Test
  @DisplayName("Sem sessão a API fiscal responde 401")
  void requiresSession() throws Exception {
    mvc.perform(get("/api/fiscal/issuer")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET issuer devolve 204 sem cadastro e o contrato IssuerDTO com cadastro")
  void getIssuer() throws Exception {
    when(issuers.find("t1")).thenReturn(Optional.empty());
    mvc.perform(get("/api/fiscal/issuer").header("Authorization", OPERATOR))
        .andExpect(status().isNoContent());

    when(issuers.find("t1")).thenReturn(Optional.of(issuer()));
    mvc.perform(get("/api/fiscal/issuer").header("Authorization", OPERATOR))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.regime").value("MEI"))
        .andExpect(jsonPath("$.address.codigoMunicipio").value("3534401"))
        .andExpect(jsonPath("$.certificateValidUntil").value("2099-01-01"))
        .andExpect(jsonPath("$.readyToIssue").value(true))
        .andExpect(jsonPath("$.environment").value(2));
  }

  @Test
  @DisplayName("Só o administrador da loja salva dados fiscais, e o corpo vira o comando")
  void saveIssuer() throws Exception {
    String body =
        """
        {"cnpj":"11222333000181","razaoSocial":"Loja","inscricaoEstadual":"1234567",
         "regime":"SIMPLES_NACIONAL","autoIssue":true,
         "address":{"logradouro":"Rua","bairro":"Centro","codigoMunicipio":"3534401",
                    "cidade":"Osasco","uf":"SP","cep":"06020000"}}
        """;
    mvc.perform(
            put("/api/fiscal/issuer")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    verify(issuers, never()).save(any(), any());

    when(issuers.save(eq("t1"), any())).thenReturn(issuer());
    mvc.perform(
            put("/api/fiscal/issuer")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
    ArgumentCaptor<IssuerCommand> command = ArgumentCaptor.forClass(IssuerCommand.class);
    verify(issuers).save(eq("t1"), command.capture());
    org.assertj.core.api.Assertions.assertThat(command.getValue().regime())
        .isEqualTo(TaxRegime.SIMPLES_NACIONAL);
    org.assertj.core.api.Assertions.assertThat(command.getValue().autoIssue()).isTrue();
    org.assertj.core.api.Assertions.assertThat(command.getValue().contingencyEnabled()).isFalse();
    org.assertj.core.api.Assertions.assertThat(command.getValue().address().cidade())
        .isEqualTo("Osasco");
  }

  @Test
  @DisplayName("Upload do certificado chega ao caso de uso com bytes e senha")
  void uploadCertificate() throws Exception {
    when(issuers.uploadCertificate(eq("t1"), any(), eq("senha"))).thenReturn(issuer());

    mvc.perform(
            multipart("/api/fiscal/issuer/certificate")
                .file(
                    new MockMultipartFile(
                        "file", "a1.pfx", "application/x-pkcs12", new byte[] {1, 2}))
                .param("password", "senha")
                .header("Authorization", ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.certificateHolder").value("LOJA"));
  }

  @Test
  @DisplayName("NFC-e segue o contrato NfceDTO, inclusive cStat e xMotivo")
  void nfceContract() throws Exception {
    when(nfce.find("t1", "s1")).thenReturn(Optional.of(authorized()));

    mvc.perform(get("/api/fiscal/nfce/s1").header("Authorization", OPERATOR))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("AUTHORIZED"))
        .andExpect(jsonPath("$.numero").value(42))
        .andExpect(jsonPath("$.cStat").value("100"))
        .andExpect(jsonPath("$.xMotivo").value("Autorizado o uso da NF-e"))
        .andExpect(jsonPath("$.contingency").value(false))
        .andExpect(jsonPath("$.total").value(10.00));

    when(nfce.find("t1", "s2")).thenReturn(Optional.empty());
    mvc.perform(get("/api/fiscal/nfce/s2").header("Authorization", OPERATOR))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("Emitir aceita CPF opcional e corpo vazio")
  void issue() throws Exception {
    when(nfce.requestIssue(eq("t1"), eq("s1"), any())).thenReturn(authorized());

    mvc.perform(
            post("/api/fiscal/nfce/s1/issue")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/fiscal/nfce/s1/issue")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"consumerCpf\":\"52998224725\"}"))
        .andExpect(status().isOk());
    verify(nfce).requestIssue("t1", "s1", "52998224725");
  }

  @Test
  @DisplayName("Cancelamento valida o motivo pelo contrato e exige administrador")
  void cancel() throws Exception {
    mvc.perform(
            post("/api/fiscal/nfce/s1/cancel")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"curto\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Campo inválido: reason"));

    mvc.perform(
            post("/api/fiscal/nfce/s1/cancel")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Cliente desistiu da compra no caixa\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Corpo sem Content-Type responde 415, não 500")
  void unsupportedMediaType() throws Exception {
    mvc.perform(post("/api/fiscal/nfce/s1/issue").header("Authorization", OPERATOR).content("x"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.message").value("Envie o corpo como application/json."));
  }

  @Test
  @DisplayName("Erro de negócio vira 422 com ErrorDTO")
  void businessError() throws Exception {
    when(nfce.requestIssue(eq("t1"), eq("s1"), any()))
        .thenThrow(new BusinessException("CPF do consumidor inválido."));

    mvc.perform(
            post("/api/fiscal/nfce/s1/issue")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value("CPF do consumidor inválido."));
  }

  @Test
  @DisplayName("DANFE sai como PDF")
  void danfe() throws Exception {
    when(nfce.danfe("t1", "s1")).thenReturn("%PDF-1.4".getBytes());

    mvc.perform(get("/api/fiscal/nfce/s1/danfe").header("Authorization", OPERATOR))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(header().string("Content-Disposition", "inline; filename=\"nfce-s1.pdf\""))
        .andExpect(content().bytes("%PDF-1.4".getBytes()));
  }

  @Test
  @DisplayName("Preferência: sem resposta vem decided=false e só o administrador decide")
  void preferences() throws Exception {
    when(preferences.find("t1")).thenReturn(Optional.empty());
    mvc.perform(get("/api/fiscal/preferences").header("Authorization", OPERATOR))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decided").value(false))
        .andExpect(jsonPath("$.nfceEnabled").value(false));

    mvc.perform(
            put("/api/fiscal/preferences")
                .header("Authorization", OPERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nfceEnabled\":true}"))
        .andExpect(status().isForbidden());

    when(preferences.decide("t1", true, "u1"))
        .thenReturn(new FiscalPreference("t1", true, Instant.parse("2026-11-01T12:00:00Z"), "u1"));
    mvc.perform(
            put("/api/fiscal/preferences")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nfceEnabled\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decided").value(true))
        .andExpect(jsonPath("$.nfceEnabled").value(true));
  }
}
