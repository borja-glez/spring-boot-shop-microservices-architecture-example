package com.borjaglez.shop.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import com.borjaglez.shop.catalog.FakeStockLevels;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;

/** The public API end to end: HTTP, buses, specification-repository and PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class,
  FakeStockLevels.class
})
class CatalogApiIT {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON =
      new ParameterizedTypeReference<>() {};

  @LocalServerPort int port;
  RestClient http;

  @BeforeEach
  void client() {
    http =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build();
  }

  private ResponseEntity<Map<String, Object>> get(String uri) {
    return http.get().uri(uri).retrieve().toEntity(JSON);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> content(ResponseEntity<Map<String, Object>> response) {
    return (List<Map<String, Object>>) response.getBody().get("content");
  }

  @Test
  void searchesByTextIgnoringCase() {
    var response = get("/api/catalog/products?filter=name:contains:cafetera");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(content(response))
        .extracting(p -> p.get("sku"))
        .containsExactlyInAnyOrder("CAF-003", "ELE-005");
  }

  @Test
  void searchesIgnoringAccents() {
    var response = get("/api/catalog/products?filter=name:contains:café&size=100");

    assertThat(content(response))
        .extracting(p -> p.get("sku"))
        .contains("CAF-001", "CAF-002", "CAF-003", "ELE-005");
  }

  @Test
  @SuppressWarnings("unchecked")
  void facetsCountTheTextSearchIgnoringCase() {
    var response = get("/api/catalog/products/facets?filter=name:contains:CAFETERA");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<Map<String, Object>>) response.getBody().get("categories"))
        .filteredOn(c -> "cafe-e-infusiones".equals(c.get("value")))
        .singleElement()
        .satisfies(c -> assertThat(c.get("count")).isEqualTo(2));
  }

  @Test
  void filtersByTag() {
    var response = get("/api/catalog/products?filter=tags:in:artesania|audio&size=100");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(content(response)).extracting(p -> p.get("sku")).contains("HOG-004", "ELE-001");
  }

  @Test
  void productsWithoutTagsCanBeFound() {
    var response = get("/api/catalog/products?filter=tags:isempty&size=100");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(content(response)).extracting(p -> p.get("sku")).contains("DES-006", "EMB-003");
  }

  @Test
  void valueOfTheWrongTypeIsABadRequest() {
    var response = get("/api/catalog/products?filter=price.amount:gte:abc");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("code", "invalid-filter");
  }

  @Test
  void unknownOperatorIsABadRequest() {
    var response = get("/api/catalog/products?filter=name:like:cafe");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("code", "invalid-filter");
  }

  @Test
  void unknownEnumValueIsABadRequest() {
    var response = get("/api/catalog/products?filter=status:eq:SOLD_OUT");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void unknownFieldIsABadRequest() {
    var response = get("/api/catalog/products?filter=nonexistent:eq:x");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void filtersByPriceRangeAndSorts() {
    var response =
        get(
            "/api/catalog/products?filter=price.amount:between:5|20&sort=price.amount,asc&size=100");

    List<Map<String, Object>> products = content(response);
    assertThat(products).hasSize(33);
    List<Double> prices =
        products.stream().map(p -> ((Number) p.get("price")).doubleValue()).toList();
    assertThat(prices).isSorted();
  }

  @Test
  void combinesAlternativesWithOrFilter() {
    var response =
        get(
            "/api/catalog/products?orFilter=categories.slug:eq:vinos;seller.id:eq:seller-diego&size=100");

    assertThat(content(response))
        .extracting(p -> p.get("sku"))
        .contains("VIN-001", "ELE-001")
        .doesNotContain("VIN-005", "ELE-008");
  }

  @Test
  void pageSizeIsCapped() {
    var response = get("/api/catalog/products?size=10000");

    assertThat(response.getBody()).containsEntry("size", 100);
  }

  @Test
  void privateFieldsCannotBeFiltered() {
    var response = get("/api/catalog/products?filter=seller.email:startswith:ana");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("code", "invalid-filter");
  }

  @Test
  void privateFieldsCannotBeSortedEvenThroughPageable() {
    var response = get("/api/catalog/products?sort=seller.email,asc");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("code", "invalid-filter");
  }

  @Test
  void malformedFilterIsABadRequest() {
    var response = get("/api/catalog/products?filter=name:contains");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("code", "invalid-filter");
  }

  @Test
  void productPageAndMissingProduct() {
    assertThat(get("/api/catalog/products/cafe-de-colombia-en-grano-1-kg-caf-001").getBody())
        .containsEntry("sku", "CAF-001");

    var missing = get("/api/catalog/products/no-existe");
    assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(missing.getBody()).containsEntry("code", "product-not-found");
  }

  @Test
  void facetsFollowTheFilters() {
    var response = get("/api/catalog/products/facets?filter=categories.slug:eq:vinos");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsKeys("categories", "sellers", "tags", "price");
  }

  @Test
  void categoriesAreListed() {
    var response =
        http.get()
            .uri("/api/catalog/categories")
            .retrieve()
            .toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

    assertThat(response.getBody()).hasSize(12);
  }

  @Test
  void writesNeedAUser() {
    var response =
        http.post()
            .uri("/api/catalog/products")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"sku\":\"X-1\",\"name\":\"X\",\"price\":1,\"currency\":\"EUR\"}")
            .retrieve()
            .toEntity(JSON);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void createAndPublishAProduct() {
    var created =
        http.post()
            .uri("/api/catalog/products")
            .header("X-Shop-User", "seller-elena")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {"sku":"LIB-900","name":"Atlas de Galicia","price":31.00,"currency":"EUR",
                 "categories":["libros"],"tags":["viajes"]}
                """)
            .retrieve()
            .toEntity(JSON);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = (String) created.getBody().get("id");

    var published =
        http.post()
            .uri("/api/catalog/products/{id}/publish", id)
            .header("X-Shop-User", "seller-elena")
            .retrieve()
            .toBodilessEntity();
    assertThat(published.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(content(get("/api/catalog/products?filter=sku:eq:LIB-900"))).hasSize(1);
  }

  @Test
  void everyResponseCarriesACorrelationId() {
    var response =
        http.get()
            .uri("/api/catalog/categories")
            .header("X-Correlation-Id", "it-42")
            .retrieve()
            .toBodilessEntity();

    assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("it-42");
  }

  @Test
  void healthProbesAreExposed() {
    assertThat(get("/actuator/health/readiness").getBody()).containsEntry("status", "UP");
    assertThat(get("/actuator/health/liveness").getBody()).containsEntry("status", "UP");
  }
}
