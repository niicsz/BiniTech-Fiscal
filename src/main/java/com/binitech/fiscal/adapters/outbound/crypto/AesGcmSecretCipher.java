package com.binitech.fiscal.adapters.outbound.crypto;

import com.binitech.fiscal.application.ports.outbound.SecretCipherPort;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AesGcmSecretCipher implements SecretCipherPort {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public AesGcmSecretCipher(String base64Key) {
    if (base64Key == null || base64Key.isBlank()) {
      throw new IllegalArgumentException("FISCAL_MASTER_KEY é obrigatória.");
    }
    byte[] raw = Base64.getDecoder().decode(base64Key.trim());
    if (raw.length != 32) {
      throw new IllegalArgumentException("FISCAL_MASTER_KEY deve ter 32 bytes em Base64.");
    }
    this.key = new SecretKeySpec(raw, "AES");
  }

  @Override
  public String encrypt(byte[] plain) {
    try {
      byte[] iv = new byte[IV_LENGTH];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] encrypted = cipher.doFinal(plain);
      return Base64.getEncoder()
          .encodeToString(
              ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Falha ao criptografar segredo.", e);
    }
  }

  @Override
  public byte[] decrypt(String cipherText) {
    try {
      byte[] data = Base64.getDecoder().decode(cipherText);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_LENGTH));
      return cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Falha ao descriptografar segredo.", e);
    }
  }
}
