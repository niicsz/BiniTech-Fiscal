package com.binitech.fiscal.domain.exception;

public class TenantRequiredException extends RuntimeException {
  public TenantRequiredException() {
    super("Esta operação exige um usuário vinculado a uma loja.");
  }
}
