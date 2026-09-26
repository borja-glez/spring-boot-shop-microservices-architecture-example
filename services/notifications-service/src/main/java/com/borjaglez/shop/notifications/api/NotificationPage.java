package com.borjaglez.shop.notifications.api;

import java.util.List;

import org.springframework.data.domain.Page;

/** Same JSON shape as the other services' pages (service-support is Boot 4 only). */
record NotificationPage<T>(
    List<T> content, int page, int size, long totalElements, int totalPages) {

  static <T> NotificationPage<T> of(Page<T> page) {
    return new NotificationPage<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
