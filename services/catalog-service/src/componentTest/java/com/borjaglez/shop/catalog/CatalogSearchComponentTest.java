package com.borjaglez.shop.catalog;

import static com.borjaglez.shop.catalog.CatalogClient.PRODUCTS;
import static com.borjaglez.shop.catalog.CatalogClient.content;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * The public catalog over HTTP, on the seeded catalog (V2__catalog_seed.sql): the search and its
 * facets, the product pages and the categories, from the request parameters to PostgreSQL and back
 * as JSON. The products other component tests create are books whose names match none of the
 * searches here.
 */
@CatalogComponentTest
class CatalogSearchComponentTest {

  /** An instant in UTC as the API writes it: ISO 8601 with a Z and only the fraction it has. */
  private static final String ISO_UTC = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d*[1-9])?Z";

  private static final String CAF_001 = "cafe-de-colombia-en-grano-1-kg-caf-001";

  @LocalServerPort int port;

  CatalogClient catalog;

  @BeforeEach
  void client() {
    catalog = new CatalogClient(port);
  }

  @Test
  void theSearchIgnoresCaseAndAccents() {
    assertThat(content(catalog.get(PRODUCTS + "?filter=name:contains:CAFETERA")))
        .extracting(card -> card.get("sku"))
        .containsExactlyInAnyOrder("CAF-003", "ELE-005");
    assertThat(content(catalog.get(PRODUCTS + "?filter=name:contains:café&size=100")))
        .extracting(card -> card.get("sku"))
        .contains("CAF-001", "CAF-002", "CAF-003", "ELE-005");
  }

  @Test
  @SuppressWarnings("unchecked")
  void facetsCountTheTextSearchIgnoringCase() {
    var facets = catalog.get(PRODUCTS + "/facets?filter=name:contains:CAFETERA");

    assertThat(facets.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<Map<String, Object>>) facets.getBody().get("categories"))
        .filteredOn(category -> "cafe-e-infusiones".equals(category.get("value")))
        .singleElement()
        .satisfies(category -> assertThat(category.get("count")).isEqualTo(2));
  }

  @Test
  void searchCardsCarryUtcTimestamps() {
    assertThat(catalog.cardsOf("CAF-001"))
        .singleElement()
        .satisfies(card -> assertThat((String) card.get("publishedAt")).matches(ISO_UTC));
  }

  @Test
  @SuppressWarnings("unchecked")
  void aProductPageShowsTheProductWithUtcTimestamps() {
    Map<String, Object> page = catalog.pageOf(CAF_001);

    assertThat(page)
        .containsEntry("sku", "CAF-001")
        .containsEntry("slug", CAF_001)
        .containsEntry("name", "Café de Colombia en grano 1 kg")
        .containsEntry("status", "ACTIVE")
        .containsEntry(
            "seller", Map.of("id", "seller-ana", "displayName", "Tostadores Ana", "city", "Madrid"))
        .containsEntry("tags", List.of("comercio-justo", "ecologico"));
    assertThat((List<Map<String, Object>>) page.get("categories"))
        .containsExactly(Map.of("slug", "cafe-e-infusiones", "name", "Café e infusiones"));
    assertThat((String) page.get("publishedAt")).matches(ISO_UTC);
    assertThat((String) page.get("updatedAt")).matches(ISO_UTC);
  }

  @Test
  void aMissingProductIsAProblem() {
    var missing = catalog.get(PRODUCTS + "/no-existe");

    assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(missing.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(missing.getBody()).containsEntry("code", "product-not-found");
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({
    "/products?filter=seller.email:startswith:ana, seller.email",
    "/products?orFilter=sku:eq:CAF-001;seller.email:eq:x, seller.email",
    "'/products?sort=seller.email,asc', seller.email",
    "/products/facets?filter=seller.email:eq:ana@tostadores.example, seller.email",
    "'/products/facets?sort=name,asc', name"
  })
  void whatTheReadSideRejectsIsAProblemNamingTheField(String request, String field) {
    var rejected = catalog.get("/api/v1/catalog" + request);

    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(rejected.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(rejected.getBody())
        .containsEntry("code", "invalid-filter")
        .containsEntry("field", field);
  }

  @Test
  void anInListAboveTheShopLimitIsAProblemThatNamesTheLimit() {
    String sellers =
        IntStream.rangeClosed(1, 51).mapToObj(i -> "seller-" + i).collect(Collectors.joining("|"));

    var rejected = catalog.get(PRODUCTS + "?filter=seller.id:in:{sellers}", sellers);

    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(rejected.getBody())
        .containsEntry("code", "invalid-filter")
        .containsEntry(
            "detail",
            "Invalid filter expression 'seller.id:in': too many values (max 50) for field"
                + " 'seller.id'");
  }

  @Test
  void theCategoriesAreListed() {
    var categories =
        catalog
            .http()
            .get()
            .uri("/api/v1/catalog/categories")
            .retrieve()
            .toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

    assertThat(categories.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(categories.getBody())
        .hasSize(12)
        .anySatisfy(
            category ->
                assertThat(category)
                    .containsEntry("slug", "cafe-e-infusiones")
                    .containsEntry("activeProducts", 8));
  }
}
