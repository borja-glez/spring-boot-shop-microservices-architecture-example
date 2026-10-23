package com.borjaglez.shop.eskit;

import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.borjaglez.shop.eskit.fixtures.CounterIncremented;
import com.borjaglez.shop.eskit.fixtures.TallyEvent;

/**
 * What a service whose aggregates emit domain events declares: their stored names, and the
 * integration events each one is published as. Opening a tally publishes nothing; an increment is
 * published as a {@link CounterIncremented}.
 */
@TestConfiguration(proxyBeanMethods = false)
class DomainEventsTestConfiguration {

  static final String COUNTER_INCREMENTED = "shop.eskit-test.1.event.counter.counter-incremented";

  @Bean
  static DomainEventTypes domainEventTypes() {
    return DomainEventTypes.builder()
        .register("tally-opened", TallyEvent.Opened.class)
        .register("tally-incremented", TallyEvent.Incremented.class)
        .build();
  }

  @Bean
  static IntegrationEventTranslator integrationEventTranslator() {
    return event ->
        switch (event) {
          case TallyEvent.Incremented incremented ->
              List.of(new CounterIncremented(incremented.tallyId(), incremented.amount()));
          default -> List.of();
        };
  }
}
