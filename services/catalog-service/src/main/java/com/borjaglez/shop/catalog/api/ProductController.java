package com.borjaglez.shop.catalog.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.catalog.api.dto.ChangePriceRequest;
import com.borjaglez.shop.catalog.api.dto.CreateProductRequest;
import com.borjaglez.shop.catalog.api.dto.CreatedResponse;
import com.borjaglez.shop.catalog.api.dto.DiscontinueRequest;
import com.borjaglez.shop.catalog.application.port.in.CatalogFacets;
import com.borjaglez.shop.catalog.application.port.in.ChangeProductPriceCommand;
import com.borjaglez.shop.catalog.application.port.in.CreateProductCommand;
import com.borjaglez.shop.catalog.application.port.in.DiscontinueProductCommand;
import com.borjaglez.shop.catalog.application.port.in.GetCatalogFacetsQuery;
import com.borjaglez.shop.catalog.application.port.in.GetProductQuery;
import com.borjaglez.shop.catalog.application.port.in.ProductCard;
import com.borjaglez.shop.catalog.application.port.in.ProductDetail;
import com.borjaglez.shop.catalog.application.port.in.PublishProductCommand;
import com.borjaglez.shop.catalog.application.port.in.SearchProductsQuery;
import com.borjaglez.shop.kernel.query.ClientFilter;
import com.borjaglez.shop.kernel.query.Page;
import com.borjaglez.shop.kernel.query.PageRequest;
import com.borjaglez.shop.support.web.CurrentUser;
import com.borjaglez.shop.support.web.PageResponse;

/**
 * Public catalog API.
 *
 * <p>Search and facets accept the specification-repository HTTP filter syntax: repeatable {@code
 * filter=field:op:value}, {@code orFilter=a:op:v;b:op:v} and {@code sort=field,dir}. They reach the
 * read side as the client sent them, and the read side checks them against the public whitelist; a
 * rejected filter comes back through the query bus and is answered with 400 {@code invalid-filter}.
 * The controller only translates HTTP into commands and queries; the buses do the rest.
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

  /**
   * The page and its size come from {@code pageable}, so the shop's paging defaults and its cap on
   * the size apply; the sort goes to the read side with the filters.
   */
  @GetMapping
  PageResponse<ProductCard> search(
      @RequestParam MultiValueMap<String, String> params, Pageable pageable) {
    Page<ProductCard> page =
        queries.ask(
            new SearchProductsQuery(
                clientFilter(params),
                new PageRequest(pageable.getPageNumber(), pageable.getPageSize())));
    return new PageResponse<>(
        page.content(), page.page(), page.size(), page.totalElements(), page.totalPages());
  }

  @GetMapping("/{slug}")
  ProductDetail product(@PathVariable String slug) {
    return queries.ask(new GetProductQuery(slug));
  }

  @GetMapping("/facets")
  CatalogFacets facets(@RequestParam MultiValueMap<String, String> params) {
    return queries.ask(new GetCatalogFacetsQuery(clientFilter(params)));
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
   * The filters and sorts exactly as the client sent them. They are read from the raw parameters:
   * binding a single value to a list would split it at its commas, and a sort such as {@code
   * price.amount,desc} or a text with commas would no longer be what the client asked for.
   */
  private static ClientFilter clientFilter(MultiValueMap<String, String> params) {
    return new ClientFilter(params.get("filter"), params.get("orFilter"), params.get("sort"));
  }
}
