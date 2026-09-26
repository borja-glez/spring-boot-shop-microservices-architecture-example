package com.borjaglez.shop.support.error;

/** The request clashes with the current state, for example a duplicated unique key. */
public class ConflictException extends DomainException {

  public ConflictException(String code, String message) {
    super(code, message);
  }
}
