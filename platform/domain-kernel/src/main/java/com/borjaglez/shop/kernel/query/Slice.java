package com.borjaglez.shop.kernel.query;

import java.util.List;
import java.util.function.Function;

/** A page of results that only knows whether another page follows. */
public record Slice<T>(List<T> content, int page, int size, boolean hasNext) {

  public Slice {
    if (page < 0) {
      throw new IllegalArgumentException("page must not be negative: " + page);
    }
    if (size < 1) {
      throw new IllegalArgumentException("size must be at least 1: " + size);
    }
    content = List.copyOf(content);
  }

  public <R> Slice<R> map(Function<? super T, ? extends R> mapper) {
    return new Slice<>(content.stream().<R>map(mapper).toList(), page, size, hasNext);
  }
}
