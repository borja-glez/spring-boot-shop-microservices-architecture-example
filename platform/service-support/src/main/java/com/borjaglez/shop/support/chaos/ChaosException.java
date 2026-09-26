package com.borjaglez.shop.support.chaos;

/** A technical failure injected on purpose. It is not mapped to a 4xx: it must look real. */
public class ChaosException extends RuntimeException {

  public ChaosException(String message) {
    super(message);
  }
}
