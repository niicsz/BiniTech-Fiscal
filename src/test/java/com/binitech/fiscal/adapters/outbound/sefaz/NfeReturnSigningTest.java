package com.binitech.fiscal.adapters.outbound.sefaz;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.swconsultoria.nfe.schemas.TEnviNFe;
import br.com.swconsultoria.nfe.schemas.TProtNFe;
import br.com.swconsultoria.nfe.util.XmlNfeUtil;
import com.binitech.fiscal.application.ports.outbound.SefazPort.Credentials;
import com.binitech.fiscal.config.SchemaDirectory;
import com.binitech.fiscal.domain.Address;
import com.binitech.fiscal.domain.Issuer;
import com.binitech.fiscal.domain.PreparedNfce;
import com.binitech.fiscal.domain.ReturnDocument;
import com.binitech.fiscal.domain.ReturnSnapshot;
import com.binitech.fiscal.domain.ReturnStatus;
import com.binitech.fiscal.domain.SaleSnapshot;
import com.binitech.fiscal.domain.StoredCertificate;
import com.binitech.fiscal.domain.TaxRegime;
import com.binitech.fiscal.support.TestCertificates;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NfeReturnSigningTest {

  private static final String CNPJ = "11222333000181";
  private static final String PASSWORD = "senha-teste";
  private static final String NFCE_KEY = "35261011222333000181650010000000421123456786";
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

  static Issuer issuer() {
    Issuer issuer = new Issuer();
    issuer.setTenantId("t1");
    issuer.setCnpj(CNPJ);
    issuer.setRazaoSocial("Mercadinho Teste Ltda");
    issuer.setInscricaoEstadual("123456789110");
    issuer.setRegime(TaxRegime.SIMPLES_NACIONAL);
    issuer.setAddress(
        new Address("Rua Rio Xingu", "400", null, "Centro", "3534401", "Osasco", "SP", "06020000"));
    issuer.setSerie(1);
    issuer.setNfeSerie(2);
    issuer.setEnvironment(2);
    issuer.setDefaultNcm("21069090");
    issuer.setCertificate(
        new StoredCertificate("x", "y", "MERCADINHO TESTE LTDA", LocalDate.now().plusDays(300)));
    return issuer;
  }

  static ReturnDocument document(String cpf, String cfop) {
    ReturnSnapshot snapshot =
        new ReturnSnapshot(
            "t1",
            "sale-1",
            "ret-1",
            Instant.now(),
            false,
            "Produto com defeito",
            cpf,
            "Maria Souza",
            List.of(
                new SaleSnapshot.Item(
                    "p1",
                    "Refrigerante Guaraná 350ml",
                    "7894900011517",
                    "22021000",
                    cfop,
                    "102",
                    "UN",
                    new BigDecimal("1"),
                    new BigDecimal("5.50"),
                    new BigDecimal("5.50"))));
    ReturnDocument document = new ReturnDocument();
    document.setTenantId("t1");
    document.setSaleId("sale-1");
    document.setReturnId("ret-1");
    document.setStatus(ReturnStatus.PENDING);
    document.setSnapshot(snapshot);
    document.setConsumerCpf(cpf);
    document.setEnvironment(2);
    document.setSerie(2);
    document.setNumero(7);
    document.setCnf("87654321");
    document.setIssuedAt(Instant.now());
    document.setReferencedChave(NFCE_KEY);
    document.setReferencedNumero(42);
    return document;
  }

  @Test
  @DisplayName("NF-e de devolução com CPF: modelo 55, entrada, finalidade 4 e NFC-e referenciada")
  void signsReturnWithConsumer() {
    PreparedNfce prepared =
        adapter.prepareReturn(issuer(), credentials, document("52998224725", "5405"));

    assertThat(prepared.chave()).hasSize(44).startsWith("35").contains(CNPJ);
    assertThat(prepared.chave().substring(20, 22)).isEqualTo("55");
    assertThat(prepared.chave().substring(22, 25)).isEqualTo("002");
    assertThat(prepared.qrCode()).isNull();
    assertThat(prepared.signedXml())
        .contains("<Signature")
        .contains("<mod>55</mod>")
        .contains("<tpNF>0</tpNF>")
        .contains("<finNFe>4</finNFe>")
        .contains("<natOp>DEVOLUCAO DE VENDA</natOp>")
        .contains("<refNFe>" + NFCE_KEY + "</refNFe>")
        .contains("<CPF>52998224725</CPF>")
        .contains("<indIEDest>9</indIEDest>")
        .contains("<indFinal>1</indFinal>")
        .contains(NfceXmlBuilder.HOMOLOGATION_NAME)
        .contains("<enderDest>")
        .contains("<CFOP>1411</CFOP>")
        .contains("<CSOSN>900</CSOSN>")
        .contains("<CST>98</CST>")
        .contains("<tPag>90</tPag>")
        .contains("<vPag>0.00</vPag>")
        .contains("<vNF>5.50</vNF>")
        .contains("referente a NFC-e n 42")
        .contains("Motivo: Produto com defeito")
        .doesNotContain("<qrCode>");
  }

  @Test
  @DisplayName("Sem CPF do consumidor, a própria loja figura como destinatário")
  void signsReturnWithoutConsumer() {
    PreparedNfce prepared = adapter.prepareReturn(issuer(), credentials, document(null, null));

    assertThat(prepared.signedXml())
        .contains("<dest><CNPJ>" + CNPJ + "</CNPJ>")
        .contains("<indIEDest>1</indIEDest>")
        .contains("<IE>123456789110</IE>")
        .contains("<indFinal>0</indFinal>")
        .contains("<CFOP>1202</CFOP>");
  }

  @Test
  @DisplayName("Gera o DANFE A4 da NF-e de devolução autorizada")
  void rendersReturnDanfe() throws Exception {
    PreparedNfce prepared =
        adapter.prepareReturn(issuer(), credentials, document("52998224725", "5102"));
    TEnviNFe enviNFe = XmlNfeUtil.xmlToObject(prepared.signedXml(), TEnviNFe.class);
    TProtNFe protocol = new TProtNFe();
    protocol.setVersao("4.00");
    TProtNFe.InfProt infProt = new TProtNFe.InfProt();
    infProt.setTpAmb("2");
    infProt.setVerAplic("SP_NFE_PL_009_V4");
    infProt.setChNFe(prepared.chave());
    infProt.setDhRecbto("2026-11-01T12:00:01-03:00");
    infProt.setNProt("135260000000777");
    infProt.setCStat("100");
    infProt.setXMotivo("Autorizado o uso da NF-e");
    protocol.setInfProt(infProt);

    byte[] pdf = new JasperDanfeAdapter().renderNfe(XmlNfeUtil.criaNfeProc(enviNFe, protocol));

    java.nio.file.Files.write(java.nio.file.Path.of("target", "danfe-nfe-devolucao.pdf"), pdf);
    assertThat(pdf).hasSizeGreaterThan(1000);
    assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
  }

  @Test
  @DisplayName("Mapeia o CFOP da venda para o CFOP de devolução")
  void mapsReturnCfop() {
    assertThat(NfceXmlBuilder.returnCfop("5102")).isEqualTo("1202");
    assertThat(NfceXmlBuilder.returnCfop("5101")).isEqualTo("1201");
    assertThat(NfceXmlBuilder.returnCfop("5405")).isEqualTo("1411");
    assertThat(NfceXmlBuilder.returnCfop("5401")).isEqualTo("1410");
    assertThat(NfceXmlBuilder.returnCfop("5933")).isEqualTo("1202");
  }
}
