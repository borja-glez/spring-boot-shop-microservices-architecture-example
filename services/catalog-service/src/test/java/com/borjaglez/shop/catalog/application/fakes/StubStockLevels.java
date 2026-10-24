package com.borjaglez.shop.catalog.application.fakes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.borjaglez.shop.catalog.application.port.out.StockLevels;
import com.borjaglez.shop.catalog.domain.ProductId;

/**
 * [Hexagonal · adaptador driven] An inventory that answers as each test tells it and remembers what
 * it was asked, for the unit tests of the queries. Products it was not told about are unknown to
 * it, so it leaves them out of its answer.
 */
public final class StubStockLevels implements StockLevels {

  private final Map<ProductId, Integer> units = new LinkedHashMap<>();
  private final List<List<ProductId>> asked = new ArrayList<>();
  private boolean answering = true;

  @Override
  public Optional<Map<ProductId, Integer>> available(List<ProductId> products) {
    asked.add(List.copyOf(products));
    if (!answering) {
      return Optional.empty();
    }
    return Optional.of(
        products.stream()
            .filter(units::containsKey)
            .collect(Collectors.toMap(Function.identity(), units::get)));
  }

  /** The free units of a product. */
  public void stock(ProductId product, int available) {
    units.put(product, available);
  }

  /** The inventory stops answering, as when it is slow, failing or unreachable. */
  public void silent() {
    answering = false;
  }

  /** The products of every question, in order. */
  public List<List<ProductId>> asked() {
    return List.copyOf(asked);
  }
}
