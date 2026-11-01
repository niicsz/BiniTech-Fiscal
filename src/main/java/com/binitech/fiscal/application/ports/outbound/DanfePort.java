package com.binitech.fiscal.application.ports.outbound;

public interface DanfePort {
  byte[] render(String xml, String urlConsulta);

  byte[] renderNfe(String xml);
}
