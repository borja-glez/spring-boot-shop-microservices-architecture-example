package com.borjaglez.shop.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.borjaglez.shop.catalog.domain.ProductId;

/**
 * The stock on the product pages of the seeded catalog, asked to the inventory over a real
 * RabbitMQ: the catalog's own adapter sends each question to the broker, and {@link
 * InventoryStandIn} takes it from there and answers as inventory-service does.
 */
@CatalogComponentTest
class StockLevelsOverRabbitComponentTest {

  @LocalServerPort int port;
  @Autowired InventoryStandIn inventory;
  @Autowired StockQuestions questions;

  CatalogClient catalog;

  @BeforeEach
  void client() {
    catalog = new CatalogClient(port);
  }

  private UUID idOf(String slug) {
    return UUID.fromString((String) catalog.pageOf(slug).get("id"));
  }

  @Test
  void theProductPageShowsTheStockTheInventoryReports() {
    String slug = "cafe-molido-natural-500-g-caf-002";
    UUID id = idOf(slug);
    inventory.stock(id, 3);

    Map<String, Object> page = catalog.pageOf(slug);

    assertThat(page)
        .containsEntry("sku", "CAF-002")
        .containsEntry("availability", Map.of("status", "LOW_STOCK", "units", 3));
    assertThat(inventory.questionsAbout(id))
        .last()
        .satisfies(question -> assertThat(question.productIds()).containsExactly(id));
  }

  @Test
  void aProductTheInventoryDoesNotKnowIsOutOfStock() {
    Map<String, Object> page = catalog.pageOf(catalog.slugOf("ACE-002"));

    assertThat(page).containsEntry("availability", Map.of("status", "OUT_OF_STOCK", "units", 0));
  }

  @Test
  @SuppressWarnings("unchecked")
  void theProductPageIsShownWhenTheInventoryFails() {
    String slug = "te-verde-matcha-ceremonial-30-g-caf-005";
    inventory.failsFor(idOf(slug));

    Map<String, Object> page = catalog.pageOf(slug);

    assertThat(page).containsEntry("sku", "CAF-005");
    assertThat((Map<String, Object>) page.get("availability"))
        .containsEntry("status", "UNKNOWN")
        .containsEntry("units", null);
  }

  @Test
  void aProductNoLongerOnSaleHasNoStockAndTheInventoryIsNotAsked() {
    Map<String, Object> page = catalog.pageOf("rooibos-con-vainilla-caf-008");

    assertThat(page).containsEntry("status", "DISCONTINUED").containsEntry("availability", null);
    assertThat(inventory.questionsAbout(UUID.fromString((String) page.get("id")))).isEmpty();
  }

  @Test
  void theInventoryIsAskedOutsideAnyTransaction() {
    String slug = "cafetera-italiana-de-aluminio-6-tazas-caf-003";
    UUID id = idOf(slug);
    inventory.stock(id, 12);

    catalog.pageOf(slug);

    List<StockQuestions.Question> asked = questions.about(new ProductId(id));
    assertThat(asked).isNotEmpty().noneMatch(StockQuestions.Question::inTransaction);
  }
}
