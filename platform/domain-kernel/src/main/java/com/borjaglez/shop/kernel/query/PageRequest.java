package com.borjaglez.shop.kernel.query;

/** Zero-based page and size of a paged query. */
public record PageRequest(int page, int size) {

  public PageRequest {
    if (page < 0) {
      throw new IllegalArgumentException("page must not be negative: " + page);
    }
    if (size < 1) {
      throw new IllegalArgumentException("size must be at least 1: " + size);
    }
  }
}
