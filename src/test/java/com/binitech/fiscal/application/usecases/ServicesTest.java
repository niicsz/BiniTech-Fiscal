package com.binitech.fiscal.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.binitech.fiscal.adapters.outbound.crypto.AesGcmSecretCipher;
import com.binitech.fiscal.application.ports.inbound.IssuerUseCase.IssuerCommand;
import com.binitech.fiscal.application.ports.outbound.DanfePort;
import com.binitech.fiscal.application.ports.outbound.FiscalPreferenceRepositoryPort;
import com.binitech.fiscal.application.ports.outbound.IssuerRepositoryPort;
import com.binitech.fiscal.application.usecases.NfceProcessorTest.FakeSefaz;
import com.binitech.fiscal.application.usecases.NfceProcessorTest.InMemoryDocuments;
import com.binitech.fiscal.application.usecases.NfceProcessorTest.MutableClock;
import com.binitech.fiscal.application.usecases.NfceProcessorTest.PlainCipher;
import com.binitech.fiscal.domain.Address;
import com.binitech.fiscal.domain.CertificateInfo;
import com.binitech.fiscal.domain.FiscalDocument;
import com.binitech.fiscal.domain.FiscalPreference;
import com.binitech.fiscal.domain.FiscalStatus;
import com.binitech.fiscal.domain.Issuer;
import com.binitech.fiscal.domain.SaleSnapshot;
import com.binitech.fiscal.domain.SefazOutcome;
import com.binitech.fiscal.domain.SefazOutcome.Kind;
import com.binitech.fiscal.domain.StoredCertificate;
import com.binitech.fiscal.domain.TaxRegime;
import com.binitech.fiscal.domain.exception.BusinessException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ServicesTest {

  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-31T15:00:00Z"));
  private final Map<String, Issuer> issuerStore = new HashMap<>();
  private final Map<String, FiscalPreference> preferenceStore = new HashMap<>();
  private final FiscalPreferenceRepositoryPort preferences =
      new FiscalPreferenceRepositoryPort() {
        @Override
        public FiscalPreference save(FiscalPreference preference) {
          preferenceStore.put(preference.tenantId(), preference);
          return preference;
        }

        @Override
        public Optional<FiscalPreference> findByTenantId(String tenantId) {
          return Optional.ofNullable(preferenceStore.get(tenantId));
        }
      };
  private final IssuerRepositoryPort issuers =
      new IssuerRepositoryPort() {
        @Override
        public Issuer save(Issuer issuer) {
          issuerStore.put(issuer.getTenantId(), issuer);
          return issuer;
        }

        @Override
        public Optional<Issuer> findByTenantId(String tenantId) {
          return Optional.ofNullable(issuerStore.get(tenantId));
        }
      };

  private static Address address() {
    return new Address(
        "Rua Rio Xingu", "400", null, "Centro", "3534401", "Osasco", "sp", "06020-000");
  }

  private static IssuerCommand command(String cnpj) {
    return new IssuerCommand(
        cnpj,
        "Loja Teste Ltda",
        null,
        "123.456.789.110",
        TaxRegime.SIMPLES_NACIONAL,
        address(),
        null,
        null,
        null,
        null,
        "21069090",
        null,
        null,
        true,
        true);
  }

  private Issuer readyIssuer(boolean autoIssue) {
    Issuer issuer = new Issuer();
    issuer.setTenantId("t1");
    issuer.setCnpj("11222333000181");
    issuer.setInscricaoEstadual("123456789110");
    issuer.setRegime(TaxRegime.SIMPLES_NACIONAL);
    issuer.setAddress(address());
    issuer.setSerie(1);
    issuer.setEnvironment(2);
    issuer.setAutoIssue(autoIssue);
    issuer.setCertificate(new StoredCertificate("pfx", "pwd", "LOJA", LocalDate.of(2027, 1, 1)));
    issuers.save(issuer);
    preferences.save(new FiscalPreference("t1", true, Instant.now(), "u1"));
    return issuer;
  }

  private static SaleSnapshot sale(String saleId, String cpf) {
    return new SaleSnapshot(
        "t1",
        saleId,
        Instant.now(),
        cpf,
        null,
        List.of(
            new SaleSnapshot.Item(
                "p1",
                "Item",
                null,
                "22021000",
                null,
                null,
                "UN",
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.TEN)),
        List.of(new SaleSnapshot.Payment("CASH", BigDecimal.TEN)));
  }

  @Nested
  @DisplayName("IssuerService")
  class Issuers {

    private final FakeSefaz sefaz =
        new FakeSefaz() {
          @Override
          public CertificateInfo inspectCertificate(byte[] pfx, String password) {
            return new CertificateInfo(
                "LOJA TESTE", new String(pfx, StandardCharsets.UTF_8), LocalDate.of(2027, 3, 1));
          }
        };
    private final IssuerService service =
        new IssuerService(issuers, sefaz, new PlainCipher(), 2, clock);

    @Test
    @DisplayName("Normaliza e salva os dados fiscais da loja")
    void savesIssuer() {
      Issuer saved = service.save("t1", command("11.222.333/0001-81"));

      assertThat(saved.getCnpj()).isEqualTo("11222333000181");
      assertThat(saved.getInscricaoEstadual()).isEqualTo("123456789110");
      assertThat(saved.getAddress().uf()).isEqualTo("SP");
      assertThat(saved.getAddress().cep()).isEqualTo("06020000");
      assertThat(saved.getEnvironment()).isEqualTo(2);
      assertThat(saved.getSerie()).isEqualTo(1);
    }

    @Test
    @DisplayName("Recusa CNPJ inválido e município de outra UF")
    void rejectsInvalidData() {
      assertThatThrownBy(() -> service.save("t1", command("11222333000180")))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("CNPJ");
      IssuerCommand wrongCity =
          new IssuerCommand(
              "11222333000181",
              "Loja",
              null,
              "1234567",
              TaxRegime.MEI,
              new Address("Rua", "1", null, "Centro", "3304557", "Rio", "SP", "06020000"),
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              false);
      assertThatThrownBy(() -> service.save("t1", wrongCity))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("IBGE");
    }

    @Test
    @DisplayName("Guarda o certificado criptografado e recusa e-CNPJ de outra empresa")
    void certificate() {
      service.save("t1", command("11222333000181"));

      Issuer withCertificate =
          service.uploadCertificate("t1", "11222333000181".getBytes(StandardCharsets.UTF_8), "s");
      assertThat(withCertificate.getCertificate().validUntil()).isEqualTo(LocalDate.of(2027, 3, 1));

      assertThatThrownBy(
              () ->
                  service.uploadCertificate(
                      "t1", "99888777000166".getBytes(StandardCharsets.UTF_8), "s"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("outro CNPJ");
    }
  }

  @Nested
  @DisplayName("NfceService")
  class Nfce {

    private InMemoryDocuments documents;
    private FakeSefaz sefaz;
    private NfceService service;
    private final List<String> processed = new ArrayList<>();

    @BeforeEach
    void setUp() {
      documents = new InMemoryDocuments();
      sefaz = new FakeSefaz();
      NfceProcessor processor =
          new NfceProcessor(
              documents, issuers, (t, e, m, s) -> 1, sefaz, new PlainCipher(), clock) {
            @Override
            public void processNow(String documentId) {
              processed.add(documentId);
            }
          };
      service =
          new NfceService(
              documents,
              issuers,
              preferences,
              sefaz,
              danfe(new byte[] {1}),
              new PlainCipher(),
              processor,
              Runnable::run,
              clock);
    }

    @Test
    @DisplayName("Loja com emissão automática já enfileira a NFC-e ao receber a venda")
    void autoIssueQueues() {
      readyIssuer(true);

      service.registerSale(sale("s1", "529.982.247-25"));
      service.registerSale(sale("s1", null));

      FiscalDocument document = documents.findByTenantIdAndSaleId("t1", "s1").orElseThrow();
      assertThat(document.getStatus()).isEqualTo(FiscalStatus.PENDING);
      assertThat(document.getConsumerCpf()).isEqualTo("52998224725");
      assertThat(processed).hasSize(1);
    }

    @Test
    @DisplayName("Sem emissão automática a venda fica aguardando o pedido manual")
    void manualIssue() {
      readyIssuer(false);
      service.registerSale(sale("s1", "123"));

      FiscalDocument waiting = documents.findByTenantIdAndSaleId("t1", "s1").orElseThrow();
      assertThat(waiting.getStatus()).isEqualTo(FiscalStatus.AWAITING_REQUEST);
      assertThat(waiting.getConsumerCpf()).isNull();

      FiscalDocument requested = service.requestIssue("t1", "s1", "52998224725");
      assertThat(requested.getStatus()).isEqualTo(FiscalStatus.PENDING);
      assertThat(requested.getConsumerCpf()).isEqualTo("52998224725");
      assertThat(processed).hasSize(1);

      service.registerSale(sale("s2", null));
      assertThatThrownBy(() -> service.requestIssue("t1", "s2", "111.111.111-11"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("CPF");
    }

    @Test
    @DisplayName("Loja com NFC-e desativada ignora a venda e recusa emissão manual")
    void disabledStoreIgnoresSales() {
      readyIssuer(true);
      preferences.save(new FiscalPreference("t1", false, Instant.now(), "u1"));

      service.registerSale(sale("s1", null));

      assertThat(documents.findByTenantIdAndSaleId("t1", "s1")).isEmpty();
      assertThat(processed).isEmpty();
      assertThatThrownBy(() -> service.requestIssue("t1", "s1", null))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("desativada");
    }

    @Test
    @DisplayName("Loja sem emitente configurado ignora a venda")
    void noIssuerIgnores() {
      service.registerSale(sale("s1", null));
      assertThat(documents.findByTenantIdAndSaleId("t1", "s1")).isEmpty();
    }

    @Test
    @DisplayName("Cancela NFC-e autorizada e exige motivo de 15 caracteres")
    void cancel() {
      readyIssuer(false);
      service.registerSale(sale("s1", null));
      FiscalDocument document = documents.findByTenantIdAndSaleId("t1", "s1").orElseThrow();
      document.setStatus(FiscalStatus.AUTHORIZED);
      document.setChave("35261011222333000181650010000000011123456789");
      document.setProtocolo("135");

      assertThatThrownBy(() -> service.cancel("t1", "s1", "curto"))
          .isInstanceOf(BusinessException.class);

      FakeSefaz cancelling =
          new FakeSefaz() {
            @Override
            public SefazOutcome cancel(
                Issuer issuer, Credentials credentials, String chave, String protocolo, String r) {
              return new SefazOutcome(
                  Kind.AUTHORIZED, "135", "Evento registrado", "1352", null, "<proc/>");
            }
          };
      NfceService cancelService =
          new NfceService(
              documents,
              issuers,
              preferences,
              cancelling,
              danfe(new byte[0]),
              new PlainCipher(),
              null,
              Runnable::run,
              clock);

      FiscalDocument cancelled =
          cancelService.cancel("t1", "s1", "Cliente desistiu da compra no caixa");
      assertThat(cancelled.getStatus()).isEqualTo(FiscalStatus.CANCELLED);
      assertThat(cancelled.getCancelProtocol()).isEqualTo("1352");
    }

    @Test
    @DisplayName("DANFE só sai para NFC-e autorizada ou em contingência")
    void danfeRequiresIssuedDocument() {
      readyIssuer(false);
      service.registerSale(sale("s1", null));

      assertThatThrownBy(() -> service.danfe("t1", "s1")).isInstanceOf(BusinessException.class);

      FiscalDocument document = documents.findByTenantIdAndSaleId("t1", "s1").orElseThrow();
      document.setStatus(FiscalStatus.CONTINGENCY);
      document.setSignedXml("<nfe/>");
      assertThat(service.danfe("t1", "s1")).containsExactly(1);
    }
  }

  @Test
  @DisplayName("AES-GCM cifra e decifra, e o mesmo texto gera cifras diferentes")
  void cipher() {
    AesGcmSecretCipher cipher =
        new AesGcmSecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
    String first = cipher.encrypt("segredo".getBytes(StandardCharsets.UTF_8));
    String second = cipher.encrypt("segredo".getBytes(StandardCharsets.UTF_8));

    assertThat(first).isNotEqualTo(second);
    assertThat(new String(cipher.decrypt(first), StandardCharsets.UTF_8)).isEqualTo("segredo");
    assertThatThrownBy(() -> new AesGcmSecretCipher("curta"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  static DanfePort danfe(byte[] pdf) {
    return new DanfePort() {
      @Override
      public byte[] render(String xml, String urlConsulta) {
        return pdf;
      }

      @Override
      public byte[] renderNfe(String xml) {
        return pdf;
      }
    };
  }
}
