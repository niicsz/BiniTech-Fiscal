package com.binitech.fiscal.application.ports.outbound;

import com.binitech.fiscal.domain.CertificateInfo;
import com.binitech.fiscal.domain.FiscalDocument;
import com.binitech.fiscal.domain.Issuer;
import com.binitech.fiscal.domain.PreparedNfce;
import com.binitech.fiscal.domain.SefazOutcome;

public interface SefazPort {

  CertificateInfo inspectCertificate(byte[] pfx, String password);

  PreparedNfce prepare(Issuer issuer, Credentials credentials, FiscalDocument document);

  SefazOutcome authorize(Issuer issuer, Credentials credentials, String signedXml);

  SefazOutcome consult(Issuer issuer, Credentials credentials, String chave, String signedXml);

  SefazOutcome cancel(
      Issuer issuer, Credentials credentials, String chave, String protocolo, String reason);

  SefazOutcome voidNumber(
      Issuer issuer, Credentials credentials, int serie, int numero, String reason);

  SefazOutcome serviceStatus(Issuer issuer, Credentials credentials);

  record Credentials(byte[] pfx, String password) {}
}
