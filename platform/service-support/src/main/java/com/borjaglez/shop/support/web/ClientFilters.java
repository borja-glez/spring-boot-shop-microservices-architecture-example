package com.borjaglez.shop.support.web;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;

import com.borjaglez.shop.kernel.query.ClientFilter;
import com.borjaglez.specrepository.http.HttpFilterParserConfiguration;

/**
 * Reads the filters, alternative filters and sorts of a request as the client sent them, for a read
 * side that parses them with the HTTP filter syntax of specification-repository.
 *
 * <p>Each occurrence of a parameter is one value, taken whole. Binding a parameter to a {@code
 * List<String>}, as the interfaces generated from the contracts do, splits a single value at its
 * commas: {@code sort=price.amount,desc} would become two sorts, and a text filter with a comma two
 * filters. A REST adapter reads them here instead, with the filter syntax of the service (the
 * {@code specrepository.http.*} properties its read side parses with):
 *
 * <pre>{@code
 * ClientFilters clientFilters = new ClientFilters(httpFilterProperties.toParserConfiguration());
 * ...
 * Page<ProductCard> page = queries.ask(new SearchProductsQuery(clientFilters.from(request), ...));
 * }</pre>
 *
 * <p>The parameter names are the parser's ({@code filter-param}, {@code or-filter-param} and {@code
 * sort-param}), so the adapter reads the parameters the read side parses.
 */
public final class ClientFilters {

  private final HttpFilterParserConfiguration syntax;

  public ClientFilters(HttpFilterParserConfiguration syntax) {
    this.syntax = Objects.requireNonNull(syntax, "syntax must not be null");
  }

  /** The filters, alternative filters and sorts of {@code request}; empty lists when absent. */
  public ClientFilter from(HttpServletRequest request) {
    return new ClientFilter(
        values(request, syntax.filterParam()),
        values(request, syntax.orFilterParam()),
        values(request, syntax.sortParam()));
  }

  private static @Nullable List<String> values(HttpServletRequest request, String name) {
    String[] values = request.getParameterValues(name);
    return values == null ? null : Arrays.asList(values);
  }
}
