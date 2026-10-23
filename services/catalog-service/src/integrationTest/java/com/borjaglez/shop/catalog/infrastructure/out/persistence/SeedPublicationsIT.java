package com.borjaglez.shop.catalog.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.jdbc.outbox.OutboxContextCodec;
import com.borjaglez.cqrs.jdbc.outbox.OutboxEventTypeResolver;
import com.borjaglez.cqrs.jdbc.outbox.OutboxRelay;
import com.borjaglez.cqrs.jdbc.outbox.OutboxStore;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.borjaglez.shop.contracts.catalog.ProductPublished;

/**
 * The seed products on sale are announced through the outbox of spring-boot-cqrs: {@code V1001}
 * records a {@code ProductPublished} per product in {@code event_store}, and {@code V1002} hands
 * each one to {@code cqrs_outbox} under the same event id, where the relay reads it like any other
 * integration event. The migrations ran on a new database when the context started.
 */
@CatalogPersistenceTest
class SeedPublicationsIT {

  private static final int SEEDED_ACTIVE = 62;
  private static final String PUBLISHED = "shop.catalog.1.event.product.product-published";
  private static final String HAND_OVER =
      "db/migration/V1002__hand_seed_publications_to_the_outbox.sql";

  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;
  @Autowired MessageSerializer serializer;
  @Autowired OutboxStore outbox;
  @Autowired OutboxEventTypeResolver eventTypes;
  @Autowired OutboxContextCodec contexts;
  @Autowired PlatformTransactionManager transactionManager;

  /** The seed publications in the event store, each with its product and its outbox rows. */
  private List<Map<String, Object>> seedPublications() {
    return jdbc.queryForList(
        """
        select p.id as product_id, p.status, e.event_id::text as event_id, e.published_at,
               (select count(*) from cqrs_outbox o where o.event_id = e.event_id::text)
                   as outbox_rows
        from event_store e
        join product p on p.id::text = e.stream_id
        where e.stream_type = 'product'
          and e.event_type = ?
          and e.metadata ->> 'correlationId' = 'catalog-seed'
        """,
        PUBLISHED);
  }

  /** The outbox rows of the seed publications, each with the product it announces. */
  private List<Map<String, Object>> seedOutboxRows() {
    return jdbc.queryForList(
        """
        select o.event_id, o.event_name, o.event_class, o.payload, o.context,
               p.id as product_id, p.sku, p.name, p.price_amount, p.price_currency, p.seller_id,
               p.published_at
        from cqrs_outbox o
        join event_store e on e.event_id::text = o.event_id
        join product p on p.id::text = e.stream_id
        where e.metadata ->> 'correlationId' = 'catalog-seed'
        order by o.id
        """);
  }

  private long outboxSize() {
    return jdbc.queryForObject("select count(*) from cqrs_outbox", Long.class);
  }

  @Test
  void everySeedProductOnSaleIsHandedToTheOutboxOnce() {
    List<Map<String, Object>> publications = seedPublications();

    assertThat(publications).hasSize(SEEDED_ACTIVE);
    assertThat(publications).extracting(row -> row.get("product_id")).doesNotHaveDuplicates();
    assertThat(publications)
        .allSatisfy(
            row -> {
              assertThat(row.get("status")).isEqualTo("ACTIVE");
              assertThat(row.get("outbox_rows")).isEqualTo(1L);
            });
    assertThat(
            jdbc.queryForObject(
                """
                select count(*) from product p
                where p.status = 'ACTIVE'
                  and not exists (select 1 from event_store e
                                  where e.stream_type = 'product'
                                    and e.stream_id = p.id::text
                                    and e.event_type in (?, 'product-published.v1'))
                """,
                Long.class,
                PUBLISHED))
        .as("products on sale without a publication")
        .isZero();
  }

  @Test
  void noSeedPublicationIsLeftPending() {
    assertThat(seedPublications())
        .isNotEmpty()
        .allSatisfy(row -> assertThat(row.get("published_at")).isNotNull());
  }

  @Test
  void eachPayloadIsTheProductPublishedOfItsProduct() {
    List<Map<String, Object>> rows = seedOutboxRows();

    assertThat(rows).hasSize(SEEDED_ACTIVE);
    for (Map<String, Object> row : rows) {
      assertThat(row.get("event_name")).isEqualTo(PUBLISHED);
      assertThat(row.get("context")).isNull();
      assertThat(read(row))
          .isInstanceOfSatisfying(
              ProductPublished.class,
              event -> {
                assertThat(event.getEventId()).isEqualTo(row.get("event_id"));
                assertThat(event.getOccurredOn())
                    .isEqualTo(((Timestamp) row.get("published_at")).toInstant());
                assertThat(event.getProductId()).isEqualTo((UUID) row.get("product_id"));
                assertThat(event.getSku()).isEqualTo(row.get("sku"));
                assertThat(event.getName()).isEqualTo(row.get("name"));
                assertThat(event.getPrice())
                    .isEqualByComparingTo((BigDecimal) row.get("price_amount"));
                assertThat(event.getCurrency()).isEqualTo(row.get("price_currency"));
                assertThat(event.getSellerId()).isEqualTo(row.get("seller_id"));
              });
    }
  }

  /** Reads a row as the relay does: the class stored with it, and the application's serializer. */
  private Event read(Map<String, Object> row) {
    try {
      Class<? extends Event> type =
          Class.forName((String) row.get("event_class")).asSubclass(Event.class);
      return serializer.deserialize((byte[]) row.get("payload"), type);
    } catch (ClassNotFoundException e) {
      throw new AssertionError("Unknown event class " + row.get("event_class"), e);
    }
  }

  @Test
  void theRelayPublishesEverySeedProduct() {
    RecordingEventBus kafka = new RecordingEventBus();
    OutboxRelay relay =
        new OutboxRelay(
            outbox, eventTypes, serializer, contexts, kafka, transactionManager, 500, 1);
    Set<String> seedEventIds =
        seedPublications().stream()
            .map(row -> (String) row.get("event_id"))
            .collect(Collectors.toSet());

    // Joins the test's transaction, which rolls back: the rows stay pending for the other tests.
    relay.relayBatch();

    assertThat(kafka.published)
        .filteredOn(event -> seedEventIds.contains(event.getEventId()))
        .hasSize(SEEDED_ACTIVE)
        .allSatisfy(event -> assertThat(event).isInstanceOf(ProductPublished.class));
  }

  @Test
  void handingTheSeedOverAgainChangesNothing() {
    long before = outboxSize();

    new ResourceDatabasePopulator(new ClassPathResource(HAND_OVER)).execute(dataSource);

    assertThat(outboxSize()).isEqualTo(before);
    assertThat(seedPublications())
        .allSatisfy(row -> assertThat(row.get("outbox_rows")).isEqualTo(1L));
  }

  /** The Kafka event bus as the relay sees it, recording what it publishes. */
  private static final class RecordingEventBus implements EventBus {

    private final List<Event> published = new ArrayList<>();

    @Override
    public void publish(Event event) {
      published.add(event);
    }

    @Override
    public void publish(List<Event> events) {
      published.addAll(events);
    }
  }
}
