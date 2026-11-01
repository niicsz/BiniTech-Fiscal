package com.binitech.fiscal.application.ports.outbound;

import com.binitech.fiscal.domain.CertificateInfo;
import com.binitech.fiscal.domain.FiscalDocument;
import com.binitech.fiscal.domain.Issuer;
import com.binitech.fiscal.domain.PreparedNfce;
import com.binitech.fiscal.domain.ReturnDocument;
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

  PreparedNfce prepareReturn(Issuer issuer, Credentials credentials, ReturnDocument document);

  SefazOutcome authorizeNfe(Issuer issuer, Credentials credentials, String signedXml);

  SefazOutcome consultNfe(Issuer issuer, Credentials credentials, String chave, String signedXml);

  SefazOutcome voidNfeNumber(
      Issuer issuer, Credentials credentials, int serie, int numero, String reason);

  record Credentials(byte[] pfx, String password) {}
}
