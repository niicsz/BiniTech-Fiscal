package com.binitech.fiscal.adapters.inbound.web;

import com.binitech.fiscal.adapters.inbound.web.generated.model.ErrorDTO;
import com.binitech.fiscal.domain.exception.BusinessException;
import com.binitech.fiscal.domain.exception.ExternalServiceUnavailableException;
import com.binitech.fiscal.domain.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ErrorDTO> business(BusinessException e) {
    return error(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<ErrorDTO> notFound(ResourceNotFoundException e) {
    return error(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorDTO> forbidden(AccessDeniedException e) {
    return error(HttpStatus.FORBIDDEN, e.getMessage());
  }

  @ExceptionHandler(ExternalServiceUnavailableException.class)
  ResponseEntity<ErrorDTO> unavailable(ExternalServiceUnavailableException e) {
    return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<ErrorDTO> conflict(OptimisticLockingFailureException e) {
    return error(HttpStatus.CONFLICT, "A NFC-e está sendo processada. Tente de novo em instantes.");
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ErrorDTO> unsupportedMediaType(HttpMediaTypeNotSupportedException e) {
    return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Envie o corpo como application/json.");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorDTO> invalid(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> "Campo inválido: " + error.getField())
            .orElse("Requisição inválida.");
    return error(HttpStatus.BAD_REQUEST, message);
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
  ResponseEntity<ErrorDTO> badRequest(Exception e) {
    return error(HttpStatus.BAD_REQUEST, "Requisição inválida.");
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ErrorDTO> tooLarge(MaxUploadSizeExceededException e) {
    return error(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo muito grande.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorDTO> unexpected(Exception e) {
    log.error("Erro inesperado: {}", e.getMessage(), e);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado.");
  }

  private static ResponseEntity<ErrorDTO> error(HttpStatus status, String message) {
    return ResponseEntity.status(status)
        .body(new ErrorDTO().message(message == null ? "" : message));
  }
}
