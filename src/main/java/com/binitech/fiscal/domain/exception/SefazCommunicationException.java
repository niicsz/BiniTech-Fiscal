package com.binitech.fiscal.domain.exception;

public class SefazCommunicationException extends RuntimeException {

  private final boolean possiblyDelivered;

  public SefazCommunicationException(String message, boolean possiblyDelivered, Throwable cause) {
    super(message, cause);
    this.possiblyDelivered = possiblyDelivered;
  }

  public boolean isPossiblyDelivered() {
    return possiblyDelivered;
  }
}
