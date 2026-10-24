package com.borjaglez.shop.kernel.error;

/** The requested resource does not exist. */
public class NotFoundException extends DomainException {

  public NotFoundException(String code, String message) {
    super(code, message);
  }
}
