package com.borjaglez.shop.support.error;

/** The request is well formed but breaks a business rule (an invariant of an aggregate). */
public class BusinessRuleViolationException extends DomainException {

  public BusinessRuleViolationException(String code, String message) {
    super(code, message);
  }
}
