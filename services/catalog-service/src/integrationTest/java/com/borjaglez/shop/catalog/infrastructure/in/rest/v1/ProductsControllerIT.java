package com.borjaglez.shop.catalog.infrastructure.in.rest.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.catalog.application.exception.DuplicateSkuException;
import com.borjaglez.shop.catalog.application.exception.ProductNotFoundException;
import com.borjaglez.shop.catalog.application.exception.SellerNotFoundException;
import com.borjaglez.shop.catalog.application.exception.UnknownCategoryException;
import com.borjaglez.shop.catalog.application.port.in.Availability;
import com.borjaglez.shop.catalog.application.port.in.CatalogFacets;
import com.borjaglez.shop.catalog.application.port.in.CategoryRef;
import com.borjaglez.shop.catalog.application.port.in.ChangeProductPriceCommand;
import com.borjaglez.shop.catalog.application.port.in.CreateProductCommand;
import com.borjaglez.shop.catalog.application.port.in.DiscontinueProductCommand;
import com.borjaglez.shop.catalog.application.port.in.FacetValue;
import com.borjaglez.shop.catalog.application.port.in.GetCatalogFacetsQuery;
import com.borjaglez.shop.catalog.application.port.in.GetProductQuery;
import com.borjaglez.shop.catalog.application.port.in.PriceRange;
import com.borjaglez.shop.catalog.application.port.in.ProductCard;
import com.borjaglez.shop.catalog.application.port.in.ProductDetail;
import com.borjaglez.shop.catalog.application.port.in.PublishProductCommand;
import com.borjaglez.shop.catalog.application.port.in.SearchProductsQuery;
import com.borjaglez.shop.catalog.application.port.in.SellerSummary;
import com.borjaglez.shop.catalog.application.port.in.StockStatus;
import com.borjaglez.shop.catalog.domain.ProductStatus;
import com.borjaglez.shop.catalog.domain.Sku;
import com.borjaglez.shop.catalog.domain.exception.CurrencyMismatchException;
import com.borjaglez.shop.catalog.domain.exception.InvalidCurrencyException;
import com.borjaglez.shop.catalog.domain.exception.InvalidPriceException;
import com.borjaglez.shop.catalog.domain.exception.ProductAlreadyPublishedException;
import com.borjaglez.shop.catalog.domain.exception.ProductDiscontinuedException;
import com.borjaglez.shop.kernel.query.ClientFilter;
import com.borjaglez.shop.kernel.query.Page;
import com.borjaglez.shop.kernel.query.PageRequest;
import com.borjaglez.shop.testsupport.contract.OpenApiContract;
import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.InvalidFilterException;
import com.borjaglez.specrepository.http.HttpFilterSyntaxException;
import com.borjaglez.specrepository.http.HttpUnknownOperatorException;

import tools.jackson.databind.json.JsonMapper;

/**
 * The products of the catalog API over HTTP: the message each operation sends through the buses,
 * how each result and each problem is rendered, and that every exchange follows {@code
 * catalog-api.v1.yaml}. A request that breaks the contract on purpose (to see how the server
 * rejects it) is checked with {@link OpenApiContract#matchesResponse()}.
 */
@WebMvcTest(ProductsController.class)
class ProductsControllerIT {

  private static final OpenApiContract CONTRACT =
      OpenApiContract.of(Path.of("src/main/openapi/catalog-api.v1.yaml"));

  private static final String PRODUCTS = "/api/v1/catalog/products";
  private static final String USER = "X-Shop-User";
  private static final String SELLER = "seller-ana";
  private static final UUID ID = UUID.fromString("8a9c2c1e-6f59-4a44-9d4b-0f6f4e3f1c11");
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvc mvc;
  @MockitoBean QueryBus queries;
  @MockitoBean CommandBus commands;

  private static ProductCard card() {
    return new ProductCard(
        ID,
        "cafe-de-colombia-caf-001",
        "CAF-001",
        "Café de Colombia",
        new BigDecimal("18.90"),
        "EUR",
        ProductStatus.ACTIVE,
        new SellerSummary("seller-ana", "Tostadores Ana", "Madrid"),
        List.of("cafe-e-infusiones"),
        List.of("ecologico"),
        OffsetDateTime.parse("2026-09-01T10:00:00Z"));
  }

  private static ProductDetail detail(ProductStatus status, Availability availability) {
    return new ProductDetail(
        ID,
        "cafe-de-colombia-caf-001",
        "CAF-001",
        "Café de Colombia",
        "Tueste medio",
        new BigDecimal("18.90"),
        "EUR",
        status,
        new SellerSummary("seller-ana", "Tostadores Ana", "Madrid"),
        List.of(new CategoryRef("cafe-e-infusiones", "Café e infusiones")),
        List.of("ecologico"),
        OffsetDateTime.parse("2026-09-01T10:00:00.123456Z"),
        OffsetDateTime.parse("2026-09-02T08:30:00.1Z"),
        3,
        availability);
  }

  private Query asked() {
    ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
    verify(queries).ask(captor.capture());
    return captor.getValue();
  }

  private Command dispatchedAndWaited() {
    ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
    verify(commands).dispatchAndWait(captor.capture());
    return captor.getValue();
  }

  // --- search

  @Test
  void searchRendersEachCardWithItsPublicFields() throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(new Page<>(List.of(card()), 0, 20, 1));

    mvc.perform(get(PRODUCTS))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matches())
        .andExpect(
            content()
                .json(
                    """
                    {"content": [{
                       "id": "8a9c2c1e-6f59-4a44-9d4b-0f6f4e3f1c11",
                       "slug": "cafe-de-colombia-caf-001",
                       "sku": "CAF-001",
                       "name": "Café de Colombia",
                       "price": 18.90,
                       "currency": "EUR",
                       "status": "ACTIVE",
                       "seller": {"id": "seller-ana", "displayName": "Tostadores Ana", "city": "Madrid"},
                       "categories": ["cafe-e-infusiones"],
                       "tags": ["ecologico"],
                       "publishedAt": "2026-09-01T10:00:00Z"}],
                     "page": 0, "size": 20, "totalElements": 1, "totalPages": 1}
                    """,
                    JsonCompareMode.STRICT));
  }

  @Test
  void searchAnswersWithAStablePageEnvelope() throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(new Page<>(List.of(card()), 0, 20, 41));

    mvc.perform(get(PRODUCTS))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matches())
        .andExpect(jsonPath("$.content[0].sku").value("CAF-001"))
        .andExpect(jsonPath("$.content[0].seller.displayName").value("Tostadores Ana"))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(41))
        .andExpect(jsonPath("$.totalPages").value(3));
  }

  @Test
  void searchPassesTheFiltersAsTheClientSentThem() throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(new Page<>(List.of(card()), 2, 5, 11));

    mvc.perform(
            get(PRODUCTS)
                .param("filter", "name:contains:café", "price.amount:between:5|20")
                .param("orFilter", "seller.city:eq:Madrid;tags:eq:ecologico")
                .param("sort", "price.amount,desc", "name")
                .param("page", "2")
                .param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matches());

    assertThat(asked())
        .isInstanceOfSatisfying(
            SearchProductsQuery.class,
            query -> {
              assertThat(query.getFilter())
                  .isEqualTo(
                      new ClientFilter(
                          List.of("name:contains:café", "price.amount:between:5|20"),
                          List.of("seller.city:eq:Madrid;tags:eq:ecologico"),
                          List.of("price.amount,desc", "name")));
              assertThat(query.getPage()).isEqualTo(new PageRequest(2, 5));
            });
  }

  @Test
  void aSingleSortOrFilterWithCommasReachesTheReadSideWhole() throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(new Page<>(List.of(), 0, 20, 0));

    mvc.perform(
            get(PRODUCTS)
                .param("filter", "name:contains:sal, aceite")
                .param("sort", "price.amount,desc"))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matches());

    assertThat(((SearchProductsQuery) asked()).getFilter())
        .isEqualTo(
            new ClientFilter(
                List.of("name:contains:sal, aceite"), List.of(), List.of("price.amount,desc")));
  }

  @Test
  void aSingleListOfValuesWithCommasReachesTheReadSideWhole() throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(new Page<>(List.of(), 0, 20, 0));

    mvc.perform(
            get(PRODUCTS)
                .param("filter", "name:in:sal, fina|sal, gruesa")
                .param("orFilter", "seller.city:in:Madrid|Sevilla;name:contains:sal, aceite"))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matches());

    assertThat(((SearchProductsQuery) asked()).getFilter())
        .isEqualTo(
            new ClientFilter(
                List.of("name:in:sal, fina|sal, gruesa"),
                List.of("seller.city:in:Madrid|Sevilla;name:contains:sal, aceite"),
                List.of()));
  }

  @Test
  void searchWithoutParametersAsksForTheFirstPageOfTwenty() throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(new Page<>(List.of(), 0, 20, 0));

    mvc.perform(get(PRODUCTS)).andExpect(status().isOk()).andExpect(CONTRACT.matches());

    SearchProductsQuery query = (SearchProductsQuery) asked();
    assertThat(query.getFilter()).isEqualTo(ClientFilter.none());
    assertThat(query.getPage()).isEqualTo(new PageRequest(0, 20));
  }

  /** Above the maximum of the contract, but still answered. */
  @Test
  void thePageSizeIsCapped() throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(new Page<>(List.of(), 0, 100, 0));

    mvc.perform(get(PRODUCTS).param("size", "10000"))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matchesResponse());

    assertThat(((SearchProductsQuery) asked()).getPage()).isEqualTo(new PageRequest(0, 100));
  }

  /** Outside the bounds of the contract, but still answered with the defaults. */
  @Test
  void aPageOrSizeTheServerCannotUseFallsBackToTheDefaults() throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(new Page<>(List.of(), 0, 20, 0));

    mvc.perform(get(PRODUCTS).param("page", "-1").param("size", "0"))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matchesResponse());

    assertThat(((SearchProductsQuery) asked()).getPage()).isEqualTo(new PageRequest(0, 20));
  }

  /**
   * The contract's pattern already excludes the field, but the request reaches the read side, which
   * rejects it naming the field.
   */
  @Test
  void aFilterOutsideTheWhitelistIsABadRequestNamingTheField() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenThrow(new DisallowedFieldException("seller.email", "filtering"));

    mvc.perform(get(PRODUCTS).param("filter", "seller.email:startswith:ana"))
        .andExpect(status().isBadRequest())
        .andExpect(CONTRACT.matchesResponse())
        .andExpect(jsonPath("$.code").value("invalid-filter"))
        .andExpect(jsonPath("$.field").value("seller.email"));

    assertThat(((SearchProductsQuery) asked()).getFilter().filters())
        .containsExactly("seller.email:startswith:ana");
  }

  @Test
  void aSortOutsideTheWhitelistIsABadRequestNamingTheField() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenThrow(new DisallowedFieldException("seller.email", "sorting"));

    mvc.perform(get(PRODUCTS).param("sort", "seller.email,asc"))
        .andExpect(status().isBadRequest())
        .andExpect(CONTRACT.matchesResponse())
        .andExpect(jsonPath("$.code").value("invalid-filter"))
        .andExpect(jsonPath("$.field").value("seller.email"));

    assertThat(((SearchProductsQuery) asked()).getFilter().sorts())
        .containsExactly("seller.email,asc");
  }

  @Test
  void aFilterTheDatabaseCannotRunIsABadRequestNamingTheField() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenThrow(
            new InvalidDataAccessApiUsageException(
                "wrapped by the repository",
                new InvalidFilterException("price.amount", "not a number: abc")));

    mvc.perform(get(PRODUCTS).param("filter", "price.amount:gte:abc"))
        .andExpect(status().isBadRequest())
        .andExpect(CONTRACT.matches())
        .andExpect(jsonPath("$.code").value("invalid-filter"))
        .andExpect(jsonPath("$.field").value("price.amount"));
  }

  @Test
  void anOperatorTheCatalogDoesNotOfferIsABadRequest() throws Exception {
    when(queries.ask(any(Query.class))).thenThrow(new HttpUnknownOperatorException("like"));

    mvc.perform(get(PRODUCTS).param("filter", "name:like:cafe"))
        .andExpect(status().isBadRequest())
        .andExpect(CONTRACT.matches())
        .andExpect(jsonPath("$.code").value("invalid-filter"));
  }

  @Test
  void aJsonOnlyClientGetsAnInvalidFilterAsJson() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenThrow(
            new HttpFilterSyntaxException("name:contains", "value is required for operator"));

    mvc.perform(get(PRODUCTS).param("filter", "name:contains").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(CONTRACT.matches())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("invalid-filter"));
  }

  // --- facets

  @Test
  void facetsRenderTheCountsAndThePriceRange() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenReturn(
            new CatalogFacets(
                List.of(new FacetValue("cafe-e-infusiones", "Café e infusiones", 8)),
                List.of(new FacetValue("seller-ana", "Tostadores Ana", 5)),
                List.of(new FacetValue("ecologico", "ecologico", 3)),
                new PriceRange(new BigDecimal("3.20"), new BigDecimal("349.00"))));

    mvc.perform(get(PRODUCTS + "/facets"))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matches())
        .andExpect(
            content()
                .json(
                    """
                    {"categories": [{"value": "cafe-e-infusiones", "label": "Café e infusiones", "count": 8}],
                     "sellers": [{"value": "seller-ana", "label": "Tostadores Ana", "count": 5}],
                     "tags": [{"value": "ecologico", "label": "ecologico", "count": 3}],
                     "price": {"min": 3.20, "max": 349.00}}
                    """,
                    JsonCompareMode.STRICT));
  }

  @Test
  void facetsOfASearchThatMatchesNothingHaveNoPriceRange() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenReturn(new CatalogFacets(List.of(), List.of(), List.of(), new PriceRange(null, null)));

    mvc.perform(get(PRODUCTS + "/facets").param("filter", "sku:eq:NONE"))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matches())
        .andExpect(
            content()
                .json(
                    """
                    {"categories": [], "sellers": [], "tags": [], "price": {"min": null, "max": null}}
                    """,
                    JsonCompareMode.STRICT));
  }

  /** The facets take no sort: the read side rejects one, so it reaches it too. */
  @Test
  void facetsShareTheSearchFilters() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenReturn(new CatalogFacets(List.of(), List.of(), List.of(), new PriceRange(null, null)));

    mvc.perform(
            get(PRODUCTS + "/facets")
                .param("filter", "name:contains:cafe")
                .param("orFilter", "seller.city:eq:Madrid;tags:eq:ecologico")
                .param("sort", "name,asc"))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matches());

    assertThat(asked())
        .isInstanceOfSatisfying(
            GetCatalogFacetsQuery.class,
            query ->
                assertThat(query.getFilter())
                    .isEqualTo(
                        new ClientFilter(
                            List.of("name:contains:cafe"),
                            List.of("seller.city:eq:Madrid;tags:eq:ecologico"),
                            List.of("name,asc"))));
  }

  @Test
  void facetsReceiveASingleFilterWithCommasWhole() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenReturn(new CatalogFacets(List.of(), List.of(), List.of(), new PriceRange(null, null)));

    mvc.perform(get(PRODUCTS + "/facets").param("filter", "name:contains:sal, aceite"))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matches());

    assertThat(((GetCatalogFacetsQuery) asked()).getFilter().filters())
        .containsExactly("name:contains:sal, aceite");
  }

  @Test
  void aMalformedFilterIsABadRequest() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenThrow(
            new HttpFilterSyntaxException("name:contains", "value is required for operator"));

    mvc.perform(get(PRODUCTS + "/facets").param("filter", "name:contains"))
        .andExpect(status().isBadRequest())
        .andExpect(CONTRACT.matches())
        .andExpect(jsonPath("$.code").value("invalid-filter"));
  }

  // --- product page

  @Test
  void productPageRendersTheDetailWithItsStockAndUtcTimestamps() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenReturn(detail(ProductStatus.ACTIVE, new Availability(StockStatus.LOW_STOCK, 3)));

    mvc.perform(get(PRODUCTS + "/{slug}", "cafe-de-colombia-caf-001"))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matches())
        .andExpect(
            content()
                .json(
                    """
                    {"id": "8a9c2c1e-6f59-4a44-9d4b-0f6f4e3f1c11",
                     "slug": "cafe-de-colombia-caf-001",
                     "sku": "CAF-001",
                     "name": "Café de Colombia",
                     "description": "Tueste medio",
                     "price": 18.90,
                     "currency": "EUR",
                     "status": "ACTIVE",
                     "seller": {"id": "seller-ana", "displayName": "Tostadores Ana", "city": "Madrid"},
                     "categories": [{"slug": "cafe-e-infusiones", "name": "Café e infusiones"}],
                     "tags": ["ecologico"],
                     "publishedAt": "2026-09-01T10:00:00.123456Z",
                     "updatedAt": "2026-09-02T08:30:00.1Z",
                     "version": 3,
                     "availability": {"status": "LOW_STOCK", "units": 3}}
                    """,
                    JsonCompareMode.STRICT));
    assertThat(asked())
        .isInstanceOfSatisfying(
            GetProductQuery.class,
            query -> assertThat(query.getSlug()).isEqualTo("cafe-de-colombia-caf-001"));
  }

  @Test
  void aProductNoLongerOnSaleIsRenderedWithoutStock() throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(detail(ProductStatus.DISCONTINUED, null));

    mvc.perform(get(PRODUCTS + "/{slug}", "cafe-de-colombia-caf-001"))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matches())
        .andExpect(jsonPath("$.status").value("DISCONTINUED"))
        .andExpect(content().string(containsString("\"availability\":null")));
  }

  @Test
  void aProductWhoseStockIsUnknownIsRenderedWithoutUnits() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenReturn(detail(ProductStatus.ACTIVE, Availability.unknown()));

    mvc.perform(get(PRODUCTS + "/{slug}", "cafe-de-colombia-caf-001"))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matches())
        .andExpect(
            content()
                .string(
                    containsString("\"availability\":{\"status\":\"UNKNOWN\",\"units\":null}")));
  }

  @Test
  void aDraftWithdrawnBeforeItWasPublishedHasNoPublicationDate() throws Exception {
    ProductDetail withdrawnDraft = detail(ProductStatus.DISCONTINUED, null);
    when(queries.ask(any(Query.class)))
        .thenReturn(
            new ProductDetail(
                withdrawnDraft.id(),
                withdrawnDraft.slug(),
                withdrawnDraft.sku(),
                withdrawnDraft.name(),
                "",
                withdrawnDraft.price(),
                withdrawnDraft.currency(),
                withdrawnDraft.status(),
                withdrawnDraft.seller(),
                List.of(),
                List.of(),
                null,
                withdrawnDraft.updatedAt(),
                1,
                null));

    mvc.perform(get(PRODUCTS + "/{slug}", "cafe-de-colombia-caf-001"))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matches())
        .andExpect(content().string(containsString("\"publishedAt\":null")))
        .andExpect(jsonPath("$.description").value(""));
  }

  @Test
  void aProductWithoutAPublicPageIsNotFound() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenThrow(ProductNotFoundException.publishedAs("no-existe"));

    mvc.perform(get(PRODUCTS + "/{slug}", "no-existe"))
        .andExpect(status().isNotFound())
        .andExpect(CONTRACT.matches())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("product-not-found"))
        .andExpect(jsonPath("$.detail").value("No product is published as no-existe"));
  }

  @Test
  void aJsonOnlyClientGetsANotFoundAsJson() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenThrow(ProductNotFoundException.publishedAs("no-existe"));

    mvc.perform(get(PRODUCTS + "/{slug}", "no-existe").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(CONTRACT.matches())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("product-not-found"));
  }

  // --- create

  @Test
  void creatingAProductDispatchesTheCommandForTheCurrentSeller() throws Exception {
    when(commands.dispatchAndReceive(any(Command.class))).thenReturn(ID);

    mvc.perform(
            post(PRODUCTS)
                .header(USER, SELLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(newProduct())))
        .andExpect(status().isCreated())
        .andExpect(CONTRACT.matches())
        .andExpect(content().json("{\"id\": \"" + ID + "\"}", JsonCompareMode.STRICT));

    ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
    verify(commands).dispatchAndReceive(captor.capture());
    assertThat(captor.getValue())
        .isInstanceOfSatisfying(
            CreateProductCommand.class,
            command -> {
              assertThat(command.getSellerId()).isEqualTo(SELLER);
              assertThat(command.getSku()).isEqualTo("CAF-900");
              assertThat(command.getName()).isEqualTo("Café de Honduras");
              assertThat(command.getDescription()).isEqualTo("Tueste medio");
              assertThat(command.getPrice()).isEqualByComparingTo("14.20");
              assertThat(command.getCurrency()).isEqualTo("EUR");
              assertThat(command.getCategorySlugs()).containsExactly("cafe-e-infusiones");
              assertThat(command.getTags()).containsExactly("ecologico");
            });
  }

  @Test
  void theLimitsOfEveryFieldAreAccepted() throws Exception {
    when(commands.dispatchAndReceive(any(Command.class))).thenReturn(ID);
    Map<String, Object> product = newProduct();
    product.put("sku", "S".repeat(40));
    product.put("name", "N".repeat(160));
    product.put("description", "D".repeat(4000));
    product.put("price", new BigDecimal("9999999999.99"));
    product.put("categories", IntStream.rangeClosed(1, 5).mapToObj(i -> "category-" + i).toList());
    product.put("tags", IntStream.rangeClosed(1, 10).mapToObj(i -> "tag-" + i).toList());

    mvc.perform(
            post(PRODUCTS)
                .header(USER, SELLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(product)))
        .andExpect(status().isCreated())
        .andExpect(CONTRACT.matches());
  }

  @Test
  void onlyTheRequiredFieldsAreNeeded() throws Exception {
    when(commands.dispatchAndReceive(any(Command.class))).thenReturn(ID);
    Map<String, Object> product = new HashMap<>();
    product.put("sku", "CAF-901");
    product.put("name", "Café");
    product.put("description", null);
    product.put("price", new BigDecimal("0.01"));
    product.put("currency", "EUR");

    mvc.perform(
            post(PRODUCTS)
                .header(USER, SELLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(product)))
        .andExpect(status().isCreated())
        .andExpect(CONTRACT.matches());

    ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
    verify(commands).dispatchAndReceive(captor.capture());
    CreateProductCommand command = (CreateProductCommand) captor.getValue();
    assertThat(command.getDescription()).isNull();
    assertThat(command.getCategorySlugs()).isEmpty();
    assertThat(command.getTags()).isEmpty();
  }

  /**
   * The contract asks for categories and tags without repeats, but the server takes them as sets: a
   * repeat counts once, also against the limits.
   */
  @Test
  void repeatedCategoriesAndTagsCountOnce() throws Exception {
    when(commands.dispatchAndReceive(any(Command.class))).thenReturn(ID);
    Map<String, Object> product = newProduct();
    product.put("categories", List.of("vinos", "vinos", "vinos", "vinos", "vinos", "vinos"));
    product.put("tags", Stream.generate(() -> "ecologico").limit(11).toList());

    mvc.perform(
            post(PRODUCTS)
                .header(USER, SELLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(product)))
        .andExpect(status().isCreated())
        .andExpect(CONTRACT.matchesResponse());

    ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
    verify(commands).dispatchAndReceive(captor.capture());
    CreateProductCommand command = (CreateProductCommand) captor.getValue();
    assertThat(command.getCategorySlugs()).containsExactly("vinos");
    assertThat(command.getTags()).containsExactly("ecologico");
  }

  @Test
  void creatingAProductValidatesTheBody() throws Exception {
    mvc.perform(
            post(PRODUCTS)
                .header(USER, SELLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"\",\"name\":\"Café\",\"price\":-2,\"currency\":\"EUR\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(CONTRACT.matchesResponse())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.errors[?(@.field == 'sku')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field == 'price')]").exists());
    verifyNoInteractions(commands);
  }

  /** An empty or blank text breaks one rule, reported as Bean Validation's not-blank one. */
  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void aBlankTextIsReportedAsBlank(String sku) throws Exception {
    Map<String, Object> product = newProduct();
    product.put("sku", sku);

    mvc.perform(
            post(PRODUCTS)
                .header(USER, SELLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(product)))
        .andExpect(status().isBadRequest())
        .andExpect(CONTRACT.matchesResponse())
        .andExpect(
            content()
                .json(
                    """
                    {"code": "validation-failed",
                     "errors": [{"field": "sku", "message": "must not be blank"}]}
                    """))
        .andExpect(jsonPath("$.errors.length()").value(1));
  }

  static Stream<Arguments> invalidNewProducts() {
    return Stream.of(
        arguments("no sku", "sku", null),
        arguments("an empty sku", "sku", ""),
        arguments("a blank sku", "sku", "   "),
        arguments("a sku of 41 characters", "sku", "S".repeat(41)),
        arguments("no name", "name", null),
        arguments("a blank name", "name", "\t \n"),
        arguments("a name of 161 characters", "name", "N".repeat(161)),
        arguments("a description of 4001 characters", "description", "D".repeat(4001)),
        arguments("no price", "price", null),
        arguments("a price of 0", "price", BigDecimal.ZERO),
        arguments("a negative price", "price", new BigDecimal("-2")),
        arguments("a price with 3 decimals", "price", new BigDecimal("1.234")),
        arguments("a price with 11 integer digits", "price", new BigDecimal("12345678901")),
        arguments("no currency", "currency", null),
        arguments("a blank currency", "currency", "   "),
        arguments("a currency of 2 characters", "currency", "EU"),
        arguments("a currency of 4 characters", "currency", "EURO"),
        arguments("6 categories", "categories", names("category", 6)),
        arguments("11 tags", "tags", names("tag", 11)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidNewProducts")
  void aNewProductBreakingAConstraintIsRejectedNamingTheField(
      String description, String field, Object value) throws Exception {
    Map<String, Object> product = newProduct();
    product.put(field, value);

    mvc.perform(
            post(PRODUCTS)
                .header(USER, SELLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(product)))
        .andExpect(status().isBadRequest())
        .andExpect(CONTRACT.matchesResponse())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.errors[*].field", hasItem(field)))
        .andExpect(jsonPath("$.errors[*].field", everyItem(is(field))));
    verifyNoInteractions(commands);
  }

  // --- publish, price, discontinue

  @Test
  void publishingDispatchesTheCommand() throws Exception {
    mvc.perform(post(PRODUCTS + "/{id}/publish", ID).header(USER, SELLER))
        .andExpect(status().isNoContent())
        .andExpect(CONTRACT.matches());

    assertThat(dispatchedAndWaited())
        .isInstanceOfSatisfying(
            PublishProductCommand.class,
            command -> {
              assertThat(command.getProductId()).isEqualTo(ID);
              assertThat(command.getSellerId()).isEqualTo(SELLER);
            });
  }

  @Test
  void changingThePriceDispatchesTheCommand() throws Exception {
    mvc.perform(
            put(PRODUCTS + "/{id}/price", ID)
                .header(USER, SELLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":16.50,\"currency\":\"EUR\"}"))
        .andExpect(status().isNoContent())
        .andExpect(CONTRACT.matches());

    assertThat(dispatchedAndWaited())
        .isInstanceOfSatisfying(
            ChangeProductPriceCommand.class,
            command -> {
              assertThat(command.getProductId()).isEqualTo(ID);
              assertThat(command.getSellerId()).isEqualTo(SELLER);
              assertThat(command.getPrice()).isEqualByComparingTo("16.50");
              assertThat(command.getCurrency()).isEqualTo("EUR");
            });
  }

  @Test
  void discontinuingDispatchesTheCommandWithTheReason() throws Exception {
    mvc.perform(
            post(PRODUCTS + "/{id}/discontinue", ID)
                .header(USER, SELLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Out of production\"}"))
        .andExpect(status().isNoContent())
        .andExpect(CONTRACT.matches());

    assertThat(dispatchedAndWaited())
        .isInstanceOfSatisfying(
            DiscontinueProductCommand.class,
            command -> {
              assertThat(command.getProductId()).isEqualTo(ID);
              assertThat(command.getSellerId()).isEqualTo(SELLER);
              assertThat(command.getReason()).isEqualTo("Out of production");
            });
  }

  static Stream<Arguments> invalidPrices() {
    return Stream.of(
        arguments("no price", "{\"currency\":\"EUR\"}", "price"),
        arguments("a price of 0", "{\"price\":0,\"currency\":\"EUR\"}", "price"),
        arguments("a price with 3 decimals", "{\"price\":16.505,\"currency\":\"EUR\"}", "price"),
        arguments(
            "a price with 11 integer digits",
            "{\"price\":12345678901,\"currency\":\"EUR\"}",
            "price"),
        arguments("no currency", "{\"price\":16.50}", "currency"),
        arguments("a blank currency", "{\"price\":16.50,\"currency\":\"   \"}", "currency"),
        arguments(
            "a currency of 4 characters", "{\"price\":16.50,\"currency\":\"EURO\"}", "currency"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidPrices")
  void aNewPriceBreakingAConstraintIsRejectedNamingTheField(
      String description, String body, String field) throws Exception {
    mvc.perform(
            put(PRODUCTS + "/{id}/price", ID)
                .header(USER, SELLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(CONTRACT.matchesResponse())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.errors[*].field", hasItem(field)))
        .andExpect(jsonPath("$.errors[*].field", everyItem(is(field))));
    verifyNoInteractions(commands);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"{}", "{\"reason\":\"  \"}", "{\"reason\":null}"})
  void aWithdrawalNeedsAReason(String body) throws Exception {
    mvc.perform(
            post(PRODUCTS + "/{id}/discontinue", ID)
                .header(USER, SELLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(CONTRACT.matchesResponse())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.errors[*].field", hasItem("reason")))
        .andExpect(jsonPath("$.errors[*].field", everyItem(is("reason"))));
    verifyNoInteractions(commands);
  }

  @Test
  void aReasonOfUpTo200CharactersIsAccepted() throws Exception {
    mvc.perform(
            post(PRODUCTS + "/{id}/discontinue", ID)
                .header(USER, SELLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reason", "R".repeat(200)))))
        .andExpect(status().isNoContent())
        .andExpect(CONTRACT.matches());

    mvc.perform(
            post(PRODUCTS + "/{id}/discontinue", ID)
                .header(USER, SELLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reason", "R".repeat(201)))))
        .andExpect(status().isBadRequest())
        .andExpect(CONTRACT.matchesResponse())
        .andExpect(jsonPath("$.errors[*].field", hasItem("reason")))
        .andExpect(jsonPath("$.errors[*].field", everyItem(is("reason"))));
  }

  @Test
  void malformedProductIdIsABadRequest() throws Exception {
    mvc.perform(post(PRODUCTS + "/not-a-uuid/publish").header(USER, SELLER))
        .andExpect(status().isBadRequest())
        .andExpect(CONTRACT.matchesResponse())
        .andExpect(jsonPath("$.code").value("bad-request"));
    verifyNoInteractions(commands);
  }

  // --- what every write shares

  @ParameterizedTest
  @ValueSource(strings = {"create", "publish", "price", "discontinue"})
  void everyWriteNeedsAUser(String operation) throws Exception {
    mvc.perform(write(operation, null))
        .andExpect(status().isUnauthorized())
        .andExpect(CONTRACT.matchesResponse())
        .andExpect(jsonPath("$.code").value("missing-user"));
    verifyNoInteractions(commands);
  }

  @ParameterizedTest
  @ValueSource(strings = {"create", "publish", "price", "discontinue"})
  void everyWriteRejectsAnInvalidUser(String operation) throws Exception {
    mvc.perform(write(operation, "ana; drop table"))
        .andExpect(status().isBadRequest())
        .andExpect(CONTRACT.matchesResponse())
        .andExpect(jsonPath("$.code").value("invalid-user"));
    verifyNoInteractions(commands);
  }

  static Stream<Arguments> userAndRequestProblems() {
    List<Arguments> cases = new ArrayList<>();
    String[][] users = {
      {"no", null, "401", "missing-user"},
      {"a blank", " ", "401", "missing-user"},
      {"an invalid", "ana; drop table", "400", "invalid-user"}
    };
    String[][] requests = {
      {"create", "an unreadable body"},
      {"create", "an invalid body"},
      {"publish", "a malformed id"},
      {"price", "an unreadable body"},
      {"price", "a malformed id"},
      {"discontinue", "an unreadable body"},
      {"discontinue", "a malformed id"}
    };
    for (String[] user : users) {
      for (String[] request : requests) {
        cases.add(
            arguments(
                request[0], user[0], user[1], request[1], Integer.parseInt(user[2]), user[3]));
      }
    }
    return cases.stream();
  }

  /**
   * The user comes first: a write without a valid user is answered with the user problem whatever
   * else the request gets wrong.
   */
  @ParameterizedTest(name = "{0} with {1} user and {3}")
  @MethodSource("userAndRequestProblems")
  void theUserIsCheckedBeforeTheRestOfTheRequest(
      String operation, String label, String user, String problem, int status, String code)
      throws Exception {
    MockHttpServletRequestBuilder request = brokenWrite(operation, problem);
    if (user != null) {
      request.header(USER, user);
    }

    mvc.perform(request)
        .andExpect(status().is(status))
        .andExpect(CONTRACT.matchesResponse())
        .andExpect(jsonPath("$.code").value(code));
    verifyNoInteractions(commands);
  }

  @Test
  void aJsonOnlyClientGetsAMissingUserAsJson() throws Exception {
    mvc.perform(write("publish", null).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(CONTRACT.matchesResponse())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("missing-user"));
  }

  static Stream<Arguments> writeProblems() {
    return Stream.of(
        arguments("create", new SellerNotFoundException(SELLER), 404, "seller-not-found"),
        arguments("create", new DuplicateSkuException(new Sku("CAF-900")), 409, "duplicate-sku"),
        arguments(
            "create",
            new DataIntegrityViolationException("product_sku_key"),
            409,
            "data-integrity-violation"),
        arguments("create", new UnknownCategoryException(List.of("nope")), 422, "unknown-category"),
        arguments("create", new InvalidPriceException("too low"), 422, "invalid-price"),
        arguments("publish", new ProductNotFoundException(ID), 404, "product-not-found"),
        arguments(
            "publish",
            new ProductAlreadyPublishedException("published"),
            422,
            "product-already-published"),
        arguments(
            "publish",
            new OptimisticLockingFailureException("stale"),
            409,
            "concurrent-modification"),
        arguments("price", new ProductNotFoundException(ID), 404, "product-not-found"),
        arguments("price", new CurrencyMismatchException("in EUR"), 422, "currency-mismatch"),
        arguments("price", new InvalidCurrencyException("XXX"), 422, "invalid-currency"),
        arguments("price", new ProductDiscontinuedException("gone"), 422, "product-discontinued"),
        arguments(
            "price",
            new OptimisticLockingFailureException("stale"),
            409,
            "concurrent-modification"),
        arguments("discontinue", new ProductNotFoundException(ID), 404, "product-not-found"),
        arguments(
            "discontinue",
            new OptimisticLockingFailureException("stale"),
            409,
            "concurrent-modification"));
  }

  @ParameterizedTest(name = "{0}: {3}")
  @MethodSource("writeProblems")
  void writesRenderTheProblemsOfTheCatalog(
      String operation, RuntimeException failure, int status, String code) throws Exception {
    if (operation.equals("create")) {
      when(commands.dispatchAndReceive(any(Command.class))).thenThrow(failure);
    } else {
      doThrow(failure).when(commands).dispatchAndWait(any(Command.class));
    }

    mvc.perform(write(operation, SELLER))
        .andExpect(status().is(status))
        .andExpect(CONTRACT.matches())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value(code));
  }

  private static MockHttpServletRequestBuilder write(String operation, String user) {
    MockHttpServletRequestBuilder request =
        switch (operation) {
          case "create" ->
              post(PRODUCTS).contentType(MediaType.APPLICATION_JSON).content(json(newProduct()));
          case "publish" -> post(PRODUCTS + "/{id}/publish", ID);
          case "price" ->
              put(PRODUCTS + "/{id}/price", ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"price\":16.50,\"currency\":\"EUR\"}");
          case "discontinue" ->
              post(PRODUCTS + "/{id}/discontinue", ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"reason\":\"Out of production\"}");
          default -> throw new IllegalArgumentException(operation);
        };
    return user == null ? request : request.header(USER, user);
  }

  /** A write that is wrong in another way than its user. */
  private static MockHttpServletRequestBuilder brokenWrite(String operation, String problem) {
    String id = problem.equals("a malformed id") ? "not-a-uuid" : ID.toString();
    String body =
        switch (problem) {
          case "an unreadable body" -> "{";
          case "an invalid body" -> "{\"sku\":\"\",\"price\":-2}";
          default ->
              operation.equals("price")
                  ? "{\"price\":16.50,\"currency\":\"EUR\"}"
                  : "{\"reason\":\"Out of production\"}";
        };
    return switch (operation) {
      case "create" -> post(PRODUCTS).contentType(MediaType.APPLICATION_JSON).content(body);
      case "publish" -> post(PRODUCTS + "/{id}/publish", id);
      case "price" ->
          put(PRODUCTS + "/{id}/price", id).contentType(MediaType.APPLICATION_JSON).content(body);
      default ->
          post(PRODUCTS + "/{id}/discontinue", id)
              .contentType(MediaType.APPLICATION_JSON)
              .content(body);
    };
  }

  /** A valid new product, as a mutable map to break one field at a time. */
  private static Map<String, Object> newProduct() {
    Map<String, Object> product = new LinkedHashMap<>();
    product.put("sku", "CAF-900");
    product.put("name", "Café de Honduras");
    product.put("description", "Tueste medio");
    product.put("price", new BigDecimal("14.20"));
    product.put("currency", "EUR");
    product.put("categories", List.of("cafe-e-infusiones"));
    product.put("tags", List.of("ecologico"));
    return product;
  }

  private static List<String> names(String prefix, int count) {
    return IntStream.rangeClosed(1, count).mapToObj(i -> prefix + "-" + i).toList();
  }

  private static String json(Object body) {
    return JSON.writeValueAsString(body);
  }
}
