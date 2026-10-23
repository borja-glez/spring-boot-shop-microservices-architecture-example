package com.borjaglez.shop.catalog.infrastructure.contract;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.asyncapi.AsyncApiVerifier;
import com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration;
import com.borjaglez.cqrs.autoconfigure.CqrsSerializationAutoConfiguration;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.borjaglez.shop.contracts.catalog.ProductDiscontinued;
import com.borjaglez.shop.contracts.catalog.ProductPriceChanged;
import com.borjaglez.shop.contracts.catalog.ProductPublished;
import com.borjaglez.shop.contracts.inventory.GetStockLevels;
import com.borjaglez.shop.contracts.inventory.StockLevel;
import com.borjaglez.shop.contracts.inventory.StockLevels;
import com.borjaglez.shop.testsupport.contract.AsyncApiContracts;

/**
 * The messages the catalog publishes, reads back and asks for, serialized by the application's own
 * {@link MessageSerializer} and checked against {@code src/main/asyncapi/catalog.yaml}. No database
 * and no broker: only the serialization of spring-boot-cqrs is started.
 */
@SpringBootTest(
    classes = CatalogMessageContractsIT.Serialization.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "cqrs.naming.prefix=shop")
class CatalogMessageContractsIT {

  private static final Path CONTRACT = Path.of("src/main/asyncapi/catalog.yaml");

  private static final UUID PRODUCT_ID = UUID.fromString("0b5d4e0a-6f0e-4a57-9c52-3f1d6b1c9a10");

  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration({
    JacksonAutoConfiguration.class,
    CqrsAutoConfiguration.class,
    CqrsSerializationAutoConfiguration.class
  })
  static class Serialization {}

  @Autowired MessageSerializer serializer;
  @Autowired MessageNamingStrategy naming;

  /** The payload V1001 and V1002 build in SQL for the seed products, field for field. */
  private static final String SEED_PAYLOAD =
      """
      {"eventId":"6f1c1c0e-52f5-4a43-a0a4-1d6d4f1b3f01","occurredOn":"2025-01-15T09:30:00.123456Z",
       "productId":"0b5d4e0a-6f0e-4a57-9c52-3f1d6b1c9a10","sku":"KB-001","name":"Mechanical keyboard",
       "price":89.90,"currency":"EUR","sellerId":"s1"}
      """;

  private AsyncApiVerifier verifier() {
    return AsyncApiContracts.verifier(CONTRACT, serializer, naming)
        .sample(
            new ProductPublished(
                PRODUCT_ID, "KB-001", "Mechanical keyboard", new BigDecimal("89.90"), "EUR", "s1"))
        .sample(
            serializer.deserialize(
                SEED_PAYLOAD.getBytes(StandardCharsets.UTF_8), ProductPublished.class))
        .sample(
            new ProductPriceChanged(
                PRODUCT_ID, new BigDecimal("89.90"), new BigDecimal("79.00"), "EUR"))
        .sample(new ProductDiscontinued(PRODUCT_ID, "Superseded by the 2025 model"))
        .sample(new GetStockLevels(List.of(PRODUCT_ID)))
        .sample("inventory.stock-levels", new StockLevels(List.of(new StockLevel(PRODUCT_ID, 7))));
  }

  @Test
  void payloadsMatchTheContract() {
    verifier().verifyPayloads();
  }

  @Test
  void everyMessageOfTheContractIsSampled() {
    verifier().verifyCoverage();
  }

  @Test
  void payloadSchemasAreClosed() {
    verifier().verifyClosedSchemas();
  }
}
