package com.binitech.fiscal.application.ports.inbound;

import com.binitech.fiscal.domain.FiscalDocument;
import com.binitech.fiscal.domain.SaleSnapshot;
import java.util.Optional;

public interface NfceUseCase {

  void registerSale(SaleSnapshot sale);

  FiscalDocument requestIssue(String tenantId, String saleId, String consumerCpf);

  Optional<FiscalDocument> find(String tenantId, String saleId);

  FiscalDocument cancel(String tenantId, String saleId, String reason);

  byte[] danfe(String tenantId, String saleId);
}
