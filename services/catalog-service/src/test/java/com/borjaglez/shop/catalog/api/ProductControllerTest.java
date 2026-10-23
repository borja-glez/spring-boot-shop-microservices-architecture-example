package com.borjaglez.shop.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.catalog.application.exception.ProductNotFound;
import com.borjaglez.shop.catalog.application.port.in.Availability;
import com.borjaglez.shop.catalog.application.port.in.CatalogFacets;
import com.borjaglez.shop.catalog.application.port.in.CategoryRef;
import com.borjaglez.shop.catalog.application.port.in.ChangeProductPriceCommand;
import com.borjaglez.shop.catalog.application.port.in.CreateProductCommand;
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
import com.borjaglez.shop.kernel.query.ClientFilter;
import com.borjaglez.shop.kernel.query.Page;
import com.borjaglez.shop.kernel.query.PageRequest;
import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.InvalidFilterException;
import com.borjaglez.specrepository.http.HttpFilterSyntaxException;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

  private static final UUID ID = UUID.fromString("8a9c2c1e-6f59-4a44-9d4b-0f6f4e3f1c11");

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

  @Test
  void searchRendersEachCardWithItsPublicFields() throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(new Page<>(List.of(card()), 0, 20, 1));

    mvc.perform(get("/api/catalog/products"))
        .andExpect(status().isOk())
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
  void productPageRendersTheDetailWithItsStockAndUtcTimestamps() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenReturn(detail(ProductStatus.ACTIVE, new Availability(StockStatus.LOW_STOCK, 3)));

    mvc.perform(get("/api/catalog/products/{slug}", "cafe-de-colombia-caf-001"))
        .andExpect(status().isOk())
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

    mvc.perform(get("/api/catalog/products/{slug}", "cafe-de-colombia-caf-001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISCONTINUED"))
        .andExpect(content().string(containsString("\"availability\":null")));
  }

  @Test
  void aProductWithoutAPublicPageIsNotFound() throws Exception {
    when(queries.ask(any(Query.class))).thenThrow(ProductNotFound.publishedAs("no-existe"));

    mvc.perform(get("/api/catalog/products/{slug}", "no-existe"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("product-not-found"))
        .andExpect(jsonPath("$.detail").value("No product is published as no-existe"));
  }

  @Test
  void facetsRenderTheCountsAndThePriceRange() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenReturn(
            new CatalogFacets(
                List.of(new FacetValue("cafe-e-infusiones", "Café e infusiones", 8)),
                List.of(new FacetValue("seller-ana", "Tostadores Ana", 5)),
                List.of(new FacetValue("ecologico", "ecologico", 3)),
                new PriceRange(new BigDecimal("3.20"), new BigDecimal("349.00"))));

    mvc.perform(get("/api/catalog/products/facets"))
        .andExpect(status().isOk())
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
  void searchPassesTheFiltersAsTheClientSentThem() throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(new Page<>(List.of(card()), 2, 5, 11));

    mvc.perform(
            get("/api/catalog/products")
                .param("filter", "name:contains:café", "price.amount:between:5|20")
                .param("orFilter", "seller.city:eq:Madrid;tags:eq:ecologico")
                .param("sort", "price.amount,desc", "name")
                .param("page", "2")
                .param("size", "5"))
        .andExpect(status().isOk());

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
            get("/api/catalog/products")
                .param("filter", "name:contains:sal, aceite")
                .param("sort", "price.amount,desc"))
        .andExpect(status().isOk());

    assertThat(((SearchProductsQuery) asked()).getFilter())
        .isEqualTo(
            new ClientFilter(
                List.of("name:contains:sal, aceite"), List.of(), List.of("price.amount,desc")));
  }

  @Test
  void searchWithoutParametersAsksForTheFirstPageOfTwenty() throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(new Page<>(List.of(), 0, 20, 0));

    mvc.perform(get("/api/catalog/products")).andExpect(status().isOk());

    SearchProductsQuery query = (SearchProductsQuery) asked();
    assertThat(query.getFilter()).isEqualTo(ClientFilter.none());
    assertThat(query.getPage()).isEqualTo(new PageRequest(0, 20));
  }

  @Test
  void thePageSizeIsCapped() throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(new Page<>(List.of(), 0, 100, 0));

    mvc.perform(get("/api/catalog/products").param("size", "10000")).andExpect(status().isOk());

    assertThat(((SearchProductsQuery) asked()).getPage()).isEqualTo(new PageRequest(0, 100));
  }

  @Test
  void facetsShareTheSearchFilters() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenReturn(new CatalogFacets(List.of(), List.of(), List.of(), new PriceRange(null, null)));

    mvc.perform(
            get("/api/catalog/products/facets")
                .param("filter", "name:contains:cafe")
                .param("orFilter", "seller.city:eq:Madrid;tags:eq:ecologico")
                .param("sort", "name,asc"))
        .andExpect(status().isOk());

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
  void searchAnswersWithAStablePageEnvelope() throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(new Page<>(List.of(card()), 0, 20, 41));

    mvc.perform(get("/api/catalog/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].sku").value("CAF-001"))
        .andExpect(jsonPath("$.content[0].seller.displayName").value("Tostadores Ana"))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(41))
        .andExpect(jsonPath("$.totalPages").value(3));
  }

  @Test
  void aFilterOutsideTheWhitelistIsABadRequestNamingTheField() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenThrow(new DisallowedFieldException("seller.email", "filtering"));

    mvc.perform(get("/api/catalog/products").param("filter", "seller.email:startswith:ana"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"))
        .andExpect(jsonPath("$.field").value("seller.email"));
  }

  @Test
  void aMalformedFilterIsABadRequest() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenThrow(
            new HttpFilterSyntaxException("name:contains", "value is required for operator"));

    mvc.perform(get("/api/catalog/products/facets").param("filter", "name:contains"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"));
  }

  @Test
  void aFilterTheDatabaseCannotRunIsABadRequestNamingTheField() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenThrow(
            new InvalidDataAccessApiUsageException(
                "wrapped by the repository",
                new InvalidFilterException("price.amount", "not a number: abc")));

    mvc.perform(get("/api/catalog/products").param("filter", "price.amount:gte:abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"))
        .andExpect(jsonPath("$.field").value("price.amount"));
  }

  @Test
  void creatingAProductNeedsAUser() throws Exception {
    mvc.perform(
            post("/api/catalog/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateBody()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("missing-user"));
    verifyNoInteractions(commands);
  }

  @Test
  void creatingAProductValidatesTheBody() throws Exception {
    mvc.perform(
            post("/api/catalog/products")
                .header("X-Shop-User", "seller-ana")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"\",\"name\":\"Café\",\"price\":-2,\"currency\":\"EUR\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.errors[?(@.field == 'sku')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field == 'price')]").exists());
    verifyNoInteractions(commands);
  }

  @Test
  void creatingAProductDispatchesTheCommandForTheCurrentSeller() throws Exception {
    when(commands.dispatchAndReceive(any(Command.class))).thenReturn(ID);

    mvc.perform(
            post("/api/catalog/products")
                .header("X-Shop-User", "seller-ana")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateBody()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(ID.toString()));

    ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
    verify(commands).dispatchAndReceive(captor.capture());
    CreateProductCommand command = (CreateProductCommand) captor.getValue();
    assertThat(command.getSellerId()).isEqualTo("seller-ana");
    assertThat(command.getCategorySlugs()).containsExactly("cafe-e-infusiones");
  }

  @Test
  void publishingDispatchesTheCommand() throws Exception {
    mvc.perform(post("/api/catalog/products/{id}/publish", ID).header("X-Shop-User", "seller-ana"))
        .andExpect(status().isNoContent());

    ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
    verify(commands).dispatchAndWait(captor.capture());
    assertThat(captor.getValue())
        .isInstanceOfSatisfying(
            PublishProductCommand.class, c -> assertThat(c.getProductId()).isEqualTo(ID));
  }

  @Test
  void changingThePriceDispatchesTheCommand() throws Exception {
    mvc.perform(
            put("/api/catalog/products/{id}/price", ID)
                .header("X-Shop-User", "seller-ana")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":16.50,\"currency\":\"EUR\"}"))
        .andExpect(status().isNoContent());

    ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
    verify(commands).dispatchAndWait(captor.capture());
    assertThat(captor.getValue())
        .isInstanceOfSatisfying(
            ChangeProductPriceCommand.class,
            c -> assertThat(c.getPrice()).isEqualByComparingTo("16.50"));
  }

  @Test
  void malformedProductIdIsABadRequest() throws Exception {
    mvc.perform(
            post("/api/catalog/products/not-a-uuid/publish").header("X-Shop-User", "seller-ana"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("bad-request"));
  }

  private static String validCreateBody() {
    return """
        {
          "sku": "CAF-900",
          "name": "Café de Honduras",
          "description": "Tueste medio",
          "price": 14.20,
          "currency": "EUR",
          "categories": ["cafe-e-infusiones"],
          "tags": ["ecologico"]
        }
        """;
  }
}
