package com.binitech.fiscal.domain;

import java.time.Instant;

public class FiscalDocument extends SefazDocument {

  private FiscalStatus status;
  private SaleSnapshot sale;
  private String tpEmis;
  private String qrCode;
  private String urlChave;
  private Instant unavailableSince;
  private String cancelReason;
  private String cancelProtocol;
  private String cancelXml;
  private Instant cancelledAt;

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

  public String getTpEmis() {
    return tpEmis;
  }

  public void setTpEmis(String tpEmis) {
    this.tpEmis = tpEmis;
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

  public Instant getUnavailableSince() {
    return unavailableSince;
  }

  public void setUnavailableSince(Instant unavailableSince) {
    this.unavailableSince = unavailableSince;
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

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public void setCancelledAt(Instant cancelledAt) {
    this.cancelledAt = cancelledAt;
  }
}
