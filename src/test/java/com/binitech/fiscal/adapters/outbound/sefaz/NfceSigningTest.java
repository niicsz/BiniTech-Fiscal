package com.binitech.fiscal.adapters.outbound.sefaz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.binitech.fiscal.application.ports.outbound.SefazPort.Credentials;
import com.binitech.fiscal.config.SchemaDirectory;
import com.binitech.fiscal.domain.Address;
import com.binitech.fiscal.domain.CertificateInfo;
import com.binitech.fiscal.domain.FiscalDocument;
import com.binitech.fiscal.domain.FiscalStatus;
import com.binitech.fiscal.domain.Issuer;
import com.binitech.fiscal.domain.PreparedNfce;
import com.binitech.fiscal.domain.SaleSnapshot;
import com.binitech.fiscal.domain.StoredCertificate;
import com.binitech.fiscal.domain.TaxRegime;
import com.binitech.fiscal.domain.exception.BusinessException;
import com.binitech.fiscal.support.TestCertificates;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NfceSigningTest {

  private static final String CNPJ = "11222333000181";
  private static final String PASSWORD = "senha-teste";
  private static JavaNfeSefazAdapter adapter;
  private static Credentials credentials;

  @BeforeAll
  static void setUp() throws Exception {
    adapter =
        new JavaNfeSefazAdapter(
            SchemaDirectory.extract(),
            5000,
            new NfceXmlBuilder(
                new NfceXmlBuilder.ResponsibleTechnician(
                    "68053458000142",
                    "Nicolas Bini",
                    "contato@binitechpdv.com.br",
                    "11958710404")));
    credentials =
        new Credentials(
            TestCertificates.pfx(CNPJ, "MERCADINHO TESTE LTDA", PASSWORD, 365), PASSWORD);
  }

  private Issuer issuer() {
    Issuer issuer = new Issuer();
    issuer.setTenantId("t1");
    issuer.setCnpj(CNPJ);
    issuer.setRazaoSocial("Mercadinho Teste Ltda");
    issuer.setNomeFantasia("Mercadinho São João");
    issuer.setInscricaoEstadual("123456789110");
    issuer.setRegime(TaxRegime.SIMPLES_NACIONAL);
    issuer.setAddress(
        new Address("Rua Rio Xingu", "400", null, "Centro", "3534401", "Osasco", "SP", "06020000"));
    issuer.setTelefone("11958710404");
    issuer.setSerie(1);
    issuer.setEnvironment(2);
    issuer.setDefaultNcm("21069090");
    issuer.setCertificate(
        new StoredCertificate("x", "y", "MERCADINHO TESTE LTDA", LocalDate.now().plusDays(300)));
    return issuer;
  }

  private FiscalDocument document(String tpEmis, String cpf, String csosn) {
    SaleSnapshot sale =
        new SaleSnapshot(
            "t1",
            "sale-1",
            Instant.now(),
            cpf,
            "João da Silva",
            List.of(
                new SaleSnapshot.Item(
                    "p1",
                    "Refrigerante Guaraná 350ml",
                    "7894900011517",
                    "22021000",
                    null,
                    csosn,
                    "UN",
                    new BigDecimal("2"),
                    new BigDecimal("5.50"),
                    new BigDecimal("11.00"),
                    null,
                    null),
                new SaleSnapshot.Item(
                    "p2",
                    "Pão francês & cia <kg>",
                    "123",
                    null,
                    null,
                    null,
                    "KG",
                    new BigDecimal("1"),
                    new BigDecimal("9.00"),
                    new BigDecimal("9.00"),
                    null,
                    null)),
            List.of(
                new SaleSnapshot.Payment("PIX", new BigDecimal("5.00")),
                new SaleSnapshot.Payment("CASH", new BigDecimal("20.00"))));
    FiscalDocument document = new FiscalDocument();
    document.setTenantId("t1");
    document.setSaleId("sale-1");
    document.setStatus(FiscalStatus.PENDING);
    document.setSale(sale);
    document.setConsumerCpf(cpf);
    document.setEnvironment(2);
    document.setSerie(1);
    document.setNumero(42);
    document.setCnf("12345678");
    document.setTpEmis(tpEmis);
    document.setIssuedAt(Instant.now());
    return document;
  }

  @Test
  @DisplayName("Desconto e acréscimo do item saem em vDesc e vOutro e entram no total da nota")
  void discountAndSurchargeEnterTheTotals() {
    FiscalDocument document = document("1", null, null);
    SaleSnapshot original = document.getSale();
    SaleSnapshot.Item first = original.items().get(0);
    SaleSnapshot.Item second = original.items().get(1);
    document.setSale(
        new SaleSnapshot(
            original.tenantId(),
            original.saleId(),
            original.occurredAt(),
            null,
            null,
            List.of(
                new SaleSnapshot.Item(
                    first.code(),
                    first.description(),
                    first.barcode(),
                    first.ncm(),
                    first.cfop(),
                    first.csosn(),
                    first.unit(),
                    first.quantity(),
                    first.unitPrice(),
                    first.total(),
                    new BigDecimal("1.50"),
                    null),
                new SaleSnapshot.Item(
                    second.code(),
                    second.description(),
                    second.barcode(),
                    second.ncm(),
                    second.cfop(),
                    second.csosn(),
                    second.unit(),
                    second.quantity(),
                    second.unitPrice(),
                    second.total(),
                    null,
                    new BigDecimal("0.90"))),
            List.of(new SaleSnapshot.Payment("CASH", new BigDecimal("20.00")))));

    PreparedNfce prepared = adapter.prepare(issuer(), credentials, document);

    assertThat(prepared.signedXml())
        .contains("<vUnTrib>5.5000</vUnTrib><vDesc>1.50</vDesc>")
        .contains("<vProd>9.00</vProd>")
        .contains("<vUnTrib>9.0000</vUnTrib><vOutro>0.90</vOutro>")
        .contains("<vProd>20.00</vProd>")
        .contains("<vDesc>1.50</vDesc>")
        .contains("<vNF>19.40</vNF>")
        .contains("<vTroco>0.60</vTroco>");
  }

  @Test
  @DisplayName("Monta, assina e valida no XSD uma NFC-e normal de homologação")
  void signsAndValidatesNormalNfce() {
    PreparedNfce prepared = adapter.prepare(issuer(), credentials, document("1", null, null));

    assertThat(prepared.chave()).hasSize(44).startsWith("35").contains(CNPJ);
    assertThat(prepared.chave().substring(20, 22)).isEqualTo("65");
    assertThat(prepared.chave().substring(34, 35)).isEqualTo("1");
    assertThat(prepared.signedXml())
        .contains("<Signature")
        .contains("<tpAmb>2</tpAmb>")
        .contains("<mod>65</mod>")
        .contains("<CRT>1</CRT>")
        .contains("<CSOSN>102</CSOSN>")
        .contains(NfceXmlBuilder.HOMOLOGATION_ITEM)
        .contains("<NCM>21069090</NCM>")
        .contains("<cEAN>SEM GTIN</cEAN>")
        .contains("<cEAN>7894900011517</cEAN>")
        .contains("<vNF>20.00</vNF>")
        .contains("<tPag>17</tPag>")
        .contains("<tpIntegra>2</tpIntegra>")
        .contains("<vTroco>5.00</vTroco>")
        .contains("<infRespTec>")
        .contains("<qrCode>")
        .doesNotContain("<dest>");
    assertThat(prepared.qrCode()).contains("?p=" + prepared.chave() + "|3|2");
    assertThat(prepared.urlChave()).isNotBlank();
  }

  @Test
  @DisplayName("NFC-e em contingência offline leva tpEmis 9, justificativa e QR Code assinado")
  void signsContingencyNfce() {
    PreparedNfce prepared =
        adapter.prepare(issuer(), credentials, document("9", "52998224725", "500"));

    assertThat(prepared.chave().substring(34, 35)).isEqualTo("9");
    assertThat(prepared.signedXml())
        .contains("<tpEmis>9</tpEmis>")
        .contains("<xJust>")
        .contains("<dhCont>")
        .contains("<CPF>52998224725</CPF>")
        .contains(NfceXmlBuilder.HOMOLOGATION_NAME)
        .contains("<CSOSN>500</CSOSN>");
    assertThat(prepared.qrCode())
        .matches(
            ".*\\?p=" + prepared.chave() + "\\|3\\|2\\|\\d{2}\\|20\\.00\\|2\\|52998224725\\|.+");
  }

  @Test
  @DisplayName("Produto sem NCM e sem NCM padrão é recusado antes de chegar na SEFAZ")
  void missingNcmIsRejected() {
    Issuer issuer = issuer();
    issuer.setDefaultNcm(null);

    assertThatThrownBy(() -> adapter.prepare(issuer, credentials, document("1", null, null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("NCM");
  }

  @Test
  @DisplayName("Lê titular, documento e validade do certificado A1")
  void inspectsCertificate() {
    CertificateInfo info = adapter.inspectCertificate(credentials.pfx(), PASSWORD);

    assertThat(info.validUntil()).isAfter(LocalDate.now().plusDays(300));
    assertThat(info.holder()).containsIgnoringCase("MERCADINHO TESTE LTDA");
    assertThat(info.document()).isEqualTo(CNPJ);
  }

  @Test
  @DisplayName("Senha errada do certificado vira mensagem amigável")
  void wrongPassword() {
    assertThatThrownBy(() -> adapter.inspectCertificate(credentials.pfx(), "errada"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("senha");
  }
}
