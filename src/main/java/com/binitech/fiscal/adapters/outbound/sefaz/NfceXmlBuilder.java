package com.binitech.fiscal.adapters.outbound.sefaz;

import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.DocumentoEnum;
import br.com.swconsultoria.nfe.schemas.TEnderEmi;
import br.com.swconsultoria.nfe.schemas.TEndereco;
import br.com.swconsultoria.nfe.schemas.TEnviNFe;
import br.com.swconsultoria.nfe.schemas.TInfRespTec;
import br.com.swconsultoria.nfe.schemas.TNFe;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Dest;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Det;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Det.Imposto;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Det.Imposto.COFINS;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Det.Imposto.ICMS;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Det.Imposto.PIS;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Det.Prod;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Emit;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Ide;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Ide.NFref;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.InfAdic;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Pag;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Total;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Total.ICMSTot;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Transp;
import br.com.swconsultoria.nfe.schemas.TUf;
import br.com.swconsultoria.nfe.schemas.TUfEmi;
import br.com.swconsultoria.nfe.util.ChaveUtil;
import br.com.swconsultoria.nfe.util.ConstantesUtil;
import br.com.swconsultoria.nfe.util.XmlNfeUtil;
import com.binitech.fiscal.domain.Address;
import com.binitech.fiscal.domain.FiscalDocument;
import com.binitech.fiscal.domain.Issuer;
import com.binitech.fiscal.domain.ReturnDocument;
import com.binitech.fiscal.domain.ReturnSnapshot;
import com.binitech.fiscal.domain.SaleSnapshot;
import com.binitech.fiscal.domain.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

public class NfceXmlBuilder {

  static final String HOMOLOGATION_NAME =
      "NF-E EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL";
  static final String HOMOLOGATION_ITEM =
      "NOTA FISCAL EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL";
  static final String CONTINGENCY_JUSTIFICATION =
      "SEFAZ indisponivel no momento da venda - emissao em contingencia offline";
  private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
  private static final Set<String> CSOSN_SN102 = Set.of("102", "103", "300", "400");
  private static final Set<String> CARD_PAYMENTS = Set.of("03", "04", "17");
  private static final String PIS_COFINS_CST = "49";
  private static final String PIS_COFINS_ENTRY_CST = "98";
  private static final String RETURN_CSOSN = "900";
  private static final String RETURN_NAT_OP = "DEVOLUCAO DE VENDA";
  private static final String DEFAULT_RETURN_CFOP = "1202";
  private static final Map<String, String> RETURN_CFOP =
      Map.of(
          "5101", "1201",
          "5102", "1202",
          "5103", "1203",
          "5104", "1204",
          "5401", "1410",
          "5402", "1410",
          "5403", "1411",
          "5405", "1411");
  private static final String ZERO = "0.00";
  private static final String ZERO_RATE = "0.0000";

  private final ResponsibleTechnician technician;

  public NfceXmlBuilder(ResponsibleTechnician technician) {
    this.technician = technician;
  }

  public record ResponsibleTechnician(String cnpj, String contact, String email, String phone) {
    boolean isConfigured() {
      return cnpj != null && !cnpj.isBlank() && contact != null && email != null && phone != null;
    }
  }

  public TEnviNFe build(ConfiguracoesNfe config, Issuer issuer, FiscalDocument document) {
    boolean homologation = "2".equals(config.getAmbiente().getCodigo());
    LocalDateTime issuedAt = LocalDateTime.ofInstant(document.getIssuedAt(), BRASILIA);
    String modelo = DocumentoEnum.NFCE.getModelo();
    ChaveUtil chaveUtil =
        new ChaveUtil(
            config.getEstado(),
            issuer.getCnpj(),
            modelo,
            document.getSerie(),
            document.getNumero(),
            document.getTpEmis(),
            document.getCnf(),
            issuedAt);

    InfNFe infNFe = new InfNFe();
    infNFe.setId(chaveUtil.getChaveNF());
    infNFe.setVersao(ConstantesUtil.VERSAO.NFE);
    infNFe.setIde(ide(config, issuer, document, issuedAt, chaveUtil.getDigitoVerificador()));
    infNFe.setEmit(emit(issuer));
    Dest dest = dest(document, homologation);
    if (dest != null) {
      infNFe.setDest(dest);
    }

    SaleSnapshot sale = document.getSale();
    BigDecimal total = BigDecimal.ZERO;
    int index = 0;
    for (SaleSnapshot.Item item : sale.items()) {
      Det det = new Det();
      det.setNItem(String.valueOf(index + 1));
      det.setProd(prod(issuer, item, homologation && index == 0));
      det.setImposto(imposto(issuer, item));
      infNFe.getDet().add(det);
      total = total.add(money(item.total()));
      index++;
    }
    infNFe.setTotal(total(total));
    Transp transp = new Transp();
    transp.setModFrete("9");
    infNFe.setTransp(transp);
    infNFe.setPag(pag(sale.payments(), total));
    return envelope(infNFe, document.getNumero());
  }

  public TEnviNFe buildReturn(ConfiguracoesNfe config, Issuer issuer, ReturnDocument document) {
    boolean homologation = "2".equals(config.getAmbiente().getCodigo());
    LocalDateTime issuedAt = LocalDateTime.ofInstant(document.getIssuedAt(), BRASILIA);
    ChaveUtil chaveUtil =
        new ChaveUtil(
            config.getEstado(),
            issuer.getCnpj(),
            DocumentoEnum.NFE.getModelo(),
            document.getSerie(),
            document.getNumero(),
            "1",
            document.getCnf(),
            issuedAt);

    InfNFe infNFe = new InfNFe();
    infNFe.setId(chaveUtil.getChaveNF());
    infNFe.setVersao(ConstantesUtil.VERSAO.NFE);
    infNFe.setIde(returnIde(config, issuer, document, issuedAt, chaveUtil.getDigitoVerificador()));
    infNFe.setEmit(emit(issuer));
    infNFe.setDest(returnDest(issuer, document, homologation));

    ReturnSnapshot snapshot = document.getSnapshot();
    BigDecimal total = BigDecimal.ZERO;
    int index = 0;
    for (SaleSnapshot.Item item : snapshot.items()) {
      Prod prod = prod(issuer, item, false);
      prod.setCFOP(returnCfop(firstText(item.cfop(), issuer.getDefaultCfop(), "5102")));
      Det det = new Det();
      det.setNItem(String.valueOf(index + 1));
      det.setProd(prod);
      det.setImposto(returnImposto());
      infNFe.getDet().add(det);
      total = total.add(money(item.total()));
      index++;
    }
    infNFe.setTotal(total(total));
    Transp transp = new Transp();
    transp.setModFrete("9");
    infNFe.setTransp(transp);
    infNFe.setPag(noPayment());
    InfAdic infAdic = new InfAdic();
    infAdic.setInfCpl(text(returnNote(document), 5000));
    infNFe.setInfAdic(infAdic);
    return envelope(infNFe, document.getNumero());
  }

  private TEnviNFe envelope(InfNFe infNFe, Integer numero) {
    if (technician != null && technician.isConfigured()) {
      TInfRespTec respTec = new TInfRespTec();
      respTec.setCNPJ(technician.cnpj());
      respTec.setXContato(technician.contact());
      respTec.setEmail(technician.email());
      respTec.setFone(technician.phone());
      infNFe.setInfRespTec(respTec);
    }
    TNFe nfe = new TNFe();
    nfe.setInfNFe(infNFe);
    TEnviNFe enviNFe = new TEnviNFe();
    enviNFe.setVersao(ConstantesUtil.VERSAO.NFE);
    enviNFe.setIdLote(String.valueOf(numero));
    enviNFe.setIndSinc("1");
    enviNFe.getNFe().add(nfe);
    return enviNFe;
  }

  private Ide returnIde(
      ConfiguracoesNfe config,
      Issuer issuer,
      ReturnDocument document,
      LocalDateTime issuedAt,
      String digito) {
    Ide ide = new Ide();
    ide.setCUF(config.getEstado().getCodigoUF());
    ide.setCNF(document.getCnf());
    ide.setNatOp(RETURN_NAT_OP);
    ide.setMod(DocumentoEnum.NFE.getModelo());
    ide.setSerie(String.valueOf(document.getSerie()));
    ide.setNNF(String.valueOf(document.getNumero()));
    ide.setDhEmi(XmlNfeUtil.dataNfe(issuedAt, BRASILIA));
    ide.setDhSaiEnt(XmlNfeUtil.dataNfe(issuedAt, BRASILIA));
    ide.setTpNF("0");
    ide.setIdDest("1");
    ide.setCMunFG(issuer.getAddress().codigoMunicipio());
    ide.setTpImp("1");
    ide.setTpEmis("1");
    ide.setCDV(digito);
    ide.setTpAmb(config.getAmbiente().getCodigo());
    ide.setFinNFe("4");
    ide.setIndFinal(document.getConsumerCpf() == null ? "0" : "1");
    ide.setIndPres("1");
    ide.setProcEmi("0");
    ide.setVerProc("BiniTech Fiscal 1.0");
    NFref ref = new NFref();
    ref.setRefNFe(document.getReferencedChave());
    ide.getNFref().add(ref);
    return ide;
  }

  private Dest returnDest(Issuer issuer, ReturnDocument document, boolean homologation) {
    Dest dest = new Dest();
    String name;
    if (document.getConsumerCpf() != null) {
      dest.setCPF(document.getConsumerCpf());
      dest.setIndIEDest("9");
      String consumerName = document.getSnapshot().consumerName();
      name =
          consumerName != null && consumerName.trim().length() >= 2
              ? consumerName
              : "CONSUMIDOR FINAL";
    } else {
      dest.setCNPJ(issuer.getCnpj());
      dest.setIndIEDest("1");
      dest.setIE(issuer.getInscricaoEstadual());
      name = issuer.getRazaoSocial();
    }
    dest.setXNome(homologation ? HOMOLOGATION_NAME : text(name, 60));
    dest.setEnderDest(issuerAddress(issuer));
    return dest;
  }

  private static TEndereco issuerAddress(Issuer issuer) {
    Address address = issuer.getAddress();
    TEndereco ender = new TEndereco();
    ender.setXLgr(text(address.logradouro(), 60));
    ender.setNro(text(address.numero(), 60));
    if (address.complemento() != null) {
      ender.setXCpl(text(address.complemento(), 60));
    }
    ender.setXBairro(text(address.bairro(), 60));
    ender.setCMun(address.codigoMunicipio());
    ender.setXMun(text(address.cidade(), 60));
    ender.setUF(TUf.valueOf(address.uf()));
    ender.setCEP(address.cep());
    ender.setCPais("1058");
    ender.setXPais("Brasil");
    return ender;
  }

  private static Imposto returnImposto() {
    ICMS icms = new ICMS();
    ICMS.ICMSSN900 sn900 = new ICMS.ICMSSN900();
    sn900.setOrig("0");
    sn900.setCSOSN(RETURN_CSOSN);
    icms.setICMSSN900(sn900);

    PIS pis = new PIS();
    PIS.PISOutr pisOutr = new PIS.PISOutr();
    pisOutr.setCST(PIS_COFINS_ENTRY_CST);
    pisOutr.setVBC(ZERO);
    pisOutr.setPPIS(ZERO_RATE);
    pisOutr.setVPIS(ZERO);
    pis.setPISOutr(pisOutr);

    COFINS cofins = new COFINS();
    COFINS.COFINSOutr cofinsOutr = new COFINS.COFINSOutr();
    cofinsOutr.setCST(PIS_COFINS_ENTRY_CST);
    cofinsOutr.setVBC(ZERO);
    cofinsOutr.setPCOFINS(ZERO_RATE);
    cofinsOutr.setVCOFINS(ZERO);
    cofins.setCOFINSOutr(cofinsOutr);

    Imposto imposto = new Imposto();
    imposto.getContent().add(new JAXBElement<>(new QName("ICMS"), ICMS.class, icms));
    imposto.getContent().add(new JAXBElement<>(new QName("PIS"), PIS.class, pis));
    imposto.getContent().add(new JAXBElement<>(new QName("COFINS"), COFINS.class, cofins));
    return imposto;
  }

  private static Pag noPayment() {
    Pag.DetPag detPag = new Pag.DetPag();
    detPag.setTPag("90");
    detPag.setVPag(ZERO);
    Pag pag = new Pag();
    pag.getDetPag().add(detPag);
    return pag;
  }

  static String returnCfop(String saleCfop) {
    return RETURN_CFOP.getOrDefault(saleCfop, DEFAULT_RETURN_CFOP);
  }

  private static String returnNote(ReturnDocument document) {
    StringBuilder note =
        new StringBuilder("Devolucao de mercadoria pelo consumidor referente a NFC-e");
    if (document.getReferencedNumero() != null) {
      note.append(" n ").append(document.getReferencedNumero());
    }
    note.append(" chave ").append(document.getReferencedChave()).append(".");
    String reason = document.getSnapshot().reason();
    if (reason != null && !reason.isBlank()) {
      note.append(" Motivo: ").append(reason.trim());
    }
    return note.toString();
  }

  private Ide ide(
      ConfiguracoesNfe config,
      Issuer issuer,
      FiscalDocument document,
      LocalDateTime issuedAt,
      String digito) {
    Ide ide = new Ide();
    ide.setCUF(config.getEstado().getCodigoUF());
    ide.setCNF(document.getCnf());
    ide.setNatOp("VENDA");
    ide.setMod(DocumentoEnum.NFCE.getModelo());
    ide.setSerie(String.valueOf(document.getSerie()));
    ide.setNNF(String.valueOf(document.getNumero()));
    ide.setDhEmi(XmlNfeUtil.dataNfe(issuedAt, BRASILIA));
    ide.setTpNF("1");
    ide.setIdDest("1");
    ide.setCMunFG(issuer.getAddress().codigoMunicipio());
    ide.setTpImp("4");
    ide.setTpEmis(document.getTpEmis());
    ide.setCDV(digito);
    ide.setTpAmb(config.getAmbiente().getCodigo());
    ide.setFinNFe("1");
    ide.setIndFinal("1");
    ide.setIndPres("1");
    ide.setProcEmi("0");
    ide.setVerProc("BiniTech Fiscal 1.0");
    if ("9".equals(document.getTpEmis())) {
      ide.setDhCont(XmlNfeUtil.dataNfe(issuedAt, BRASILIA));
      ide.setXJust(CONTINGENCY_JUSTIFICATION);
    }
    return ide;
  }

  private Emit emit(Issuer issuer) {
    Address address = issuer.getAddress();
    Emit emit = new Emit();
    emit.setCNPJ(issuer.getCnpj());
    emit.setXNome(text(issuer.getRazaoSocial(), 60));
    if (issuer.getNomeFantasia() != null) {
      emit.setXFant(text(issuer.getNomeFantasia(), 60));
    }
    TEnderEmi ender = new TEnderEmi();
    ender.setXLgr(text(address.logradouro(), 60));
    ender.setNro(text(address.numero(), 60));
    if (address.complemento() != null) {
      ender.setXCpl(text(address.complemento(), 60));
    }
    ender.setXBairro(text(address.bairro(), 60));
    ender.setCMun(address.codigoMunicipio());
    ender.setXMun(text(address.cidade(), 60));
    ender.setUF(TUfEmi.valueOf(address.uf()));
    ender.setCEP(address.cep());
    ender.setCPais("1058");
    ender.setXPais("Brasil");
    if (issuer.getTelefone() != null && issuer.getTelefone().length() >= 6) {
      ender.setFone(issuer.getTelefone());
    }
    emit.setEnderEmit(ender);
    emit.setIE(issuer.getInscricaoEstadual());
    emit.setCRT(issuer.getRegime().crt());
    return emit;
  }

  private Dest dest(FiscalDocument document, boolean homologation) {
    if (document.getConsumerCpf() == null) {
      return null;
    }
    Dest dest = new Dest();
    dest.setCPF(document.getConsumerCpf());
    if (homologation) {
      dest.setXNome(HOMOLOGATION_NAME);
    } else if (document.getSale().consumerName() != null
        && document.getSale().consumerName().trim().length() >= 2) {
      dest.setXNome(text(document.getSale().consumerName(), 60));
    }
    dest.setIndIEDest("9");
    return dest;
  }

  private Prod prod(Issuer issuer, SaleSnapshot.Item item, boolean homologationLabel) {
    String ncm = firstText(item.ncm(), issuer.getDefaultNcm());
    if (ncm == null || !ncm.matches("\\d{8}")) {
      throw new BusinessException(
          "O produto \""
              + item.description()
              + "\" está sem NCM. Cadastre o NCM (8 dígitos) no produto ou um NCM padrão nos"
              + " dados fiscais.");
    }
    String cfop = firstText(item.cfop(), issuer.getDefaultCfop(), "5102");
    String ean =
        item.barcode() != null && item.barcode().matches("\\d{8}|\\d{12,14}")
            ? item.barcode()
            : "SEM GTIN";
    String unit = firstText(item.unit(), "UN");
    Prod prod = new Prod();
    prod.setCProd(text(firstText(item.code(), item.barcode(), "SEM-CODIGO"), 60));
    prod.setCEAN(ean);
    prod.setXProd(homologationLabel ? HOMOLOGATION_ITEM : text(item.description(), 120));
    prod.setNCM(ncm);
    prod.setCFOP(cfop);
    prod.setUCom(text(unit, 6));
    prod.setQCom(decimal(item.quantity(), 4));
    prod.setVUnCom(decimal(item.unitPrice(), 4));
    prod.setVProd(decimal(item.total(), 2));
    prod.setCEANTrib(ean);
    prod.setUTrib(text(unit, 6));
    prod.setQTrib(decimal(item.quantity(), 4));
    prod.setVUnTrib(decimal(item.unitPrice(), 4));
    prod.setIndTot("1");
    return prod;
  }

  private Imposto imposto(Issuer issuer, SaleSnapshot.Item item) {
    String csosn = firstText(item.csosn(), issuer.getDefaultCsosn(), "102");
    ICMS icms = new ICMS();
    if (CSOSN_SN102.contains(csosn)) {
      ICMS.ICMSSN102 sn102 = new ICMS.ICMSSN102();
      sn102.setOrig("0");
      sn102.setCSOSN(csosn);
      icms.setICMSSN102(sn102);
    } else if ("500".equals(csosn)) {
      ICMS.ICMSSN500 sn500 = new ICMS.ICMSSN500();
      sn500.setOrig("0");
      sn500.setCSOSN(csosn);
      icms.setICMSSN500(sn500);
    } else {
      throw new BusinessException(
          "CSOSN " + csosn + " não suportado na NFC-e. Use 102, 103, 300, 400 ou 500.");
    }

    PIS pis = new PIS();
    PIS.PISOutr pisOutr = new PIS.PISOutr();
    pisOutr.setCST(PIS_COFINS_CST);
    pisOutr.setVBC(ZERO);
    pisOutr.setPPIS(ZERO_RATE);
    pisOutr.setVPIS(ZERO);
    pis.setPISOutr(pisOutr);

    COFINS cofins = new COFINS();
    COFINS.COFINSOutr cofinsOutr = new COFINS.COFINSOutr();
    cofinsOutr.setCST(PIS_COFINS_CST);
    cofinsOutr.setVBC(ZERO);
    cofinsOutr.setPCOFINS(ZERO_RATE);
    cofinsOutr.setVCOFINS(ZERO);
    cofins.setCOFINSOutr(cofinsOutr);

    Imposto imposto = new Imposto();
    imposto.getContent().add(new JAXBElement<>(new QName("ICMS"), ICMS.class, icms));
    imposto.getContent().add(new JAXBElement<>(new QName("PIS"), PIS.class, pis));
    imposto.getContent().add(new JAXBElement<>(new QName("COFINS"), COFINS.class, cofins));
    return imposto;
  }

  private Total total(BigDecimal total) {
    ICMSTot tot = new ICMSTot();
    tot.setVBC(ZERO);
    tot.setVICMS(ZERO);
    tot.setVICMSDeson(ZERO);
    tot.setVFCP(ZERO);
    tot.setVBCST(ZERO);
    tot.setVST(ZERO);
    tot.setVFCPST(ZERO);
    tot.setVFCPSTRet(ZERO);
    tot.setVProd(decimal(total, 2));
    tot.setVFrete(ZERO);
    tot.setVSeg(ZERO);
    tot.setVDesc(ZERO);
    tot.setVII(ZERO);
    tot.setVIPI(ZERO);
    tot.setVIPIDevol(ZERO);
    tot.setVPIS(ZERO);
    tot.setVCOFINS(ZERO);
    tot.setVOutro(ZERO);
    tot.setVNF(decimal(total, 2));
    Total result = new Total();
    result.setICMSTot(tot);
    return result;
  }

  static Pag pag(List<SaleSnapshot.Payment> payments, BigDecimal total) {
    List<String> types = new ArrayList<>();
    List<BigDecimal> amounts = new ArrayList<>();
    if (payments != null) {
      for (SaleSnapshot.Payment payment : payments) {
        BigDecimal amount = payment.amount() == null ? BigDecimal.ZERO : money(payment.amount());
        if (amount.signum() > 0) {
          types.add(paymentType(payment.method()));
          amounts.add(amount);
        }
      }
    }
    BigDecimal paid = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    if (paid.compareTo(total) < 0) {
      types.add("05");
      amounts.add(total.subtract(paid));
      paid = total;
    }
    Pag pag = new Pag();
    for (int i = 0; i < types.size(); i++) {
      Pag.DetPag detPag = new Pag.DetPag();
      detPag.setIndPag("0");
      detPag.setTPag(types.get(i));
      detPag.setVPag(decimal(amounts.get(i), 2));
      if ("99".equals(types.get(i))) {
        detPag.setXPag("Outros");
      }
      if (CARD_PAYMENTS.contains(types.get(i))) {
        Pag.DetPag.Card card = new Pag.DetPag.Card();
        card.setTpIntegra("2");
        detPag.setCard(card);
      }
      pag.getDetPag().add(detPag);
    }
    BigDecimal change = paid.subtract(total);
    if (change.signum() > 0) {
      pag.setVTroco(decimal(change, 2));
    }
    return pag;
  }

  static String paymentType(String method) {
    if (method == null) {
      return "99";
    }
    return switch (method) {
      case "CASH" -> "01";
      case "CREDIT_CARD" -> "03";
      case "DEBIT_CARD" -> "04";
      case "PIX" -> "17";
      case "CREDIARIO" -> "05";
      default -> "99";
    };
  }

  static String text(String value, int max) {
    String plain =
        Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^\\x20-\\x7E]", "")
            .replaceAll("[<>&\"']", "")
            .replaceAll("\\s+", " ")
            .trim();
    if (plain.isEmpty()) {
      plain = "SEM DESCRICAO";
    }
    return plain.length() > max ? plain.substring(0, max).trim() : plain;
  }

  private static String firstText(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return null;
  }

  private static BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private static String decimal(BigDecimal value, int scale) {
    return value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
  }
}
