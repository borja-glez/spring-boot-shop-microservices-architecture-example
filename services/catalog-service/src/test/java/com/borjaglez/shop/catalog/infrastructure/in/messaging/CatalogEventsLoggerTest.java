package com.borjaglez.shop.catalog.infrastructure.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.borjaglez.shop.contracts.catalog.ProductDiscontinued;
import com.borjaglez.shop.contracts.catalog.ProductPriceChanged;
import com.borjaglez.shop.contracts.catalog.ProductPublished;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class CatalogEventsLoggerTest {

  private static final UUID PRODUCT = UUID.fromString("0b5d4e0a-6f0e-4a57-9c52-3f1d6b1c9a10");

  private final Logger logger = (Logger) LoggerFactory.getLogger(CatalogEventsLogger.class);
  private final ListAppender<ILoggingEvent> lines = new ListAppender<>();
  private final CatalogEventsLogger events = new CatalogEventsLogger();

  @BeforeEach
  void listen() {
    lines.start();
    logger.addAppender(lines);
  }

  @AfterEach
  void stopListening() {
    logger.detachAppender(lines);
  }

  private ILoggingEvent onlyLine() {
    assertThat(lines.list).hasSize(1);
    return lines.list.getFirst();
  }

  @Test
  void aPublicationIsLoggedWithTheSkuAndThePrice() {
    events.on(
        new ProductPublished(
            PRODUCT, "CAF-001", "Café de Colombia", new BigDecimal("18.90"), "EUR", "seller-ana"));

    ILoggingEvent line = onlyLine();
    assertThat(line.getLevel()).isEqualTo(Level.INFO);
    assertThat(line.getFormattedMessage()).isEqualTo("Product CAF-001 published at 18.90 EUR");
  }

  @Test
  void aPriceChangeIsLoggedWithBothPrices() {
    events.on(
        new ProductPriceChanged(PRODUCT, new BigDecimal("18.90"), new BigDecimal("16.50"), "EUR"));

    ILoggingEvent line = onlyLine();
    assertThat(line.getLevel()).isEqualTo(Level.INFO);
    assertThat(line.getFormattedMessage())
        .isEqualTo("Product " + PRODUCT + " price changed from 18.90 to 16.50");
  }

  @Test
  void aWithdrawalIsLoggedWithItsReason() {
    events.on(new ProductDiscontinued(PRODUCT, "Out of production"));

    ILoggingEvent line = onlyLine();
    assertThat(line.getLevel()).isEqualTo(Level.INFO);
    assertThat(line.getFormattedMessage())
        .isEqualTo("Product " + PRODUCT + " discontinued: Out of production");
  }
}
