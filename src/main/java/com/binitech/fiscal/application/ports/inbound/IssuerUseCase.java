package com.binitech.fiscal.application.ports.inbound;

import com.binitech.fiscal.domain.Address;
import com.binitech.fiscal.domain.Issuer;
import com.binitech.fiscal.domain.SefazOutcome;
import com.binitech.fiscal.domain.TaxRegime;
import java.util.Optional;

public interface IssuerUseCase {

  Optional<Issuer> find(String tenantId);

  Issuer save(String tenantId, IssuerCommand command);

  Issuer uploadCertificate(String tenantId, byte[] pfx, String password);

  SefazOutcome sefazStatus(String tenantId);

  record IssuerCommand(
      String cnpj,
      String razaoSocial,
      String nomeFantasia,
      String inscricaoEstadual,
      TaxRegime regime,
      Address address,
      String telefone,
      String email,
      Integer serie,
      String defaultNcm,
      String defaultCfop,
      String defaultCsosn,
      boolean autoIssue,
      boolean contingencyEnabled) {}
}
