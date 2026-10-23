package com.borjaglez.shop.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * The catalog API as a client calls it, answering every status instead of throwing. The products it
 * creates belong to {@value #SELLER}, are in the "libros" category and have SKUs that start with
 * {@value #SKU_PREFIX}, so the tests that read the seeded catalog can tell them apart.
 */
final class CatalogClient {

  static final String SELLER = "seller-elena";
  static final String SKU_PREFIX = "CMP-";
  static final String PRODUCTS = "/api/v1/catalog/products";

  private static final ParameterizedTypeReference<Map<String, Object>> JSON =
      new ParameterizedTypeReference<>() {};

  private final RestClient http;

  CatalogClient(int port) {
    http =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build();
  }

  static String uniqueSku() {
    return SKU_PREFIX + ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
  }

  RestClient http() {
    return http;
  }

  ResponseEntity<Map<String, Object>> get(String uri, Object... variables) {
    return http.get().uri(uri, variables).retrieve().toEntity(JSON);
  }

  /** The products of a page of the search, as JSON. */
  @SuppressWarnings("unchecked")
  static List<Map<String, Object>> content(ResponseEntity<Map<String, Object>> page) {
    assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
    return (List<Map<String, Object>>) page.getBody().get("content");
  }

  /** The cards the search finds for the SKU: one while the product is on sale, none otherwise. */
  List<Map<String, Object>> cardsOf(String sku) {
    return content(get(PRODUCTS + "?filter=sku:eq:{sku}", sku));
  }

  /** The slug of the product page of a product on sale. */
  String slugOf(String sku) {
    List<Map<String, Object>> cards = cardsOf(sku);
    assertThat(cards).hasSize(1);
    return (String) cards.getFirst().get("slug");
  }

  /** The product page of a product with a public page. */
  Map<String, Object> pageOf(String slug) {
    ResponseEntity<Map<String, Object>> page = get(PRODUCTS + "/{slug}", slug);
    assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
    return page.getBody();
  }

  /** Creates a draft of a book priced at {@code price} euros. */
  UUID create(String sku, String name, String price) {
    ResponseEntity<Map<String, Object>> created =
        http.post()
            .uri(PRODUCTS)
            .header("X-Shop-User", SELLER)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {"sku":"%s","name":"%s","price":%s,"currency":"EUR",
                 "categories":["libros"],"tags":["viajes"]}
                """
                    .formatted(sku, name, price))
            .retrieve()
            .toEntity(JSON);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return UUID.fromString((String) created.getBody().get("id"));
  }

  ResponseEntity<Void> publish(UUID id) {
    return http.post()
        .uri(PRODUCTS + "/{id}/publish", id)
        .header("X-Shop-User", SELLER)
        .retrieve()
        .toBodilessEntity();
  }

  /** Creates a book and puts it on sale. */
  UUID onSale(String sku, String name, String price) {
    UUID id = create(sku, name, price);
    assertThat(publish(id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    return id;
  }

  ResponseEntity<Map<String, Object>> changePrice(UUID id, String price, String currency) {
    return http.put()
        .uri(PRODUCTS + "/{id}/price", id)
        .header("X-Shop-User", SELLER)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"price\":%s,\"currency\":\"%s\"}".formatted(price, currency))
        .retrieve()
        .toEntity(JSON);
  }

  ResponseEntity<Map<String, Object>> discontinue(UUID id, String reason) {
    return http.post()
        .uri(PRODUCTS + "/{id}/discontinue", id)
        .header("X-Shop-User", SELLER)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"reason\":\"%s\"}".formatted(reason))
        .retrieve()
        .toEntity(JSON);
  }
}
