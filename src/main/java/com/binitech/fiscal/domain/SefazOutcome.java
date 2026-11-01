package com.binitech.fiscal.domain;

public record SefazOutcome(
    Kind kind, String cStat, String xMotivo, String protocolo, String receivedAt, String xmlProc) {

  public enum Kind {
    AUTHORIZED,
    DENIED,
    REJECTED,
    DUPLICATE,
    UNAVAILABLE,
    NOT_FOUND
  }

  public static SefazOutcome of(Kind kind, String cStat, String xMotivo) {
    return new SefazOutcome(kind, cStat, xMotivo, null, null, null);
  }
}
