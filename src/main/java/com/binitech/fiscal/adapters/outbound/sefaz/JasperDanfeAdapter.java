package com.binitech.fiscal.adapters.outbound.sefaz;

import br.com.swconsultoria.nfe.impressao.ImpressaoNfeUtil;
import com.binitech.fiscal.application.ports.outbound.DanfePort;
import com.binitech.fiscal.domain.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JasperDanfeAdapter implements DanfePort {

  private static final Logger log = LoggerFactory.getLogger(JasperDanfeAdapter.class);

  @Override
  public byte[] render(String xml, String urlConsulta) {
    try {
      return ImpressaoNfeUtil.impressaoPdfByte(
          ImpressaoNfeUtil.impressaoPadraoNFCe(xml, urlConsulta));
    } catch (Exception | LinkageError e) {
      log.error("Falha ao gerar o DANFE da NFC-e: {}", e.toString(), e);
      throw new BusinessException("Não foi possível gerar o cupom da NFC-e.");
    }
  }
}
