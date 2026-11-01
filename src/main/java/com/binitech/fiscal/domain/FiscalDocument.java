package com.binitech.fiscal.domain;

import java.math.BigDecimal;
import java.time.Instant;

public class FiscalDocument {

  private String id;
  private String tenantId;
  private String saleId;
  private FiscalStatus status;
  private SaleSnapshot sale;
  private String consumerCpf;
  private int environment;
  private Integer serie;
  private Integer numero;
  private String cnf;
  private String tpEmis;
  private Instant issuedAt;
  private String chave;
  private String signedXml;
  private String authorizedXml;
  private String qrCode;
  private String urlChave;
  private String protocolo;
  private String cStat;
  private String xMotivo;
  private BigDecimal total;
  private int attempts;
  private Instant nextAttemptAt;
  private Instant lockedUntil;
  private boolean deliveryUncertain;
  private Instant unavailableSince;
  private String lastError;
  private String cancelReason;
  private String cancelProtocol;
  private String cancelXml;
  private Instant authorizedAt;
  private Instant cancelledAt;
  private Instant createdAt;
  private Instant updatedAt;
  private Long version;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getSaleId() {
    return saleId;
  }

  public void setSaleId(String saleId) {
    this.saleId = saleId;
  }

  public FiscalStatus getStatus() {
    return status;
  }

  public void setStatus(FiscalStatus status) {
    this.status = status;
  }

  public SaleSnapshot getSale() {
    return sale;
  }

  public void setSale(SaleSnapshot sale) {
    this.sale = sale;
  }

  public String getConsumerCpf() {
    return consumerCpf;
  }

  public void setConsumerCpf(String consumerCpf) {
    this.consumerCpf = consumerCpf;
  }

  public int getEnvironment() {
    return environment;
  }

  public void setEnvironment(int environment) {
    this.environment = environment;
  }

  public Integer getSerie() {
    return serie;
  }

  public void setSerie(Integer serie) {
    this.serie = serie;
  }

  public Integer getNumero() {
    return numero;
  }

  public void setNumero(Integer numero) {
    this.numero = numero;
  }

  public String getCnf() {
    return cnf;
  }

  public void setCnf(String cnf) {
    this.cnf = cnf;
  }

  public String getTpEmis() {
    return tpEmis;
  }

  public void setTpEmis(String tpEmis) {
    this.tpEmis = tpEmis;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public void setIssuedAt(Instant issuedAt) {
    this.issuedAt = issuedAt;
  }

  public String getChave() {
    return chave;
  }

  public void setChave(String chave) {
    this.chave = chave;
  }

  public String getSignedXml() {
    return signedXml;
  }

  public void setSignedXml(String signedXml) {
    this.signedXml = signedXml;
  }

  public String getAuthorizedXml() {
    return authorizedXml;
  }

  public void setAuthorizedXml(String authorizedXml) {
    this.authorizedXml = authorizedXml;
  }

  public String getQrCode() {
    return qrCode;
  }

  public void setQrCode(String qrCode) {
    this.qrCode = qrCode;
  }

  public String getUrlChave() {
    return urlChave;
  }

  public void setUrlChave(String urlChave) {
    this.urlChave = urlChave;
  }

  public String getProtocolo() {
    return protocolo;
  }

  public void setProtocolo(String protocolo) {
    this.protocolo = protocolo;
  }

  public String getCStat() {
    return cStat;
  }

  public void setCStat(String cStat) {
    this.cStat = cStat;
  }

  public String getXMotivo() {
    return xMotivo;
  }

  public void setXMotivo(String xMotivo) {
    this.xMotivo = xMotivo;
  }

  public BigDecimal getTotal() {
    return total;
  }

  public void setTotal(BigDecimal total) {
    this.total = total;
  }

  public int getAttempts() {
    return attempts;
  }

  public void setAttempts(int attempts) {
    this.attempts = attempts;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public void setNextAttemptAt(Instant nextAttemptAt) {
    this.nextAttemptAt = nextAttemptAt;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public void setLockedUntil(Instant lockedUntil) {
    this.lockedUntil = lockedUntil;
  }

  public boolean isDeliveryUncertain() {
    return deliveryUncertain;
  }

  public void setDeliveryUncertain(boolean deliveryUncertain) {
    this.deliveryUncertain = deliveryUncertain;
  }

  public Instant getUnavailableSince() {
    return unavailableSince;
  }

  public void setUnavailableSince(Instant unavailableSince) {
    this.unavailableSince = unavailableSince;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  public String getCancelReason() {
    return cancelReason;
  }

  public void setCancelReason(String cancelReason) {
    this.cancelReason = cancelReason;
  }

  public String getCancelProtocol() {
    return cancelProtocol;
  }

  public void setCancelProtocol(String cancelProtocol) {
    this.cancelProtocol = cancelProtocol;
  }

  public String getCancelXml() {
    return cancelXml;
  }

  public void setCancelXml(String cancelXml) {
    this.cancelXml = cancelXml;
  }

  public Instant getAuthorizedAt() {
    return authorizedAt;
  }

  public void setAuthorizedAt(Instant authorizedAt) {
    this.authorizedAt = authorizedAt;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public void setCancelledAt(Instant cancelledAt) {
    this.cancelledAt = cancelledAt;
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

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }
}
