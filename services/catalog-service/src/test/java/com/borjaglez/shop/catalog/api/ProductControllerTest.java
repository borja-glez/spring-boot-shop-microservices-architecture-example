package com.borjaglez.shop.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.catalog.application.command.ChangeProductPriceCommand;
import com.borjaglez.shop.catalog.application.command.CreateProductCommand;
import com.borjaglez.shop.catalog.application.command.PublishProductCommand;
import com.borjaglez.shop.catalog.application.query.GetCatalogFacetsQuery;
import com.borjaglez.shop.catalog.application.query.ProductViews.CatalogFacets;
import com.borjaglez.shop.catalog.application.query.ProductViews.PriceRange;
import com.borjaglez.shop.catalog.application.query.ProductViews.ProductCard;
import com.borjaglez.shop.catalog.application.query.ProductViews.SellerSummary;
import com.borjaglez.shop.catalog.application.query.SearchProductsQuery;
import com.borjaglez.shop.catalog.domain.ProductStatus;
import com.borjaglez.specrepository.core.PredicateCondition;

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

  @Test
  void searchTurnsHttpFiltersIntoAQueryPlan() throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(new PageImpl<>(List.of(card())));

    mvc.perform(
            get("/api/catalog/products")
                .param("filter", "name:contains:café")
                .param("sort", "price.amount,desc")
                .param("page", "2")
                .param("size", "5"))
        .andExpect(status().isOk());

    ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
    verify(queries).ask(captor.capture());
    SearchProductsQuery query = (SearchProductsQuery) captor.getValue();
    assertThat(query.getPlan().rootCondition().conditions())
        .singleElement()
        .isInstanceOfSatisfying(
            PredicateCondition.class,
            c -> {
              assertThat(c.field()).isEqualTo("name");
              assertThat(c.value()).isEqualTo("café");
            });
    assertThat(query.getPlan().sort()).isEqualTo(Sort.by(Sort.Order.desc("price.amount")));
    assertThat(query.getPageable())
        .isEqualTo(PageRequest.of(2, 5, Sort.by(Sort.Order.desc("price.amount"))));
  }

  @Test
  void textSearchIgnoresCaseOnNameAndDescriptionOnly() throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(new PageImpl<>(List.of(card())));

    mvc.perform(
            get("/api/catalog/products")
                .param("filter", "name:contains:cafe")
                .param("filter", "description:contains:tueste")
                .param("filter", "sku:startswith:CAF"))
        .andExpect(status().isOk());

    ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
    verify(queries).ask(captor.capture());
    SearchProductsQuery query = (SearchProductsQuery) captor.getValue();
    assertThat(query.getPlan().rootCondition().conditions())
        .map(PredicateCondition.class::cast)
        .extracting(PredicateCondition::field, PredicateCondition::ignoreCase)
        .containsExactly(tuple("name", true), tuple("description", true), tuple("sku", false));
  }

  @Test
  void privateFieldsCannotBeFiltered() throws Exception {
    mvc.perform(get("/api/catalog/products").param("filter", "seller.email:startswith:ana"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"));
    verifyNoInteractions(queries);
  }

  @Test
  void onlyListedFieldsCanBeSorted() throws Exception {
    mvc.perform(get("/api/catalog/products").param("sort", "seller.email,asc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"));
    verifyNoInteractions(queries);
  }

  @Test
  void facetsShareTheSearchFilters() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenReturn(new CatalogFacets(List.of(), List.of(), List.of(), new PriceRange(null, null)));

    mvc.perform(
            get("/api/catalog/products/facets")
                .param("filter", "name:contains:cafe")
                .param("orFilter", "seller.city:eq:Madrid;tags:eq:ecologico"))
        .andExpect(status().isOk());

    ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
    verify(queries).ask(captor.capture());
    GetCatalogFacetsQuery query = (GetCatalogFacetsQuery) captor.getValue();
    assertThat(query.getPlan().rootCondition().conditions()).hasSize(2);
    assertThat(query.getPlan().rootCondition().conditions().getFirst())
        .isInstanceOfSatisfying(PredicateCondition.class, c -> assertThat(c.ignoreCase()).isTrue());
  }

  @Test
  void facetsCannotBeSorted() throws Exception {
    mvc.perform(get("/api/catalog/products/facets").param("sort", "name,asc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"));
    verifyNoInteractions(queries);
  }

  @Test
  void searchAnswersWithAStablePageEnvelope() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenReturn(new PageImpl<>(List.of(card()), PageRequest.of(0, 20), 41));

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
  void malformedFilterIsABadRequest() throws Exception {
    mvc.perform(get("/api/catalog/products").param("filter", "name:contains"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"));
    verifyNoInteractions(queries);
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
