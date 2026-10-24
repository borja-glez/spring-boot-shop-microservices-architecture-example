package com.borjaglez.shop.catalog.application.fakes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.borjaglez.shop.catalog.application.port.in.CatalogFacets;
import com.borjaglez.shop.catalog.application.port.in.CategoryView;
import com.borjaglez.shop.catalog.application.port.in.PriceRange;
import com.borjaglez.shop.catalog.application.port.in.ProductCard;
import com.borjaglez.shop.catalog.application.port.in.ProductDetail;
import com.borjaglez.shop.catalog.application.port.out.CatalogReadModel;
import com.borjaglez.shop.kernel.query.ClientFilter;
import com.borjaglez.shop.kernel.query.Page;
import com.borjaglez.shop.kernel.query.PageRequest;

/**
 * [Hexagonal · adaptador driven] A read side that answers what each test tells it and remembers
 * what it was asked, for the unit tests of the queries. It knows no product, finds nothing and has
 * no categories until told otherwise.
 */
public final class StubCatalogReadModel implements CatalogReadModel {

  private final Map<String, ProductDetail> products = new LinkedHashMap<>();
  private final List<ClientFilter> filters = new ArrayList<>();
  private final List<PageRequest> pages = new ArrayList<>();
  private List<ProductCard> found = List.of();
  private long total;
  private CatalogFacets facets =
      new CatalogFacets(List.of(), List.of(), List.of(), new PriceRange(null, null));
  private List<CategoryView> categories = List.of();

  @Override
  public Page<ProductCard> search(ClientFilter filter, PageRequest page) {
    filters.add(filter);
    pages.add(page);
    return new Page<>(found, page.page(), page.size(), total);
  }

  @Override
  public CatalogFacets facets(ClientFilter filter) {
    filters.add(filter);
    return facets;
  }

  @Override
  public Optional<ProductDetail> publicProduct(String slug) {
    return Optional.ofNullable(products.get(slug));
  }

  @Override
  public List<CategoryView> categories() {
    return categories;
  }

  /** The page of a product, found by its slug. */
  public void given(ProductDetail product) {
    products.put(product.slug(), product);
  }

  /** What every search finds: these cards, out of {@code total} matching products. */
  public void finds(List<ProductCard> cards, long total) {
    this.found = List.copyOf(cards);
    this.total = total;
  }

  /** What every facets query answers. */
  public void counts(CatalogFacets facets) {
    this.facets = facets;
  }

  /** The categories of the catalog. */
  public void categories(List<CategoryView> categories) {
    this.categories = List.copyOf(categories);
  }

  /** The filters of every search and facets query, in order. */
  public List<ClientFilter> filters() {
    return List.copyOf(filters);
  }

  /** The pages every search asked for, in order. */
  public List<PageRequest> pages() {
    return List.copyOf(pages);
  }
}
