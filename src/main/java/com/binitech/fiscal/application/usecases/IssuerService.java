package com.binitech.fiscal.application.usecases;

import com.binitech.fiscal.application.ports.inbound.IssuerUseCase;
import com.binitech.fiscal.application.ports.outbound.IssuerRepositoryPort;
import com.binitech.fiscal.application.ports.outbound.SecretCipherPort;
import com.binitech.fiscal.application.ports.outbound.SefazPort;
import com.binitech.fiscal.application.ports.outbound.SefazPort.Credentials;
import com.binitech.fiscal.domain.Address;
import com.binitech.fiscal.domain.CertificateInfo;
import com.binitech.fiscal.domain.Issuer;
import com.binitech.fiscal.domain.SefazOutcome;
import com.binitech.fiscal.domain.StoredCertificate;
import com.binitech.fiscal.domain.exception.BusinessException;
import com.binitech.fiscal.domain.exception.SefazCommunicationException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IssuerService implements IssuerUseCase {

  private static final Logger log = LoggerFactory.getLogger(IssuerService.class);
  private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
  private static final Pattern CNPJ = Pattern.compile("^[0-9A-Z]{12}\\d{2}$");
  private static final Pattern UF = Pattern.compile("^[A-Z]{2}$");
  private static final int MAX_CERTIFICATE_BYTES = 50 * 1024;

  private final IssuerRepositoryPort issuers;
  private final SefazPort sefaz;
  private final SecretCipherPort cipher;
  private final int environment;
  private final Clock clock;

  public IssuerService(
      IssuerRepositoryPort issuers,
      SefazPort sefaz,
      SecretCipherPort cipher,
      int environment,
      Clock clock) {
    this.issuers = issuers;
    this.sefaz = sefaz;
    this.cipher = cipher;
    this.environment = environment;
    this.clock = clock;
  }

  @Override
  public Optional<Issuer> find(String tenantId) {
    return issuers.findByTenantId(tenantId);
  }

  @Override
  public Issuer save(String tenantId, IssuerCommand command) {
    Issuer issuer = issuers.findByTenantId(tenantId).orElseGet(Issuer::new);
    String cnpj = upperAlnum(command.cnpj());
    if (cnpj == null || !CNPJ.matcher(cnpj).matches() || !validCnpj(cnpj)) {
      throw new BusinessException("CNPJ inválido.");
    }
    if (issuer.getCertificate() != null
        && issuer.getCnpj() != null
        && !issuer.getCnpj().equals(cnpj)) {
      throw new BusinessException(
          "O CNPJ não pode ser trocado com um certificado enviado. Envie o certificado do novo"
              + " CNPJ depois de salvar.");
    }
    if (isBlank(command.razaoSocial())) {
      throw new BusinessException("Informe a razão social.");
    }
    if (command.regime() == null) {
      throw new BusinessException("Informe o regime tributário (Simples Nacional ou MEI).");
    }
    String ie = digits(command.inscricaoEstadual());
    if (ie == null || ie.length() < 2 || ie.length() > 14) {
      throw new BusinessException("Informe a inscrição estadual (obrigatória para NFC-e).");
    }
    Address address = validAddress(command.address());
    int serie = command.serie() == null ? 1 : command.serie();
    if (serie < 1 || serie > 889) {
      throw new BusinessException("A série da NFC-e deve estar entre 1 e 889.");
    }
    String defaultNcm = optionalDigits(command.defaultNcm(), 8, "O NCM padrão deve ter 8 dígitos.");
    String defaultCfop =
        optionalDigits(command.defaultCfop(), 4, "O CFOP padrão deve ter 4 dígitos.");
    String defaultCsosn =
        optionalDigits(command.defaultCsosn(), 3, "O CSOSN padrão deve ter 3 dígitos.");

    if (issuer.getSerie() != 0 && issuer.getSerie() != serie && issuer.getCertificate() != null) {
      log.info(
          "Série da NFC-e alterada: tenantId={} de={} para={}", tenantId, issuer.getSerie(), serie);
    }
    issuer.setTenantId(tenantId);
    issuer.setCnpj(cnpj);
    issuer.setRazaoSocial(command.razaoSocial().trim());
    issuer.setNomeFantasia(trimToNull(command.nomeFantasia()));
    issuer.setInscricaoEstadual(ie);
    issuer.setRegime(command.regime());
    issuer.setAddress(address);
    issuer.setTelefone(digits(command.telefone()));
    issuer.setEmail(trimToNull(command.email()));
    issuer.setSerie(serie);
    issuer.setEnvironment(environment);
    issuer.setDefaultNcm(defaultNcm);
    issuer.setDefaultCfop(defaultCfop);
    issuer.setDefaultCsosn(defaultCsosn);
    issuer.setAutoIssue(command.autoIssue());
    issuer.setContingencyEnabled(command.contingencyEnabled());
    if (issuer.getCreatedAt() == null) {
      issuer.setCreatedAt(clock.instant());
    }
    issuer.setUpdatedAt(clock.instant());
    return issuers.save(issuer);
  }

  @Override
  public Issuer uploadCertificate(String tenantId, byte[] pfx, String password) {
    Issuer issuer =
        issuers
            .findByTenantId(tenantId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "Salve os dados fiscais da loja antes de enviar o certificado."));
    if (pfx == null || pfx.length == 0) {
      throw new BusinessException("Selecione o arquivo do certificado A1 (.pfx ou .p12).");
    }
    if (pfx.length > MAX_CERTIFICATE_BYTES) {
      throw new BusinessException("O certificado A1 deve ter no máximo 50 KB.");
    }
    if (isBlank(password)) {
      throw new BusinessException("Informe a senha do certificado A1.");
    }
    CertificateInfo info = sefaz.inspectCertificate(pfx, password);
    String document = info.document() == null ? "" : info.document().replaceAll("[^0-9A-Z]", "");
    if (!document.isEmpty() && !document.startsWith(issuer.getCnpj().substring(0, 8))) {
      throw new BusinessException(
          "O certificado pertence a outro CNPJ (" + document + "). Envie o e-CNPJ da loja.");
    }
    LocalDate today = LocalDate.now(clock.withZone(BRASILIA));
    if (info.validUntil() == null || info.validUntil().isBefore(today)) {
      throw new BusinessException("Este certificado A1 está vencido.");
    }
    issuer.setCertificate(
        new StoredCertificate(
            cipher.encrypt(pfx),
            cipher.encrypt(password.getBytes(StandardCharsets.UTF_8)),
            info.holder(),
            info.validUntil()));
    issuer.setUpdatedAt(clock.instant());
    log.info("Certificado A1 atualizado: tenantId={} validoAte={}", tenantId, info.validUntil());
    return issuers.save(issuer);
  }

  @Override
  public SefazOutcome sefazStatus(String tenantId) {
    Issuer issuer =
        issuers
            .findByTenantId(tenantId)
            .filter(i -> i.isReadyToIssue(LocalDate.now(clock.withZone(BRASILIA))))
            .orElseThrow(() -> new BusinessException("Envie um certificado A1 válido primeiro."));
    Credentials credentials =
        new Credentials(
            cipher.decrypt(issuer.getCertificate().encryptedPfx()),
            new String(
                cipher.decrypt(issuer.getCertificate().encryptedPassword()),
                StandardCharsets.UTF_8));
    try {
      return sefaz.serviceStatus(issuer, credentials);
    } catch (SefazCommunicationException e) {
      return SefazOutcome.of(SefazOutcome.Kind.UNAVAILABLE, "000", e.getMessage());
    }
  }

  private Address validAddress(Address address) {
    if (address == null) {
      throw new BusinessException("Informe o endereço da loja.");
    }
    String municipio = digits(address.codigoMunicipio());
    if (municipio == null || municipio.length() != 7) {
      throw new BusinessException("Informe o código IBGE do município (7 dígitos).");
    }
    String uf = address.uf() == null ? null : address.uf().trim().toUpperCase(Locale.ROOT);
    if (uf == null || !UF.matcher(uf).matches()) {
      throw new BusinessException("Informe a UF com 2 letras.");
    }
    if (!municipio.startsWith(ufCode(uf))) {
      throw new BusinessException("O código IBGE do município não pertence à UF informada.");
    }
    String cep = digits(address.cep());
    if (cep == null || cep.length() != 8) {
      throw new BusinessException("Informe o CEP com 8 dígitos.");
    }
    if (isBlank(address.logradouro()) || isBlank(address.bairro()) || isBlank(address.cidade())) {
      throw new BusinessException("Informe logradouro, bairro e cidade.");
    }
    return new Address(
        address.logradouro().trim(),
        isBlank(address.numero()) ? "SN" : address.numero().trim(),
        trimToNull(address.complemento()),
        address.bairro().trim(),
        municipio,
        address.cidade().trim(),
        uf,
        cep);
  }

  static String ufCode(String uf) {
    return switch (uf) {
      case "RO" -> "11";
      case "AC" -> "12";
      case "AM" -> "13";
      case "RR" -> "14";
      case "PA" -> "15";
      case "AP" -> "16";
      case "TO" -> "17";
      case "MA" -> "21";
      case "PI" -> "22";
      case "CE" -> "23";
      case "RN" -> "24";
      case "PB" -> "25";
      case "PE" -> "26";
      case "AL" -> "27";
      case "SE" -> "28";
      case "BA" -> "29";
      case "MG" -> "31";
      case "ES" -> "32";
      case "RJ" -> "33";
      case "SP" -> "35";
      case "PR" -> "41";
      case "SC" -> "42";
      case "RS" -> "43";
      case "MS" -> "50";
      case "MT" -> "51";
      case "GO" -> "52";
      case "DF" -> "53";
      default -> throw new BusinessException("UF inválida.");
    };
  }

  static boolean validCnpj(String cnpj) {
    if (cnpj.chars().distinct().count() == 1) {
      return false;
    }
    int[] first = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    int[] second = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    return checkDigit(cnpj, first) == cnpj.charAt(12) - '0'
        && checkDigit(cnpj, second) == cnpj.charAt(13) - '0';
  }

  private static int checkDigit(String cnpj, int[] weights) {
    int sum = 0;
    for (int i = 0; i < weights.length; i++) {
      sum += (cnpj.charAt(i) - '0') * weights[i];
    }
    int remainder = sum % 11;
    return remainder < 2 ? 0 : 11 - remainder;
  }

  private static String optionalDigits(String value, int length, String error) {
    String digits = digits(value);
    if (digits != null && digits.length() != length) {
      throw new BusinessException(error);
    }
    return digits;
  }

  private static String upperAlnum(String value) {
    if (value == null) {
      return null;
    }
    String cleaned = value.toUpperCase(Locale.ROOT).replaceAll("[^0-9A-Z]", "");
    return cleaned.isEmpty() ? null : cleaned;
  }

  private static String digits(String value) {
    if (value == null) {
      return null;
    }
    String cleaned = value.replaceAll("\\D", "");
    return cleaned.isEmpty() ? null : cleaned;
  }

  private static String trimToNull(String value) {
    return isBlank(value) ? null : value.trim();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
