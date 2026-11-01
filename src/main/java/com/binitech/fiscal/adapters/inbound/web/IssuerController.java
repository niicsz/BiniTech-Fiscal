package com.binitech.fiscal.adapters.inbound.web;

import com.binitech.fiscal.application.ports.inbound.IssuerUseCase;
import com.binitech.fiscal.application.ports.inbound.IssuerUseCase.IssuerCommand;
import com.binitech.fiscal.application.ports.outbound.AddressLookupPort;
import com.binitech.fiscal.domain.Address;
import com.binitech.fiscal.domain.Issuer;
import com.binitech.fiscal.domain.SefazOutcome;
import com.binitech.fiscal.domain.TaxRegime;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/fiscal")
public class IssuerController {

  private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

  private final IssuerUseCase issuers;
  private final AddressLookupPort addressLookup;

  public IssuerController(IssuerUseCase issuers, AddressLookupPort addressLookup) {
    this.issuers = issuers;
    this.addressLookup = addressLookup;
  }

  @GetMapping("/issuer")
  public ResponseEntity<IssuerResponse> get() {
    return issuers
        .find(CurrentSession.identity().tenantId())
        .map(IssuerResponse::from)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PutMapping("/issuer")
  public IssuerResponse save(@RequestBody IssuerRequest request) {
    String tenantId = CurrentSession.storeAdmin().tenantId();
    return IssuerResponse.from(issuers.save(tenantId, request.toCommand()));
  }

  @PostMapping(value = "/issuer/certificate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public IssuerResponse uploadCertificate(
      @RequestParam("file") MultipartFile file, @RequestParam("password") String password)
      throws IOException {
    String tenantId = CurrentSession.storeAdmin().tenantId();
    return IssuerResponse.from(issuers.uploadCertificate(tenantId, file.getBytes(), password));
  }

  @GetMapping("/issuer/sefaz-status")
  public SefazOutcome sefazStatus() {
    return issuers.sefazStatus(CurrentSession.identity().tenantId());
  }

  @GetMapping("/cep/{cep}")
  public ResponseEntity<Address> cep(@PathVariable String cep) {
    CurrentSession.identity();
    return addressLookup
        .findByCep(cep)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  public record IssuerRequest(
      String cnpj,
      String razaoSocial,
      String nomeFantasia,
      String inscricaoEstadual,
      TaxRegime regime,
      Address address,
      String telefone,
      String email,
      Integer serie,
      String defaultNcm,
      String defaultCfop,
      String defaultCsosn,
      boolean autoIssue,
      boolean contingencyEnabled) {

    IssuerCommand toCommand() {
      return new IssuerCommand(
          cnpj,
          razaoSocial,
          nomeFantasia,
          inscricaoEstadual,
          regime,
          address,
          telefone,
          email,
          serie,
          defaultNcm,
          defaultCfop,
          defaultCsosn,
          autoIssue,
          contingencyEnabled);
    }
  }

  public record IssuerResponse(
      String cnpj,
      String razaoSocial,
      String nomeFantasia,
      String inscricaoEstadual,
      TaxRegime regime,
      Address address,
      String telefone,
      String email,
      int serie,
      int environment,
      String defaultNcm,
      String defaultCfop,
      String defaultCsosn,
      boolean autoIssue,
      boolean contingencyEnabled,
      String certificateHolder,
      LocalDate certificateValidUntil,
      boolean readyToIssue) {

    static IssuerResponse from(Issuer issuer) {
      return new IssuerResponse(
          issuer.getCnpj(),
          issuer.getRazaoSocial(),
          issuer.getNomeFantasia(),
          issuer.getInscricaoEstadual(),
          issuer.getRegime(),
          issuer.getAddress(),
          issuer.getTelefone(),
          issuer.getEmail(),
          issuer.getSerie(),
          issuer.getEnvironment(),
          issuer.getDefaultNcm(),
          issuer.getDefaultCfop(),
          issuer.getDefaultCsosn(),
          issuer.isAutoIssue(),
          issuer.isContingencyEnabled(),
          issuer.getCertificate() == null ? null : issuer.getCertificate().holder(),
          issuer.getCertificate() == null ? null : issuer.getCertificate().validUntil(),
          issuer.isReadyToIssue(LocalDate.now(BRASILIA)));
    }
  }
}
