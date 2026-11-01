package com.binitech.fiscal.adapters.inbound.web;

import com.binitech.fiscal.adapters.inbound.web.generated.api.ReturnsApi;
import com.binitech.fiscal.adapters.inbound.web.generated.model.ReturnDocumentDTO;
import com.binitech.fiscal.application.ports.inbound.ReturnUseCase;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReturnsController implements ReturnsApi {

  private final ReturnUseCase returns;

  public ReturnsController(ReturnUseCase returns) {
    this.returns = returns;
  }

  @Override
  public ResponseEntity<List<ReturnDocumentDTO>> listReturns(String saleId) {
    return ResponseEntity.ok(
        returns.list(CurrentSession.identity().tenantId(), saleId).stream()
            .map(WebMapper::returnDocument)
            .toList());
  }

  @Override
  public ResponseEntity<ReturnDocumentDTO> retryReturn(String returnId) {
    return ResponseEntity.ok(
        WebMapper.returnDocument(returns.retry(CurrentSession.storeAdmin().tenantId(), returnId)));
  }

  @Override
  public ResponseEntity<Resource> downloadReturnDanfe(String returnId) {
    byte[] pdf = returns.danfe(CurrentSession.identity().tenantId(), returnId);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "inline; filename=\"nfe-devolucao-" + returnId + ".pdf\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(new ByteArrayResource(pdf));
  }
}
