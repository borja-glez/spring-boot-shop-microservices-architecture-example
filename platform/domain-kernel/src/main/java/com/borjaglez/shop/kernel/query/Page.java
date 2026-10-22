package com.borjaglez.shop.kernel.query;

import java.util.List;
import java.util.function.Function;

/** A page of results with the total number of elements. */
public record Page<T>(List<T> content, int page, int size, long totalElements) {

  public int totalPages() {
    return (int) ((totalElements + size - 1) / size);
  }

  public <R> Page<R> map(Function<? super T, ? extends R> mapper) {
    return new Page<>(content.stream().<R>map(mapper).toList(), page, size, totalElements);
  }
}
