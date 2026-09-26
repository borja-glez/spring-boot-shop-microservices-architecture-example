package com.borjaglez.shop.support.web;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * JSON shape of a page of results. Serializing Spring Data's {@code PageImpl} directly is not a
 * stable contract, so the API exposes this record instead.
 */
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages) {

  public static <T> PageResponse<T> of(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
