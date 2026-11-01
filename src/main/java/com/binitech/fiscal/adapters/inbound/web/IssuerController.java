package com.binitech.fiscal.adapters.inbound.web;

import com.binitech.fiscal.adapters.inbound.web.generated.api.IssuerApi;
import com.binitech.fiscal.adapters.inbound.web.generated.model.AddressDTO;
import com.binitech.fiscal.adapters.inbound.web.generated.model.IssuerDTO;
import com.binitech.fiscal.adapters.inbound.web.generated.model.IssuerRequestDTO;
import com.binitech.fiscal.adapters.inbound.web.generated.model.SefazStatusDTO;
import com.binitech.fiscal.application.ports.inbound.IssuerUseCase;
import com.binitech.fiscal.application.ports.outbound.AddressLookupPort;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class IssuerController implements IssuerApi {

  private final IssuerUseCase issuers;
  private final AddressLookupPort addressLookup;

  public IssuerController(IssuerUseCase issuers, AddressLookupPort addressLookup) {
    this.issuers = issuers;
    this.addressLookup = addressLookup;
  }

  @Override
  public ResponseEntity<IssuerDTO> getIssuer() {
    return issuers
        .find(CurrentSession.identity().tenantId())
        .map(WebMapper::issuer)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @Override
  public ResponseEntity<IssuerDTO> saveIssuer(IssuerRequestDTO issuerRequestDTO) {
    String tenantId = CurrentSession.storeAdmin().tenantId();
    return ResponseEntity.ok(
        WebMapper.issuer(issuers.save(tenantId, WebMapper.command(issuerRequestDTO))));
  }

  @Override
  public ResponseEntity<IssuerDTO> uploadCertificate(MultipartFile file, String password) {
    String tenantId = CurrentSession.storeAdmin().tenantId();
    try {
      return ResponseEntity.ok(
          WebMapper.issuer(issuers.uploadCertificate(tenantId, file.getBytes(), password)));
    } catch (IOException e) {
      throw new UncheckedIOException("Não foi possível ler o certificado enviado.", e);
    }
  }

  @Override
  public ResponseEntity<SefazStatusDTO> getSefazStatus() {
    return ResponseEntity.ok(
        WebMapper.sefazStatus(issuers.sefazStatus(CurrentSession.identity().tenantId())));
  }

  @Override
  public ResponseEntity<AddressDTO> lookupCep(String cep) {
    CurrentSession.identity();
    return addressLookup
        .findByCep(cep)
        .map(WebMapper::address)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
