package com.binitech.fiscal.adapters.inbound.web;

import com.binitech.fiscal.adapters.inbound.web.generated.api.NfceApi;
import com.binitech.fiscal.adapters.inbound.web.generated.model.CancelNfceRequestDTO;
import com.binitech.fiscal.adapters.inbound.web.generated.model.IssueNfceRequestDTO;
import com.binitech.fiscal.adapters.inbound.web.generated.model.NfceDTO;
import com.binitech.fiscal.application.ports.inbound.NfceUseCase;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NfceController implements NfceApi {

  private final NfceUseCase nfce;

  public NfceController(NfceUseCase nfce) {
    this.nfce = nfce;
  }

  @Override
  public ResponseEntity<NfceDTO> getNfce(String saleId) {
    return nfce.find(CurrentSession.identity().tenantId(), saleId)
        .map(WebMapper::nfce)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @Override
  public ResponseEntity<NfceDTO> issueNfce(String saleId, IssueNfceRequestDTO issueNfceRequestDTO) {
    return ResponseEntity.ok(
        WebMapper.nfce(
            nfce.requestIssue(
                CurrentSession.identity().tenantId(),
                saleId,
                issueNfceRequestDTO == null ? null : issueNfceRequestDTO.getConsumerCpf())));
  }

  @Override
  public ResponseEntity<NfceDTO> cancelNfce(
      String saleId, CancelNfceRequestDTO cancelNfceRequestDTO) {
    return ResponseEntity.ok(
        WebMapper.nfce(
            nfce.cancel(
                CurrentSession.storeAdmin().tenantId(), saleId, cancelNfceRequestDTO.getReason())));
  }

  @Override
  public ResponseEntity<Resource> downloadDanfe(String saleId) {
    byte[] pdf = nfce.danfe(CurrentSession.identity().tenantId(), saleId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"nfce-" + saleId + ".pdf\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(new ByteArrayResource(pdf));
  }
}
