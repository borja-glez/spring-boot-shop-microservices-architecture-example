package com.borjaglez.shop.catalog.infrastructure.in.rest.v1;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.borjaglez.shop.catalog.application.port.in.CategoryView;
import com.borjaglez.shop.catalog.application.port.in.ChangeProductPriceCommand;
import com.borjaglez.shop.catalog.application.port.in.CreateProductCommand;
import com.borjaglez.shop.catalog.application.port.in.DiscontinueProductCommand;
import com.borjaglez.shop.catalog.application.port.in.GetCatalogFacetsQuery;
import com.borjaglez.shop.catalog.application.port.in.SearchProductsQuery;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.Availability;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.CatalogFacets;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.Category;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.CategoryRef;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.ChangePriceRequest;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.CreateProductRequest;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.DiscontinueRequest;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.FacetValue;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.PriceRange;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.ProductCard;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.ProductDetail;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.ProductPage;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.ProductStatus;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.SellerSummary;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.StockStatus;
import com.borjaglez.shop.kernel.query.ClientFilter;
import com.borjaglez.shop.kernel.query.Page;
import com.borjaglez.shop.kernel.query.PageRequest;

/**
 * Translates between the models generated from {@code catalog-api.v1.yaml} and the commands,
 * queries and views of the catalog's core. The views share their names with the models of the
 * contract, so they are written here with their package.
 */
final class CatalogRestMapper {

  private CatalogRestMapper() {}

  static SearchProductsQuery searchQuery(ClientFilter filter, Pageable pageable) {
    return new SearchProductsQuery(
        filter, new PageRequest(pageable.getPageNumber(), pageable.getPageSize()));
  }

  static GetCatalogFacetsQuery facetsQuery(ClientFilter filter) {
    return new GetCatalogFacetsQuery(filter);
  }

  static CreateProductCommand createCommand(String sellerId, CreateProductRequest body) {
    return new CreateProductCommand(
        sellerId,
        body.getSku(),
        body.getName(),
        body.getDescription(),
        body.getPrice(),
        body.getCurrency(),
        body.getCategories(),
        body.getTags());
  }

  static ChangeProductPriceCommand changePriceCommand(
      UUID productId, String sellerId, ChangePriceRequest body) {
    return new ChangeProductPriceCommand(productId, sellerId, body.getPrice(), body.getCurrency());
  }

  static DiscontinueProductCommand discontinueCommand(
      UUID productId, String sellerId, DiscontinueRequest body) {
    return new DiscontinueProductCommand(productId, sellerId, body.getReason());
  }

  static ProductPage page(Page<com.borjaglez.shop.catalog.application.port.in.ProductCard> page) {
    return new ProductPage()
        .content(page.content().stream().map(CatalogRestMapper::card).toList())
        .page(page.page())
        .size(page.size())
        .totalElements(page.totalElements())
        .totalPages(page.totalPages());
  }

  static ProductDetail detail(com.borjaglez.shop.catalog.application.port.in.ProductDetail view) {
    return new ProductDetail()
        .id(view.id())
        .slug(view.slug())
        .sku(view.sku())
        .name(view.name())
        .description(view.description())
        .price(view.price())
        .currency(view.currency())
        .status(ProductStatus.valueOf(view.status().name()))
        .seller(seller(view.seller()))
        .categories(
            view.categories().stream()
                .map(category -> new CategoryRef().slug(category.slug()).name(category.name()))
                .toList())
        .tags(view.tags())
        .publishedAt(view.publishedAt())
        .updatedAt(view.updatedAt())
        .version(view.version())
        .availability(availability(view.availability()));
  }

  static CatalogFacets facets(com.borjaglez.shop.catalog.application.port.in.CatalogFacets view) {
    return new CatalogFacets()
        .categories(facetValues(view.categories()))
        .sellers(facetValues(view.sellers()))
        .tags(facetValues(view.tags()))
        .price(new PriceRange().min(view.price().min()).max(view.price().max()));
  }

  static List<Category> categories(List<CategoryView> views) {
    return views.stream()
        .map(
            view ->
                new Category()
                    .slug(view.slug())
                    .name(view.name())
                    .activeProducts(view.activeProducts()))
        .toList();
  }

  private static ProductCard card(com.borjaglez.shop.catalog.application.port.in.ProductCard view) {
    return new ProductCard()
        .id(view.id())
        .slug(view.slug())
        .sku(view.sku())
        .name(view.name())
        .price(view.price())
        .currency(view.currency())
        .status(ProductStatus.valueOf(view.status().name()))
        .seller(seller(view.seller()))
        .categories(view.categories())
        .tags(view.tags())
        .publishedAt(view.publishedAt());
  }

  private static SellerSummary seller(
      com.borjaglez.shop.catalog.application.port.in.SellerSummary view) {
    return new SellerSummary().id(view.id()).displayName(view.displayName()).city(view.city());
  }

  /** The stock of a product on sale; {@code null} for a product that is no longer on sale. */
  private static Availability availability(
      com.borjaglez.shop.catalog.application.port.in.Availability view) {
    return view == null
        ? null
        : new Availability().status(StockStatus.valueOf(view.status().name())).units(view.units());
  }

  private static List<FacetValue> facetValues(
      List<com.borjaglez.shop.catalog.application.port.in.FacetValue> views) {
    return views.stream()
        .map(view -> new FacetValue().value(view.value()).label(view.label()).count(view.count()))
        .toList();
  }
}
