package com.borjaglez.shop.catalog.infrastructure.in.rest.v1;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.catalog.application.port.in.GetProductQuery;
import com.borjaglez.shop.catalog.application.port.in.ProductCard;
import com.borjaglez.shop.catalog.application.port.in.PublishProductCommand;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.api.ProductsApi;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.CatalogFacets;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.ChangePriceRequest;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.CreateProductRequest;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.CreatedProduct;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.DiscontinueRequest;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.ProductDetail;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.ProductPage;
import com.borjaglez.shop.kernel.query.Page;
import com.borjaglez.shop.support.web.ClientFilters;
import com.borjaglez.specrepository.http.spring.HttpFilterProperties;

/**
 * [Hexagonal · adaptador driving] The products of the catalog API, {@code
 * /api/v1/catalog/products}: the operations tagged {@code products} in {@code catalog-api.v1.yaml}.
 *
 * <p>Each operation turns the request into a command or a query of the core and sends it through
 * the buses; service-support renders what they reject as problems. A write acts for the seller
 * named in {@code X-Shop-User}, a header the generated interface requires and checks.
 *
 * <p>The search and its facets take the filter syntax of specification-repository. The filters,
 * alternative filters and sorts reach the read side as the client sent them, read from the request
 * ({@code x-spring-provide-args} in the contract) by {@link ClientFilters}: the lists the interface
 * binds would split a single value at its commas ({@code sort=price.amount,desc}). The parameter
 * names come from the {@code specrepository.http.*} syntax the read side parses with. The patterns
 * the contract gives these parameters describe the public whitelist; the read side enforces it, so
 * a filter or sort on any other field is answered with 400 {@code invalid-filter} naming the field,
 * like a malformed filter or one above the limits. The page and its size are a Spring Data {@link
 * Pageable} ({@code x-spring-paginated} in the contract): the shop's default size of 20 and its cap
 * of 100 apply.
 */
@RestController
class ProductsController implements ProductsApi {

  private final CommandBus commands;
  private final QueryBus queries;
  private final ClientFilters clientFilters;

  ProductsController(CommandBus commands, QueryBus queries, HttpFilterProperties filterSyntax) {
    this.commands = commands;
    this.queries = queries;
    this.clientFilters = new ClientFilters(filterSyntax.toParserConfiguration());
  }

  /** {@code filter} and {@code orFilter} are read whole from {@code request} (see the class). */
  @Override
  public ResponseEntity<ProductPage> searchProducts(
      List<String> filter, List<String> orFilter, Pageable pageable, HttpServletRequest request) {
    Page<ProductCard> page =
        queries.ask(CatalogRestMapper.searchQuery(clientFilters.from(request), pageable));
    return ResponseEntity.ok(CatalogRestMapper.page(page));
  }

  /** {@code filter} and {@code orFilter} are read whole from {@code request} (see the class). */
  @Override
  public ResponseEntity<CatalogFacets> getCatalogFacets(
      List<String> filter, List<String> orFilter, HttpServletRequest request) {
    com.borjaglez.shop.catalog.application.port.in.CatalogFacets facets =
        queries.ask(CatalogRestMapper.facetsQuery(clientFilters.from(request)));
    return ResponseEntity.ok(CatalogRestMapper.facets(facets));
  }

  @Override
  public ResponseEntity<ProductDetail> getProduct(String slug) {
    com.borjaglez.shop.catalog.application.port.in.ProductDetail product =
        queries.ask(new GetProductQuery(slug));
    return ResponseEntity.ok(CatalogRestMapper.detail(product));
  }

  @Override
  public ResponseEntity<CreatedProduct> createProduct(
      String seller, CreateProductRequest createProductRequest) {
    UUID id =
        commands.dispatchAndReceive(CatalogRestMapper.createCommand(seller, createProductRequest));
    return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedProduct(id));
  }

  @Override
  public ResponseEntity<Void> publishProduct(String seller, UUID id) {
    commands.dispatchAndWait(new PublishProductCommand(id, seller));
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> changeProductPrice(
      String seller, UUID id, ChangePriceRequest changePriceRequest) {
    commands.dispatchAndWait(CatalogRestMapper.changePriceCommand(id, seller, changePriceRequest));
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> discontinueProduct(
      String seller, UUID id, DiscontinueRequest discontinueRequest) {
    commands.dispatchAndWait(CatalogRestMapper.discontinueCommand(id, seller, discontinueRequest));
    return ResponseEntity.noContent().build();
  }
}
