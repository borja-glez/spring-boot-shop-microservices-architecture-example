package com.borjaglez.shop.kernel.query;

import java.util.List;
import java.util.function.Function;

/** A page of results with the total number of elements. */
public record Page<T>(List<T> content, int page, int size, long totalElements) {

  public Page {
    if (page < 0) {
      throw new IllegalArgumentException("page must not be negative: " + page);
    }
    if (size < 1) {
      throw new IllegalArgumentException("size must be at least 1: " + size);
    }
    if (totalElements < 0) {
      throw new IllegalArgumentException("totalElements must not be negative: " + totalElements);
    }
    content = List.copyOf(content);
  }

  public int totalPages() {
    return Math.toIntExact((totalElements + size - 1) / size);
  }

  public <R> Page<R> map(Function<? super T, ? extends R> mapper) {
    return new Page<>(content.stream().<R>map(mapper).toList(), page, size, totalElements);
  }
}
