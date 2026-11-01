package com.binitech.fiscal.domain;

public class ReturnDocument extends SefazDocument {

  private String returnId;
  private ReturnStatus status;
  private ReturnSnapshot snapshot;
  private String referencedChave;
  private Integer referencedNumero;

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
}
