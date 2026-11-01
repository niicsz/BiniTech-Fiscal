package com.binitech.fiscal.adapters.inbound.web;

import com.binitech.fiscal.domain.exception.BusinessException;
import com.binitech.fiscal.domain.exception.ExternalServiceUnavailableException;
import com.binitech.fiscal.domain.exception.ResourceNotFoundException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<Map<String, String>> business(BusinessException e) {
    return error(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<Map<String, String>> notFound(ResourceNotFoundException e) {
    return error(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Map<String, String>> forbidden(AccessDeniedException e) {
    return error(HttpStatus.FORBIDDEN, e.getMessage());
  }

  @ExceptionHandler(ExternalServiceUnavailableException.class)
  ResponseEntity<Map<String, String>> unavailable(ExternalServiceUnavailableException e) {
    return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<Map<String, String>> conflict(OptimisticLockingFailureException e) {
    return error(HttpStatus.CONFLICT, "A NFC-e está sendo processada. Tente de novo em instantes.");
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
  ResponseEntity<Map<String, String>> badRequest(Exception e) {
    return error(HttpStatus.BAD_REQUEST, "Requisição inválida.");
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException e) {
    return error(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo muito grande.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, String>> unexpected(Exception e) {
    log.error("Erro inesperado: {}", e.getMessage(), e);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado.");
  }

  private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(Map.of("message", message == null ? "" : message));
  }
}
