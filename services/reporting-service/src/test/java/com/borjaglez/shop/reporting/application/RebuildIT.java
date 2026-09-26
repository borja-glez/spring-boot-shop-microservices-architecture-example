package com.borjaglez.shop.reporting.application;

import static com.borjaglez.shop.reporting.ReportingTestEvents.placed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.kafka.KafkaEventBus;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.contracts.orders.OrderConfirmed;
import com.borjaglez.shop.reporting.application.query.Reports.DailySales;
import com.borjaglez.shop.reporting.application.query.Reports.SalesByDayQuery;
import com.borjaglez.shop.reporting.application.rebuild.RebuildService;
import com.borjaglez.shop.reporting.domain.ReportOrder;
import com.borjaglez.shop.reporting.domain.ReportOrderRepository;
import com.borjaglez.shop.support.error.ConflictException;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.SpecificationQueryBuilder;

/** A rebuild through a real Kafka: clear, read the topic from offset 0, same numbers. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({PostgresTestConfiguration.class, KafkaTestConfiguration.class})
class RebuildIT {

  private static final Duration PATIENCE = Duration.ofSeconds(60);
  private static final String DAY = "2026-02-01";

  @Autowired KafkaEventBus kafka;
  @Autowired RebuildService rebuilds;
  @Autowired QueryBus queries;
  @Autowired ReportOrderRepository orders;

  private List<DailySales> sales() {
    return queries.ask(
        new SalesByDayQuery(
            SpecificationQueryBuilder.forEntity(ReportOrder.class)
                .where("placedDay", Operators.EQUALS, LocalDate.parse(DAY))
                .build()));
  }

  private void publishConfirmedOrder(int units) {
    UUID orderId = UUID.randomUUID();
    kafka.publish(placed(orderId, "ana", "RB-A", units, "2.00", DAY));
    kafka.publish(new OrderConfirmed(orderId, UUID.randomUUID()));
  }

  @Test
  void aRebuildGivesTheSameNumbersAndMissesNothing() {
    publishConfirmedOrder(1);
    publishConfirmedOrder(2);
    await()
        .atMost(PATIENCE)
        .untilAsserted(
            () -> assertThat(sales()).singleElement().extracting(DailySales::orders).isEqualTo(2L));

    rebuilds.rebuild();
    // Published after the rewind: read with the replay, not lost.
    publishConfirmedOrder(3);

    await()
        .atMost(PATIENCE)
        .untilAsserted(
            () ->
                assertThat(sales())
                    .singleElement()
                    .satisfies(
                        s -> {
                          assertThat(s.orders()).isEqualTo(3);
                          assertThat(s.revenue()).isEqualByComparingTo("12.00");
                        }));
    assertThat(rebuilds.status().running()).isFalse();
    assertThat(rebuilds.status().finishedAt()).isNotNull();
    assertThat(rebuilds.status().error()).isNull();
  }

  @Test
  void onlyOneRebuildRunsAtATime() throws Exception {
    CompletableFuture<?> first = CompletableFuture.runAsync(rebuilds::rebuild);
    await().atMost(PATIENCE).until(() -> rebuilds.status().running() || first.isDone());

    if (!first.isDone()) {
      assertThatThrownBy(() -> rebuilds.rebuild())
          .isInstanceOf(ConflictException.class)
          .hasFieldOrPropertyWithValue("code", "rebuild-in-progress");
    }
    first.get();
  }
}
