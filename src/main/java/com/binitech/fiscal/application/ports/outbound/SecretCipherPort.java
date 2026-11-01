package com.binitech.fiscal.application.ports.outbound;

public interface SecretCipherPort {
  String encrypt(byte[] plain);

  byte[] decrypt(String cipherText);
}
