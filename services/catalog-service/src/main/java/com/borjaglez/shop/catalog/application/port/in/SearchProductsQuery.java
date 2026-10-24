package com.borjaglez.shop.catalog.application.port.in;

import java.util.Objects;

import com.borjaglez.cqrs.query.Query;
import com.borjaglez.shop.kernel.query.ClientFilter;
import com.borjaglez.shop.kernel.query.PageRequest;

/**
 * [Hexagonal · puerto de entrada] A shopper searches the products on sale. Answered with a {@code
 * Page<ProductCard>}.
 *
 * <p>The filters and sorts are the text the shopper sent: the read side checks them against the
 * public whitelist before it runs anything.
 */
public final class SearchProductsQuery extends Query {

  private final ClientFilter filter;
  private final PageRequest page;

  public SearchProductsQuery(ClientFilter filter, PageRequest page) {
    this.filter = Objects.requireNonNull(filter, "filter must not be null");
    this.page = Objects.requireNonNull(page, "page must not be null");
  }

  public ClientFilter getFilter() {
    return filter;
  }

  public PageRequest getPage() {
    return page;
  }
}
