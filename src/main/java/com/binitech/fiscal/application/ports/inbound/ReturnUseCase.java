package com.binitech.fiscal.application.ports.inbound;

import com.binitech.fiscal.domain.ReturnDocument;
import com.binitech.fiscal.domain.ReturnSnapshot;
import java.util.List;

public interface ReturnUseCase {

  void registerReturn(ReturnSnapshot snapshot);

  List<ReturnDocument> list(String tenantId, String saleId);

  ReturnDocument retry(String tenantId, String returnId);

  byte[] danfe(String tenantId, String returnId);
}
