package com.binitech.fiscal.domain;

import java.time.Instant;
import java.time.LocalDate;

public class Issuer {

  private String tenantId;
  private String cnpj;
  private String razaoSocial;
  private String nomeFantasia;
  private String inscricaoEstadual;
  private TaxRegime regime;
  private Address address;
  private String telefone;
  private String email;
  private int serie;
  private int nfeSerie;
  private int environment;
  private String defaultNcm;
  private String defaultCfop;
  private String defaultCsosn;
  private boolean autoIssue;
  private boolean contingencyEnabled;
  private StoredCertificate certificate;
  private Instant createdAt;
  private Instant updatedAt;

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getCnpj() {
    return cnpj;
  }

  public void setCnpj(String cnpj) {
    this.cnpj = cnpj;
  }

  public String getRazaoSocial() {
    return razaoSocial;
  }

  public void setRazaoSocial(String razaoSocial) {
    this.razaoSocial = razaoSocial;
  }

  public String getNomeFantasia() {
    return nomeFantasia;
  }

  public void setNomeFantasia(String nomeFantasia) {
    this.nomeFantasia = nomeFantasia;
  }

  public String getInscricaoEstadual() {
    return inscricaoEstadual;
  }

  public void setInscricaoEstadual(String inscricaoEstadual) {
    this.inscricaoEstadual = inscricaoEstadual;
  }

  public TaxRegime getRegime() {
    return regime;
  }

  public void setRegime(TaxRegime regime) {
    this.regime = regime;
  }

  public Address getAddress() {
    return address;
  }

  public void setAddress(Address address) {
    this.address = address;
  }

  public String getTelefone() {
    return telefone;
  }

  public void setTelefone(String telefone) {
    this.telefone = telefone;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public int getSerie() {
    return serie;
  }

  public void setSerie(int serie) {
    this.serie = serie;
  }

  public int getNfeSerie() {
    return nfeSerie <= 0 ? 1 : nfeSerie;
  }

  public void setNfeSerie(int nfeSerie) {
    this.nfeSerie = nfeSerie;
  }

  public int getEnvironment() {
    return environment;
  }

  public void setEnvironment(int environment) {
    this.environment = environment;
  }

  public String getDefaultNcm() {
    return defaultNcm;
  }

  public void setDefaultNcm(String defaultNcm) {
    this.defaultNcm = defaultNcm;
  }

  public String getDefaultCfop() {
    return defaultCfop;
  }

  public void setDefaultCfop(String defaultCfop) {
    this.defaultCfop = defaultCfop;
  }

  public String getDefaultCsosn() {
    return defaultCsosn;
  }

  public void setDefaultCsosn(String defaultCsosn) {
    this.defaultCsosn = defaultCsosn;
  }

  public boolean isAutoIssue() {
    return autoIssue;
  }

  public void setAutoIssue(boolean autoIssue) {
    this.autoIssue = autoIssue;
  }

  public boolean isContingencyEnabled() {
    return contingencyEnabled;
  }

  public void setContingencyEnabled(boolean contingencyEnabled) {
    this.contingencyEnabled = contingencyEnabled;
  }

  public StoredCertificate getCertificate() {
    return certificate;
  }

  public void setCertificate(StoredCertificate certificate) {
    this.certificate = certificate;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public boolean isReadyToIssue(LocalDate today) {
    return cnpj != null
        && inscricaoEstadual != null
        && address != null
        && regime != null
        && certificate != null
        && certificate.isValidOn(today);
  }
}
