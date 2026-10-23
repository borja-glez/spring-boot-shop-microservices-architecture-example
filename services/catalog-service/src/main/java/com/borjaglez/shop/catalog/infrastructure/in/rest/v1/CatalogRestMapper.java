package com.borjaglez.shop.catalog.infrastructure.in.rest.v1;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.borjaglez.shop.catalog.application.port.in.Availability;
import com.borjaglez.shop.catalog.application.port.in.CatalogFacets;
import com.borjaglez.shop.catalog.application.port.in.CategoryView;
import com.borjaglez.shop.catalog.application.port.in.ChangeProductPriceCommand;
import com.borjaglez.shop.catalog.application.port.in.CreateProductCommand;
import com.borjaglez.shop.catalog.application.port.in.DiscontinueProductCommand;
import com.borjaglez.shop.catalog.application.port.in.FacetValue;
import com.borjaglez.shop.catalog.application.port.in.GetCatalogFacetsQuery;
import com.borjaglez.shop.catalog.application.port.in.ProductCard;
import com.borjaglez.shop.catalog.application.port.in.ProductDetail;
import com.borjaglez.shop.catalog.application.port.in.SearchProductsQuery;
import com.borjaglez.shop.catalog.application.port.in.SellerSummary;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.AvailabilityDto;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.CatalogFacetsDto;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.CategoryDto;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.CategoryRefDto;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.ChangePriceRequestDto;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.CreateProductRequestDto;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.DiscontinueRequestDto;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.FacetValueDto;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.PriceRangeDto;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.ProductCardDto;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.ProductDetailDto;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.ProductPageDto;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.ProductStatusDto;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.SellerSummaryDto;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.StockStatusDto;
import com.borjaglez.shop.kernel.query.ClientFilter;
import com.borjaglez.shop.kernel.query.Page;
import com.borjaglez.shop.kernel.query.PageRequest;

/**
 * Translates between the models generated from {@code catalog-api.v1.yaml} (the {@code *Dto}
 * classes) and the commands, queries and views of the catalog's core.
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

  static CreateProductCommand createCommand(String sellerId, CreateProductRequestDto body) {
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
      UUID productId, String sellerId, ChangePriceRequestDto body) {
    return new ChangeProductPriceCommand(productId, sellerId, body.getPrice(), body.getCurrency());
  }

  static DiscontinueProductCommand discontinueCommand(
      UUID productId, String sellerId, DiscontinueRequestDto body) {
    return new DiscontinueProductCommand(productId, sellerId, body.getReason());
  }

  static ProductPageDto page(Page<ProductCard> page) {
    return new ProductPageDto()
        .content(page.content().stream().map(CatalogRestMapper::card).toList())
        .page(page.page())
        .size(page.size())
        .totalElements(page.totalElements())
        .totalPages(page.totalPages());
  }

  static ProductDetailDto detail(ProductDetail view) {
    return new ProductDetailDto()
        .id(view.id())
        .slug(view.slug())
        .sku(view.sku())
        .name(view.name())
        .description(view.description())
        .price(view.price())
        .currency(view.currency())
        .status(ProductStatusDto.valueOf(view.status().name()))
        .seller(seller(view.seller()))
        .categories(
            view.categories().stream()
                .map(category -> new CategoryRefDto().slug(category.slug()).name(category.name()))
                .toList())
        .tags(view.tags())
        .publishedAt(view.publishedAt())
        .updatedAt(view.updatedAt())
        .version(view.version())
        .availability(availability(view.availability()));
  }

  static CatalogFacetsDto facets(CatalogFacets view) {
    return new CatalogFacetsDto()
        .categories(facetValues(view.categories()))
        .sellers(facetValues(view.sellers()))
        .tags(facetValues(view.tags()))
        .price(new PriceRangeDto().min(view.price().min()).max(view.price().max()));
  }

  static List<CategoryDto> categories(List<CategoryView> views) {
    return views.stream()
        .map(
            view ->
                new CategoryDto()
                    .slug(view.slug())
                    .name(view.name())
                    .activeProducts(view.activeProducts()))
        .toList();
  }

  private static ProductCardDto card(ProductCard view) {
    return new ProductCardDto()
        .id(view.id())
        .slug(view.slug())
        .sku(view.sku())
        .name(view.name())
        .price(view.price())
        .currency(view.currency())
        .status(ProductStatusDto.valueOf(view.status().name()))
        .seller(seller(view.seller()))
        .categories(view.categories())
        .tags(view.tags())
        .publishedAt(view.publishedAt());
  }

  private static SellerSummaryDto seller(SellerSummary view) {
    return new SellerSummaryDto().id(view.id()).displayName(view.displayName()).city(view.city());
  }

  /** The stock of a product on sale; {@code null} for a product that is no longer on sale. */
  private static AvailabilityDto availability(Availability view) {
    return view == null
        ? null
        : new AvailabilityDto()
            .status(StockStatusDto.valueOf(view.status().name()))
            .units(view.units());
  }

  private static List<FacetValueDto> facetValues(List<FacetValue> views) {
    return views.stream()
        .map(
            view -> new FacetValueDto().value(view.value()).label(view.label()).count(view.count()))
        .toList();
  }
}
