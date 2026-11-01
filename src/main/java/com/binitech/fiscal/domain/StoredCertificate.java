package com.binitech.fiscal.domain;

import java.time.LocalDate;

public record StoredCertificate(
    String encryptedPfx, String encryptedPassword, String holder, LocalDate validUntil) {

  public boolean isValidOn(LocalDate date) {
    return validUntil != null && !validUntil.isBefore(date);
  }
}
