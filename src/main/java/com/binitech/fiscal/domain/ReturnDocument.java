package com.binitech.fiscal.domain;

import java.math.BigDecimal;
import java.time.Instant;

public class ReturnDocument {

  private String id;
  private String tenantId;
  private String saleId;
  private String returnId;
  private ReturnStatus status;
  private ReturnSnapshot snapshot;
  private String consumerCpf;
  private int environment;
  private Integer serie;
  private Integer numero;
  private String cnf;
  private Instant issuedAt;
  private String chave;
  private String signedXml;
  private String authorizedXml;
  private String protocolo;
  private String cStat;
  private String xMotivo;
  private String referencedChave;
  private Integer referencedNumero;
  private BigDecimal total;
  private int attempts;
  private Instant nextAttemptAt;
  private Instant lockedUntil;
  private boolean deliveryUncertain;
  private String lastError;
  private Instant authorizedAt;
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

  public String getReturnId() {
    return returnId;
  }

  public void setReturnId(String returnId) {
    this.returnId = returnId;
  }

  public ReturnStatus getStatus() {
    return status;
  }

  public void setStatus(ReturnStatus status) {
    this.status = status;
  }

  public ReturnSnapshot getSnapshot() {
    return snapshot;
  }

  public void setSnapshot(ReturnSnapshot snapshot) {
    this.snapshot = snapshot;
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

  public String getReferencedChave() {
    return referencedChave;
  }

  public void setReferencedChave(String referencedChave) {
    this.referencedChave = referencedChave;
  }

  public Integer getReferencedNumero() {
    return referencedNumero;
  }

  public void setReferencedNumero(Integer referencedNumero) {
    this.referencedNumero = referencedNumero;
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

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  public Instant getAuthorizedAt() {
    return authorizedAt;
  }

  public void setAuthorizedAt(Instant authorizedAt) {
    this.authorizedAt = authorizedAt;
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
