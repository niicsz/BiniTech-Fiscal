package com.binitech.fiscal.domain;

public enum TaxRegime {
  SIMPLES_NACIONAL("1"),
  MEI("4");

  private final String crt;

  TaxRegime(String crt) {
    this.crt = crt;
  }

  public String crt() {
    return crt;
  }
}
