package com.borjaglez.shop.support.web;

import java.util.List;

import org.springframework.data.domain.Slice;

/**
 * JSON shape of a page of results without a total: a {@link Slice} reads one row more than the page
 * to know whether there is a next one, and skips the {@code COUNT(*)} a {@link PageResponse} needs.
 * Lists over tables that only grow, such as the event store, use it.
 */
public record SliceResponse<T>(List<T> content, int page, int size, boolean hasNext) {

  public static <T> SliceResponse<T> of(Slice<T> slice) {
    return new SliceResponse<>(
        slice.getContent(), slice.getNumber(), slice.getSize(), slice.hasNext());
  }
}
