package com.binitech.fiscal.adapters.outbound.sefaz;

import br.com.swconsultoria.certificado.Certificado;
import br.com.swconsultoria.certificado.CertificadoService;
import br.com.swconsultoria.certificado.exception.CertificadoException;
import br.com.swconsultoria.nfe.Nfe;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.Evento;
import br.com.swconsultoria.nfe.dom.enuns.AmbienteEnum;
import br.com.swconsultoria.nfe.dom.enuns.DocumentoEnum;
import br.com.swconsultoria.nfe.dom.enuns.EstadosEnum;
import br.com.swconsultoria.nfe.dom.enuns.ServicosEnum;
import br.com.swconsultoria.nfe.exception.NfeException;
import br.com.swconsultoria.nfe.schemas.TEnviNFe;
import br.com.swconsultoria.nfe.schemas.TInutNFe;
import br.com.swconsultoria.nfe.schemas.TNFe;
import br.com.swconsultoria.nfe.schemas.TProtNFe;
import br.com.swconsultoria.nfe.schemas.TRetConsReciNFe;
import br.com.swconsultoria.nfe.schemas.TRetConsSitNFe;
import br.com.swconsultoria.nfe.schemas.TRetConsStatServ;
import br.com.swconsultoria.nfe.schemas.TRetEnviNFe;
import br.com.swconsultoria.nfe.schemas.TRetInutNFe;
import br.com.swconsultoria.nfe.schemas_eventos.TEnvEventoCancelamento;
import br.com.swconsultoria.nfe.schemas_eventos.TRetEnvEventoCancelamento;
import br.com.swconsultoria.nfe.schemas_eventos.TRetEventoCancelamento;
import br.com.swconsultoria.nfe.util.CancelamentoUtil;
import br.com.swconsultoria.nfe.util.InutilizacaoUtil;
import br.com.swconsultoria.nfe.util.NFCeUtil;
import br.com.swconsultoria.nfe.util.WebServiceUtil;
import br.com.swconsultoria.nfe.util.XmlNfeUtil;
import com.binitech.fiscal.application.ports.outbound.SefazPort;
import com.binitech.fiscal.domain.CertificateInfo;
import com.binitech.fiscal.domain.FiscalDocument;
import com.binitech.fiscal.domain.Issuer;
import com.binitech.fiscal.domain.PreparedNfce;
import com.binitech.fiscal.domain.ReturnDocument;
import com.binitech.fiscal.domain.SefazOutcome;
import com.binitech.fiscal.domain.SefazOutcome.Kind;
import com.binitech.fiscal.domain.exception.BusinessException;
import com.binitech.fiscal.domain.exception.SefazCommunicationException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import javax.net.ssl.SSLHandshakeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaNfeSefazAdapter implements SefazPort {

  private static final Logger log = LoggerFactory.getLogger(JavaNfeSefazAdapter.class);
  private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
  private static final Set<String> AUTHORIZED = Set.of("100", "150");
  private static final Set<String> DENIED = Set.of("110", "301", "302", "303");
  private static final Set<String> DUPLICATE = Set.of("204", "539");
  private static final Set<String> UNAVAILABLE = Set.of("108", "109");
  private static final Set<String> CANCEL_OK = Set.of("135", "155");
  private static final Set<String> VOID_OK = Set.of("102", "563");
  private static final String LOT_PROCESSED = "104";
  private static final String LOT_RECEIVED = "103";
  private static final String LOT_IN_PROCESS = "105";
  private static final String NOT_FOUND = "217";
  private static final String SERVICE_OK = "107";

  private final String schemasDirectory;
  private final int timeoutMillis;
  private final NfceXmlBuilder builder;

  public JavaNfeSefazAdapter(String schemasDirectory, int timeoutMillis, NfceXmlBuilder builder) {
    this.schemasDirectory = schemasDirectory;
    this.timeoutMillis = timeoutMillis;
    this.builder = builder;
  }

  @Override
  public CertificateInfo inspectCertificate(byte[] pfx, String password) {
    try {
      Certificado certificado = CertificadoService.certificadoPfxBytes(pfx, password);
      return new CertificateInfo(
          certificado.getNome(), certificado.getCnpjCpf(), certificado.getVencimento());
    } catch (CertificadoException | RuntimeException e) {
      throw new BusinessException(
          "Não foi possível abrir o certificado: confira o arquivo e a senha.");
    }
  }

  @Override
  public PreparedNfce prepare(Issuer issuer, Credentials credentials, FiscalDocument document) {
    ConfiguracoesNfe config = config(issuer, credentials);
    try {
      TEnviNFe signed = Nfe.montaNfe(config, builder.build(config, issuer, document), true);
      TNFe nfe = signed.getNFe().get(0);
      String chave = nfe.getInfNFe().getId().substring(3);
      String ambiente = config.getAmbiente().getCodigo();
      String qrUrl = WebServiceUtil.getUrl(config, DocumentoEnum.NFCE, ServicosEnum.URL_QRCODE);
      String qrCode =
          "9".equals(document.getTpEmis())
              ? NFCeUtil.getCodeQRCodeContingenciaV3(
                  chave,
                  ambiente,
                  nfe.getInfNFe().getIde().getDhEmi(),
                  nfe.getInfNFe().getTotal().getICMSTot().getVNF(),
                  document.getConsumerCpf() == null ? null : "2",
                  document.getConsumerCpf(),
                  qrUrl,
                  config.getCertificado())
              : NFCeUtil.getCodeQRCodeV3(chave, ambiente, qrUrl);
      String urlChave =
          WebServiceUtil.getUrl(config, DocumentoEnum.NFCE, ServicosEnum.URL_CONSULTANFCE);
      TNFe.InfNFeSupl supl = new TNFe.InfNFeSupl();
      supl.setQrCode(qrCode);
      supl.setUrlChave(urlChave);
      nfe.setInfNFeSupl(supl);
      return new PreparedNfce(chave, XmlNfeUtil.objectToXml(signed), qrCode, urlChave);
    } catch (NfeException e) {
      throw new BusinessException("XML da NFC-e inválido: " + rootMessage(e));
    } catch (javax.xml.bind.JAXBException e) {
      throw new BusinessException("Falha ao gerar o XML da NFC-e: " + rootMessage(e));
    }
  }

  @Override
  public PreparedNfce prepareReturn(
      Issuer issuer, Credentials credentials, ReturnDocument document) {
    ConfiguracoesNfe config = config(issuer, credentials);
    try {
      TEnviNFe signed = Nfe.montaNfe(config, builder.buildReturn(config, issuer, document), true);
      String chave = signed.getNFe().get(0).getInfNFe().getId().substring(3);
      return new PreparedNfce(chave, XmlNfeUtil.objectToXml(signed), null, null);
    } catch (NfeException e) {
      throw new BusinessException("XML da NF-e de devolução inválido: " + rootMessage(e));
    } catch (javax.xml.bind.JAXBException e) {
      throw new BusinessException("Falha ao gerar o XML da NF-e de devolução: " + rootMessage(e));
    }
  }

  @Override
  public SefazOutcome authorize(Issuer issuer, Credentials credentials, String signedXml) {
    return authorize(issuer, credentials, signedXml, DocumentoEnum.NFCE);
  }

  @Override
  public SefazOutcome authorizeNfe(Issuer issuer, Credentials credentials, String signedXml) {
    return authorize(issuer, credentials, signedXml, DocumentoEnum.NFE);
  }

  private SefazOutcome authorize(
      Issuer issuer, Credentials credentials, String signedXml, DocumentoEnum model) {
    ConfiguracoesNfe config = config(issuer, credentials);
    TEnviNFe enviNFe = XmlNfeUtil.xmlToObject(signedXml, TEnviNFe.class);
    TRetEnviNFe retorno;
    try {
      retorno = Nfe.enviarNfe(config, enviNFe, model);
    } catch (NfeException e) {
      throw communicationFailure("autorização", e);
    }
    String lotStatus = retorno.getCStat();
    if (UNAVAILABLE.contains(lotStatus)) {
      return SefazOutcome.of(Kind.UNAVAILABLE, lotStatus, retorno.getXMotivo());
    }
    if (LOT_PROCESSED.equals(lotStatus) && retorno.getProtNFe() != null) {
      return fromProtocol(enviNFe, retorno.getProtNFe());
    }
    if (LOT_RECEIVED.equals(lotStatus) && retorno.getInfRec() != null) {
      return pollReceipt(config, enviNFe, retorno.getInfRec().getNRec(), model);
    }
    return SefazOutcome.of(classify(lotStatus), lotStatus, retorno.getXMotivo());
  }

  private SefazOutcome pollReceipt(
      ConfiguracoesNfe config, TEnviNFe enviNFe, String receipt, DocumentoEnum model) {
    for (int attempt = 0; attempt < 5; attempt++) {
      TRetConsReciNFe retorno;
      try {
        Thread.sleep(1000L * (attempt + 1));
        retorno = Nfe.consultaRecibo(config, receipt, model);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new SefazCommunicationException("Consulta do recibo interrompida.", true, e);
      } catch (NfeException e) {
        throw communicationFailure("consulta do recibo", e, true);
      }
      if (LOT_IN_PROCESS.equals(retorno.getCStat())) {
        continue;
      }
      if (retorno.getProtNFe() != null && !retorno.getProtNFe().isEmpty()) {
        return fromProtocol(enviNFe, retorno.getProtNFe().get(0));
      }
      return SefazOutcome.of(
          classify(retorno.getCStat()), retorno.getCStat(), retorno.getXMotivo());
    }
    throw new SefazCommunicationException("Lote ainda em processamento na SEFAZ.", true, null);
  }

  private SefazOutcome fromProtocol(TEnviNFe enviNFe, TProtNFe protocol) {
    String cStat = protocol.getInfProt().getCStat();
    String xMotivo = protocol.getInfProt().getXMotivo();
    Kind kind = classify(cStat);
    if (kind != Kind.AUTHORIZED) {
      return SefazOutcome.of(kind, cStat, xMotivo);
    }
    return new SefazOutcome(
        kind,
        cStat,
        xMotivo,
        protocol.getInfProt().getNProt(),
        protocol.getInfProt().getDhRecbto(),
        proc(enviNFe, protocol));
  }

  @Override
  public SefazOutcome consult(
      Issuer issuer, Credentials credentials, String chave, String signedXml) {
    return consult(issuer, credentials, chave, signedXml, DocumentoEnum.NFCE);
  }

  @Override
  public SefazOutcome consultNfe(
      Issuer issuer, Credentials credentials, String chave, String signedXml) {
    return consult(issuer, credentials, chave, signedXml, DocumentoEnum.NFE);
  }

  private SefazOutcome consult(
      Issuer issuer, Credentials credentials, String chave, String signedXml, DocumentoEnum model) {
    ConfiguracoesNfe config = config(issuer, credentials);
    TRetConsSitNFe retorno;
    try {
      retorno = Nfe.consultaXml(config, chave, model);
    } catch (NfeException e) {
      throw communicationFailure("consulta", e, true);
    }
    String cStat = retorno.getCStat();
    if (NOT_FOUND.equals(cStat)) {
      return SefazOutcome.of(Kind.NOT_FOUND, cStat, retorno.getXMotivo());
    }
    if (UNAVAILABLE.contains(cStat)) {
      return SefazOutcome.of(Kind.UNAVAILABLE, cStat, retorno.getXMotivo());
    }
    TProtNFe protocol = retorno.getProtNFe();
    if (protocol != null && AUTHORIZED.contains(protocol.getInfProt().getCStat())) {
      String proc =
          signedXml == null
              ? null
              : proc(XmlNfeUtil.xmlToObject(signedXml, TEnviNFe.class), protocol);
      return new SefazOutcome(
          Kind.AUTHORIZED,
          protocol.getInfProt().getCStat(),
          protocol.getInfProt().getXMotivo(),
          protocol.getInfProt().getNProt(),
          protocol.getInfProt().getDhRecbto(),
          proc);
    }
    return SefazOutcome.of(classify(cStat), cStat, retorno.getXMotivo());
  }

  @Override
  public SefazOutcome cancel(
      Issuer issuer, Credentials credentials, String chave, String protocolo, String reason) {
    ConfiguracoesNfe config = config(issuer, credentials);
    Evento evento = new Evento();
    evento.setChave(chave);
    evento.setProtocolo(protocolo);
    evento.setCnpj(issuer.getCnpj());
    evento.setMotivo(NfceXmlBuilder.text(reason, 255));
    evento.setDataEvento(LocalDateTime.now(BRASILIA));
    try {
      TEnvEventoCancelamento envio = CancelamentoUtil.montaCancelamento(evento, config);
      TRetEnvEventoCancelamento retorno = Nfe.cancelarNfe(config, envio, true, DocumentoEnum.NFCE);
      if (retorno.getRetEvento() == null || retorno.getRetEvento().isEmpty()) {
        return SefazOutcome.of(
            classify(retorno.getCStat()), retorno.getCStat(), retorno.getXMotivo());
      }
      TRetEventoCancelamento evento0 = retorno.getRetEvento().get(0);
      String cStat = evento0.getInfEvento().getCStat();
      if (CANCEL_OK.contains(cStat)) {
        return new SefazOutcome(
            Kind.AUTHORIZED,
            cStat,
            evento0.getInfEvento().getXMotivo(),
            evento0.getInfEvento().getNProt(),
            evento0.getInfEvento().getDhRegEvento(),
            CancelamentoUtil.criaProcEventoCancelamento(config, envio, evento0));
      }
      return SefazOutcome.of(classify(cStat), cStat, evento0.getInfEvento().getXMotivo());
    } catch (NfeException e) {
      throw communicationFailure("cancelamento", e, true);
    } catch (javax.xml.bind.JAXBException e) {
      throw new BusinessException("Falha ao montar o XML do cancelamento: " + rootMessage(e));
    }
  }

  @Override
  public SefazOutcome voidNumber(
      Issuer issuer, Credentials credentials, int serie, int numero, String reason) {
    return voidNumber(issuer, credentials, serie, numero, reason, DocumentoEnum.NFCE);
  }

  @Override
  public SefazOutcome voidNfeNumber(
      Issuer issuer, Credentials credentials, int serie, int numero, String reason) {
    return voidNumber(issuer, credentials, serie, numero, reason, DocumentoEnum.NFE);
  }

  private SefazOutcome voidNumber(
      Issuer issuer,
      Credentials credentials,
      int serie,
      int numero,
      String reason,
      DocumentoEnum model) {
    ConfiguracoesNfe config = config(issuer, credentials);
    TInutNFe inutilizacao =
        InutilizacaoUtil.montaInutilizacao(
            model,
            issuer.getCnpj(),
            serie,
            numero,
            numero,
            NfceXmlBuilder.text(reason, 255),
            LocalDateTime.now(BRASILIA),
            config);
    try {
      TRetInutNFe retorno = Nfe.inutilizacao(config, inutilizacao, model, true);
      String cStat = retorno.getInfInut().getCStat();
      Kind kind = VOID_OK.contains(cStat) ? Kind.AUTHORIZED : classify(cStat);
      return new SefazOutcome(
          kind,
          cStat,
          retorno.getInfInut().getXMotivo(),
          retorno.getInfInut().getNProt(),
          retorno.getInfInut().getDhRecbto(),
          kind == Kind.AUTHORIZED
              ? InutilizacaoUtil.criaProcInutilizacao(config, inutilizacao, retorno)
              : null);
    } catch (NfeException e) {
      throw communicationFailure("inutilização", e, true);
    } catch (javax.xml.bind.JAXBException e) {
      throw new BusinessException("Falha ao montar a inutilização: " + rootMessage(e));
    }
  }

  @Override
  public SefazOutcome serviceStatus(Issuer issuer, Credentials credentials) {
    ConfiguracoesNfe config = config(issuer, credentials);
    try {
      TRetConsStatServ retorno = Nfe.statusServico(config, DocumentoEnum.NFCE);
      Kind kind = SERVICE_OK.equals(retorno.getCStat()) ? Kind.AUTHORIZED : Kind.UNAVAILABLE;
      return SefazOutcome.of(kind, retorno.getCStat(), retorno.getXMotivo());
    } catch (NfeException e) {
      throw communicationFailure("status do serviço", e, false);
    }
  }

  private ConfiguracoesNfe config(Issuer issuer, Credentials credentials) {
    try {
      Certificado certificado =
          CertificadoService.certificadoPfxBytes(credentials.pfx(), credentials.password());
      certificado.setModoMultithreading(true);
      ConfiguracoesNfe config =
          ConfiguracoesNfe.criarConfiguracoes(
              EstadosEnum.valueOf(issuer.getAddress().uf()),
              issuer.getEnvironment() == 1 ? AmbienteEnum.PRODUCAO : AmbienteEnum.HOMOLOGACAO,
              certificado,
              schemasDirectory,
              BRASILIA);
      config.setTimeout(timeoutMillis);
      config.setRetry(1);
      return config;
    } catch (CertificadoException e) {
      throw new BusinessException("Certificado A1 inválido: " + rootMessage(e));
    }
  }

  private String proc(TEnviNFe enviNFe, TProtNFe protocol) {
    try {
      return XmlNfeUtil.criaNfeProc(enviNFe, protocol);
    } catch (javax.xml.bind.JAXBException | NfeException e) {
      log.warn("Falha ao montar o nfeProc: {}", rootMessage(e));
      return null;
    }
  }

  static Kind classify(String cStat) {
    if (cStat == null) {
      return Kind.UNAVAILABLE;
    }
    if (AUTHORIZED.contains(cStat)) {
      return Kind.AUTHORIZED;
    }
    if (DENIED.contains(cStat)) {
      return Kind.DENIED;
    }
    if (DUPLICATE.contains(cStat)) {
      return Kind.DUPLICATE;
    }
    if (UNAVAILABLE.contains(cStat)) {
      return Kind.UNAVAILABLE;
    }
    return Kind.REJECTED;
  }

  private SefazCommunicationException communicationFailure(String operation, NfeException e) {
    return communicationFailure(operation, e, !isPreSendFailure(e));
  }

  private SefazCommunicationException communicationFailure(
      String operation, NfeException e, boolean possiblyDelivered) {
    log.warn("Falha de comunicação com a SEFAZ na {}: {}", operation, rootMessage(e));
    return new SefazCommunicationException(
        "Falha de comunicação com a SEFAZ (" + operation + "): " + rootMessage(e),
        possiblyDelivered,
        e);
  }

  static boolean isPreSendFailure(Throwable error) {
    Throwable current = error;
    while (current != null) {
      if (current instanceof ConnectException
          || current instanceof UnknownHostException
          || current instanceof NoRouteToHostException
          || current instanceof SSLHandshakeException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static String rootMessage(Throwable error) {
    Throwable current = error;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    String message = current.getMessage();
    return message == null ? current.getClass().getSimpleName() : message;
  }
}
