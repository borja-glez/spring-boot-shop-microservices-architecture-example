package com.borjaglez.shop.catalog.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.shop.contracts.catalog.ProductDiscontinued;
import com.borjaglez.shop.contracts.catalog.ProductPriceChanged;
import com.borjaglez.shop.contracts.catalog.ProductPublished;

/**
 * Local subscriber that makes catalog events visible in the logs. Events travel through Kafka, so
 * inventory and reporting subscribe the same way from their own processes.
 */
@EventHandler
public class CatalogEventsLogger {

  private static final Logger log = LoggerFactory.getLogger(CatalogEventsLogger.class);

  @HandleEvent
  public void on(ProductPublished event) {
    log.info(
        "Product {} published at {} {}", event.getSku(), event.getPrice(), event.getCurrency());
  }

  @HandleEvent
  public void on(ProductPriceChanged event) {
    log.info(
        "Product {} price changed from {} to {}",
        event.getProductId(),
        event.getOldPrice(),
        event.getNewPrice());
  }

  @HandleEvent
  public void on(ProductDiscontinued event) {
    log.info("Product {} discontinued: {}", event.getProductId(), event.getReason());
  }
}
