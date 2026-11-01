package com.binitech.fiscal.adapters.inbound.web;

import com.binitech.fiscal.application.ports.inbound.NfceUseCase;
import com.binitech.fiscal.domain.FiscalDocument;
import com.binitech.fiscal.domain.FiscalStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fiscal/nfce")
public class NfceController {

  private final NfceUseCase nfce;

  public NfceController(NfceUseCase nfce) {
    this.nfce = nfce;
  }

  @GetMapping("/{saleId}")
  public ResponseEntity<NfceResponse> get(@PathVariable String saleId) {
    return nfce.find(CurrentSession.identity().tenantId(), saleId)
        .map(NfceResponse::from)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PostMapping("/{saleId}/issue")
  public NfceResponse issue(
      @PathVariable String saleId, @RequestBody(required = false) IssueRequest request) {
    return NfceResponse.from(
        nfce.requestIssue(
            CurrentSession.identity().tenantId(),
            saleId,
            request == null ? null : request.consumerCpf()));
  }

  @PostMapping("/{saleId}/cancel")
  public NfceResponse cancel(@PathVariable String saleId, @RequestBody CancelRequest request) {
    return NfceResponse.from(
        nfce.cancel(CurrentSession.storeAdmin().tenantId(), saleId, request.reason()));
  }

  @GetMapping(value = "/{saleId}/danfe", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> danfe(@PathVariable String saleId) {
    byte[] pdf = nfce.danfe(CurrentSession.identity().tenantId(), saleId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"nfce-" + saleId + ".pdf\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(pdf);
  }

  public record IssueRequest(String consumerCpf) {}

  public record CancelRequest(String reason) {}

  public record NfceResponse(
      String saleId,
      FiscalStatus status,
      int environment,
      Integer serie,
      Integer numero,
      String chave,
      String protocolo,
      String cStat,
      String xMotivo,
      String lastError,
      String qrCode,
      String urlChave,
      BigDecimal total,
      String consumerCpf,
      boolean contingency,
      Instant authorizedAt,
      Instant cancelledAt) {

    static NfceResponse from(FiscalDocument document) {
      return new NfceResponse(
          document.getSaleId(),
          document.getStatus(),
          document.getEnvironment(),
          document.getSerie(),
          document.getNumero(),
          document.getChave(),
          document.getProtocolo(),
          document.getCStat(),
          document.getXMotivo(),
          document.getLastError(),
          document.getQrCode(),
          document.getUrlChave(),
          document.getTotal(),
          document.getConsumerCpf(),
          "9".equals(document.getTpEmis()),
          document.getAuthorizedAt(),
          document.getCancelledAt());
    }
  }
}
