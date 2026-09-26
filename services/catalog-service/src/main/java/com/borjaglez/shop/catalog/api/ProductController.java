package com.borjaglez.shop.catalog.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.catalog.api.dto.ChangePriceRequest;
import com.borjaglez.shop.catalog.api.dto.CreateProductRequest;
import com.borjaglez.shop.catalog.api.dto.CreatedResponse;
import com.borjaglez.shop.catalog.api.dto.DiscontinueRequest;
import com.borjaglez.shop.catalog.application.command.ChangeProductPriceCommand;
import com.borjaglez.shop.catalog.application.command.CreateProductCommand;
import com.borjaglez.shop.catalog.application.command.DiscontinueProductCommand;
import com.borjaglez.shop.catalog.application.command.PublishProductCommand;
import com.borjaglez.shop.catalog.application.query.GetCatalogFacetsQuery;
import com.borjaglez.shop.catalog.application.query.GetProductQuery;
import com.borjaglez.shop.catalog.application.query.ProductViews.CatalogFacets;
import com.borjaglez.shop.catalog.application.query.ProductViews.ProductCard;
import com.borjaglez.shop.catalog.application.query.ProductViews.ProductDetail;
import com.borjaglez.shop.catalog.application.query.SearchProductsQuery;
import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.shop.support.web.CurrentUser;
import com.borjaglez.shop.support.web.PageResponse;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.http.spring.FilterableQuery;

/**
 * Public catalog API.
 *
 * <p>Search and facets accept the specification-repository HTTP filter syntax: repeatable {@code
 * filter=field:op:value}, {@code orFilter=a:op:v;b:op:v} and {@code sort=field,dir}, restricted to
 * the fields listed in each {@link FilterableQuery}. Private data such as the seller's email is
 * never listed. The controller only translates HTTP into commands and queries; the buses do the
 * rest.
 *
 * <p>The field lists are repeated in both endpoints because {@code @FilterableQuery} cannot be
 * composed into a meta-annotation.
 */
@RestController
@RequestMapping("/api/catalog/products")
class ProductController {

  private final CommandBus commands;
  private final QueryBus queries;

  ProductController(CommandBus commands, QueryBus queries) {
    this.commands = commands;
    this.queries = queries;
  }

  @GetMapping
  PageResponse<ProductCard> search(
      @FilterableQuery(
              value = Product.class,
              filterableFields = {
                "name",
                "description",
                "sku",
                "price.amount",
                "status",
                "categories.slug",
                "tags",
                "seller.id",
                "seller.city",
                "publishedAt"
              },
              sortableFields = {"name", "sku", "price.amount", "publishedAt"})
          QueryPlan<Product> plan,
      Pageable pageable) {
    Page<ProductCard> page = queries.ask(new SearchProductsQuery(plan, pagingOnly(pageable)));
    return PageResponse.of(page);
  }

  @GetMapping("/{slug}")
  ProductDetail product(@PathVariable String slug) {
    return queries.ask(new GetProductQuery(slug));
  }

  @GetMapping("/facets")
  CatalogFacets facets(
      @FilterableQuery(
              value = Product.class,
              filterableFields = {
                "name",
                "description",
                "sku",
                "price.amount",
                "status",
                "categories.slug",
                "tags",
                "seller.id",
                "seller.city",
                "publishedAt"
              })
          QueryPlan<Product> plan) {
    return queries.ask(new GetCatalogFacetsQuery(plan));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  CreatedResponse create(
      @CurrentUser String seller, @Valid @RequestBody CreateProductRequest body) {
    UUID id =
        commands.dispatchAndReceive(
            new CreateProductCommand(
                seller,
                body.sku(),
                body.name(),
                body.description(),
                body.price(),
                body.currency(),
                body.categories(),
                body.tags()));
    return new CreatedResponse(id);
  }

  @PostMapping("/{id}/publish")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void publish(@CurrentUser String seller, @PathVariable UUID id) {
    commands.dispatchAndWait(new PublishProductCommand(id, seller));
  }

  @PutMapping("/{id}/price")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void changePrice(
      @CurrentUser String seller,
      @PathVariable UUID id,
      @Valid @RequestBody ChangePriceRequest body) {
    commands.dispatchAndWait(
        new ChangeProductPriceCommand(id, seller, body.price(), body.currency()));
  }

  @PostMapping("/{id}/discontinue")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void discontinue(
      @CurrentUser String seller,
      @PathVariable UUID id,
      @Valid @RequestBody DiscontinueRequest body) {
    commands.dispatchAndWait(new DiscontinueProductCommand(id, seller, body.reason()));
  }

  /**
   * Keeps page number and size but drops the sort: the {@code sort} parameter is also parsed into
   * the plan, where the whitelist validates it. A {@code Pageable} sort would replace it without
   * validation.
   */
  private static Pageable pagingOnly(Pageable pageable) {
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
  }
}
