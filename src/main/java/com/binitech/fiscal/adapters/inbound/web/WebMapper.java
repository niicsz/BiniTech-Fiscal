package com.binitech.fiscal.adapters.inbound.web;

import com.binitech.fiscal.adapters.inbound.web.generated.model.AddressDTO;
import com.binitech.fiscal.adapters.inbound.web.generated.model.FiscalPreferencesDTO;
import com.binitech.fiscal.adapters.inbound.web.generated.model.IssuerDTO;
import com.binitech.fiscal.adapters.inbound.web.generated.model.IssuerRequestDTO;
import com.binitech.fiscal.adapters.inbound.web.generated.model.NfceDTO;
import com.binitech.fiscal.adapters.inbound.web.generated.model.NfceStatus;
import com.binitech.fiscal.adapters.inbound.web.generated.model.ReturnDocumentDTO;
import com.binitech.fiscal.adapters.inbound.web.generated.model.SefazOutcomeKind;
import com.binitech.fiscal.adapters.inbound.web.generated.model.SefazStatusDTO;
import com.binitech.fiscal.application.ports.inbound.IssuerUseCase.IssuerCommand;
import com.binitech.fiscal.domain.Address;
import com.binitech.fiscal.domain.FiscalDocument;
import com.binitech.fiscal.domain.FiscalPreference;
import com.binitech.fiscal.domain.Issuer;
import com.binitech.fiscal.domain.ReturnDocument;
import com.binitech.fiscal.domain.SefazOutcome;
import com.binitech.fiscal.domain.TaxRegime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class WebMapper {

  private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

  private WebMapper() {}

  public static IssuerCommand command(IssuerRequestDTO request) {
    return new IssuerCommand(
        request.getCnpj(),
        request.getRazaoSocial(),
        request.getNomeFantasia(),
        request.getInscricaoEstadual(),
        request.getRegime() == null ? null : TaxRegime.valueOf(request.getRegime().name()),
        address(request.getAddress()),
        request.getTelefone(),
        request.getEmail(),
        request.getSerie(),
        request.getNfeSerie(),
        request.getDefaultNcm(),
        request.getDefaultCfop(),
        request.getDefaultCsosn(),
        Boolean.TRUE.equals(request.getAutoIssue()),
        Boolean.TRUE.equals(request.getContingencyEnabled()));
  }

  public static IssuerDTO issuer(Issuer issuer) {
    return new IssuerDTO()
        .cnpj(issuer.getCnpj())
        .razaoSocial(issuer.getRazaoSocial())
        .nomeFantasia(issuer.getNomeFantasia())
        .inscricaoEstadual(issuer.getInscricaoEstadual())
        .regime(
            issuer.getRegime() == null
                ? null
                : com.binitech.fiscal.adapters.inbound.web.generated.model.TaxRegime.fromValue(
                    issuer.getRegime().name()))
        .address(address(issuer.getAddress()))
        .telefone(issuer.getTelefone())
        .email(issuer.getEmail())
        .serie(issuer.getSerie())
        .nfeSerie(issuer.getNfeSerie())
        .environment(issuer.getEnvironment())
        .defaultNcm(issuer.getDefaultNcm())
        .defaultCfop(issuer.getDefaultCfop())
        .defaultCsosn(issuer.getDefaultCsosn())
        .autoIssue(issuer.isAutoIssue())
        .contingencyEnabled(issuer.isContingencyEnabled())
        .certificateHolder(
            issuer.getCertificate() == null ? null : issuer.getCertificate().holder())
        .certificateValidUntil(
            issuer.getCertificate() == null ? null : issuer.getCertificate().validUntil())
        .readyToIssue(issuer.isReadyToIssue(LocalDate.now(BRASILIA)));
  }

  public static AddressDTO address(Address address) {
    if (address == null) {
      return null;
    }
    return new AddressDTO()
        .logradouro(address.logradouro())
        .numero(address.numero())
        .complemento(address.complemento())
        .bairro(address.bairro())
        .codigoMunicipio(address.codigoMunicipio())
        .cidade(address.cidade())
        .uf(address.uf())
        .cep(address.cep());
  }

  public static Address address(AddressDTO address) {
    if (address == null) {
      return null;
    }
    return new Address(
        address.getLogradouro(),
        address.getNumero(),
        address.getComplemento(),
        address.getBairro(),
        address.getCodigoMunicipio(),
        address.getCidade(),
        address.getUf(),
        address.getCep());
  }

  public static FiscalPreferencesDTO preferences(FiscalPreference preference) {
    if (preference == null) {
      return new FiscalPreferencesDTO().decided(false).nfceEnabled(false);
    }
    return new FiscalPreferencesDTO()
        .decided(true)
        .nfceEnabled(preference.nfceEnabled())
        .decidedAt(offset(preference.decidedAt()));
  }

  public static SefazStatusDTO sefazStatus(SefazOutcome outcome) {
    return new SefazStatusDTO()
        .kind(SefazOutcomeKind.fromValue(outcome.kind().name()))
        .cStat(outcome.cStat())
        .xMotivo(outcome.xMotivo());
  }

  public static NfceDTO nfce(FiscalDocument document) {
    return new NfceDTO()
        .saleId(document.getSaleId())
        .status(
            document.getStatus() == null ? null : NfceStatus.fromValue(document.getStatus().name()))
        .environment(document.getEnvironment())
        .serie(document.getSerie())
        .numero(document.getNumero())
        .chave(document.getChave())
        .protocolo(document.getProtocolo())
        .cStat(document.getCStat())
        .xMotivo(document.getXMotivo())
        .lastError(document.getLastError())
        .qrCode(document.getQrCode())
        .urlChave(document.getUrlChave())
        .total(document.getTotal())
        .consumerCpf(document.getConsumerCpf())
        .contingency("9".equals(document.getTpEmis()))
        .authorizedAt(offset(document.getAuthorizedAt()))
        .cancelledAt(offset(document.getCancelledAt()));
  }

  public static ReturnDocumentDTO returnDocument(ReturnDocument document) {
    return new ReturnDocumentDTO()
        .returnId(document.getReturnId())
        .saleId(document.getSaleId())
        .status(
            document.getStatus() == null
                ? null
                : com.binitech.fiscal.adapters.inbound.web.generated.model.ReturnStatus.fromValue(
                    document.getStatus().name()))
        .environment(document.getEnvironment())
        .serie(document.getSerie())
        .numero(document.getNumero())
        .chave(document.getChave())
        .protocolo(document.getProtocolo())
        .referencedChave(document.getReferencedChave())
        .cStat(document.getCStat())
        .xMotivo(document.getXMotivo())
        .lastError(document.getLastError())
        .total(document.getTotal())
        .authorizedAt(offset(document.getAuthorizedAt()));
  }

  private static OffsetDateTime offset(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
