package com.borjaglez.shop.kernel.query;

import java.util.List;

/** Filters and sorts as the client sent them, still to be validated by the query side. */
public record ClientFilter(List<String> filters, List<String> orFilters, List<String> sorts) {

  public ClientFilter {
    filters = filters == null ? List.of() : List.copyOf(filters);
    orFilters = orFilters == null ? List.of() : List.copyOf(orFilters);
    sorts = sorts == null ? List.of() : List.copyOf(sorts);
  }

  public static ClientFilter none() {
    return new ClientFilter(null, null, null);
  }
}
