package com.binitech.fiscal.adapters.outbound.sefaz;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.swconsultoria.nfe.schemas.TEnviNFe;
import br.com.swconsultoria.nfe.schemas.TProtNFe;
import br.com.swconsultoria.nfe.util.XmlNfeUtil;
import com.binitech.fiscal.application.ports.outbound.SefazPort.Credentials;
import com.binitech.fiscal.config.SchemaDirectory;
import com.binitech.fiscal.domain.Address;
import com.binitech.fiscal.domain.FiscalDocument;
import com.binitech.fiscal.domain.FiscalStatus;
import com.binitech.fiscal.domain.Issuer;
import com.binitech.fiscal.domain.PreparedNfce;
import com.binitech.fiscal.domain.SaleSnapshot;
import com.binitech.fiscal.domain.StoredCertificate;
import com.binitech.fiscal.domain.TaxRegime;
import com.binitech.fiscal.support.TestCertificates;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DanfeRenderingTest {

  @Test
  @DisplayName("Gera o cupom NFC-e em PDF a partir do XML autorizado")
  void rendersDanfePdf() throws Exception {
    String cnpj = "11222333000181";
    JavaNfeSefazAdapter adapter =
        new JavaNfeSefazAdapter(SchemaDirectory.extract(), 5000, new NfceXmlBuilder(null));
    Credentials credentials =
        new Credentials(TestCertificates.pfx(cnpj, "LOJA TESTE LTDA", "123", 365), "123");

    Issuer issuer = new Issuer();
    issuer.setTenantId("t1");
    issuer.setCnpj(cnpj);
    issuer.setRazaoSocial("Loja Teste Ltda");
    issuer.setInscricaoEstadual("123456789110");
    issuer.setRegime(TaxRegime.MEI);
    issuer.setAddress(
        new Address("Rua Rio Xingu", "400", null, "Centro", "3534401", "Osasco", "SP", "06020000"));
    issuer.setSerie(1);
    issuer.setEnvironment(2);
    issuer.setCertificate(new StoredCertificate("x", "y", "LOJA", LocalDate.now().plusDays(30)));

    FiscalDocument document = new FiscalDocument();
    document.setStatus(FiscalStatus.PENDING);
    document.setSerie(1);
    document.setNumero(7);
    document.setCnf("87654321");
    document.setTpEmis("1");
    document.setIssuedAt(Instant.now());
    document.setSale(
        new SaleSnapshot(
            "t1",
            "s1",
            Instant.now(),
            null,
            null,
            List.of(
                new SaleSnapshot.Item(
                    "p1",
                    "Café 500g",
                    null,
                    "09012100",
                    null,
                    null,
                    "UN",
                    BigDecimal.ONE,
                    new BigDecimal("18.90"),
                    new BigDecimal("18.90"))),
            List.of(new SaleSnapshot.Payment("DEBIT_CARD", new BigDecimal("18.90")))));

    PreparedNfce prepared = adapter.prepare(issuer, credentials, document);
    TEnviNFe enviNFe = XmlNfeUtil.xmlToObject(prepared.signedXml(), TEnviNFe.class);
    TProtNFe protocol = new TProtNFe();
    protocol.setVersao("4.00");
    TProtNFe.InfProt infProt = new TProtNFe.InfProt();
    infProt.setTpAmb("2");
    infProt.setVerAplic("SP_NFCE_PL_009_V400");
    infProt.setChNFe(prepared.chave());
    infProt.setDhRecbto("2026-10-31T12:00:01-03:00");
    infProt.setNProt("135260000000001");
    infProt.setCStat("100");
    infProt.setXMotivo("Autorizado o uso da NF-e");
    protocol.setInfProt(infProt);
    String nfeProc = XmlNfeUtil.criaNfeProc(enviNFe, protocol);

    byte[] pdf = new JasperDanfeAdapter().render(nfeProc, prepared.urlChave());

    java.nio.file.Files.write(java.nio.file.Path.of("target", "danfe-nfce-exemplo.pdf"), pdf);
    assertThat(pdf).hasSizeGreaterThan(1000);
    assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
  }
}
