package com.borjaglez.shop.catalog.infrastructure.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.catalog.domain.Money;
import com.borjaglez.shop.catalog.domain.ProductId;
import com.borjaglez.shop.catalog.domain.SellerId;
import com.borjaglez.shop.catalog.domain.Sku;
import com.borjaglez.shop.catalog.domain.event.ProductDiscontinued;
import com.borjaglez.shop.catalog.domain.event.ProductPriceChanged;
import com.borjaglez.shop.catalog.domain.event.ProductPublished;
import com.borjaglez.shop.kernel.domain.DomainEvent;

class CatalogIntegrationEventsTest {

  private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

  private final CatalogIntegrationEvents translator = new CatalogIntegrationEvents();
  private final ProductId id = ProductId.newId();

  @Test
  void aPublicationIsAnnouncedWithTheProductOnSale() {
    var events =
        translator.translate(
            new ProductPublished(
                id,
                new Sku("CAF-001"),
                "Café de Colombia en grano",
                Money.of("18.9", "EUR"),
                new SellerId("seller-ana"),
                NOW));

    assertThat(events)
        .singleElement()
        .isInstanceOfSatisfying(
            com.borjaglez.shop.contracts.catalog.ProductPublished.class,
            event -> {
              assertThat(event.getProductId()).isEqualTo(id.value());
              assertThat(event.getSku()).isEqualTo("CAF-001");
              assertThat(event.getName()).isEqualTo("Café de Colombia en grano");
              assertThat(event.getPrice()).isEqualTo("18.90");
              assertThat(event.getCurrency()).isEqualTo("EUR");
              assertThat(event.getSellerId()).isEqualTo("seller-ana");
              assertThat(event.getEventId()).isNotBlank();
            });
  }

  @Test
  void aPriceChangeIsAnnouncedWithTheOldAndTheNewPrice() {
    var events =
        translator.translate(
            new ProductPriceChanged(id, Money.of("18.90", "EUR"), Money.of("16.50", "EUR"), NOW));

    assertThat(events)
        .singleElement()
        .isInstanceOfSatisfying(
            com.borjaglez.shop.contracts.catalog.ProductPriceChanged.class,
            event -> {
              assertThat(event.getProductId()).isEqualTo(id.value());
              assertThat(event.getOldPrice()).isEqualTo("18.90");
              assertThat(event.getNewPrice()).isEqualTo("16.50");
              assertThat(event.getCurrency()).isEqualTo("EUR");
            });
  }

  @Test
  void aWithdrawalIsAnnouncedWithItsReason() {
    var events = translator.translate(new ProductDiscontinued(id, "Fin de temporada", NOW));

    assertThat(events)
        .singleElement()
        .isInstanceOfSatisfying(
            com.borjaglez.shop.contracts.catalog.ProductDiscontinued.class,
            event -> {
              assertThat(event.getProductId()).isEqualTo(id.value());
              assertThat(event.getReason()).isEqualTo("Fin de temporada");
            });
  }

  @Test
  void eventsOfOtherModelsAreNotTranslated() {
    DomainEvent foreign = () -> NOW;

    assertThatThrownBy(() -> translator.translate(foreign))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a catalog event");
  }
}
