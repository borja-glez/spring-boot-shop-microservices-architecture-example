package com.borjaglez.shop.kernel.query;

import java.util.List;
import java.util.function.Function;

/** A page of results that only knows whether another page follows. */
public record Slice<T>(List<T> content, int page, int size, boolean hasNext) {

  public <R> Slice<R> map(Function<? super T, ? extends R> mapper) {
    return new Slice<>(content.stream().<R>map(mapper).toList(), page, size, hasNext);
  }
}
