package com.borjaglez.shop.eskit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.idempotency.IdempotencyStore;
import com.borjaglez.cqrs.jdbc.outbox.OutboxEventBus;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.borjaglez.shop.eskit.fixtures.TallyEvent;

/** The domain-event beans exist only in a service that declares both of their inputs. */
class EventStoreAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(EventStoreAutoConfiguration.class))
          .withBean(StoredEventRepository.class, () -> mock(StoredEventRepository.class))
          .withBean(MessageNamingStrategy.class, () -> new DefaultMessageNamingStrategy("shop"))
          .withBean(MessageSerializer.class, () -> mock(MessageSerializer.class))
          .withBean(IdempotencyStore.class, () -> mock(IdempotencyStore.class))
          .withBean(OutboxEventBus.class, () -> mock(OutboxEventBus.class));

  private static DomainEventTypes domainEventTypes() {
    return DomainEventTypes.builder().register("tally-opened", TallyEvent.Opened.class).build();
  }

  private static IntegrationEventTranslator translator() {
    return event -> List.of();
  }

  private static EventBus kafkaEventBus() {
    return mock(EventBus.class);
  }

  @Test
  void withoutDomainEventsTheServiceKeepsTheEventStoreOnly() {
    runner
        .withBean("kafkaEventBus", EventBus.class, EventStoreAutoConfigurationTest::kafkaEventBus)
        .run(
            context -> {
              assertThat(context).hasSingleBean(EventStore.class);
              assertThat(context).doesNotHaveBean(DomainEventStore.class);
              assertThat(context).doesNotHaveBean(ChaosEventBus.class);
            });
  }

  @Test
  void domainEventTypesAloneAreNotEnough() {
    runner
        .withBean("kafkaEventBus", EventBus.class, EventStoreAutoConfigurationTest::kafkaEventBus)
        .withBean(DomainEventTypes.class, EventStoreAutoConfigurationTest::domainEventTypes)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(DomainEventStore.class);
              assertThat(context).doesNotHaveBean(ChaosEventBus.class);
            });
  }

  @Test
  void aTranslatorAloneIsNotEnough() {
    runner
        .withBean("kafkaEventBus", EventBus.class, EventStoreAutoConfigurationTest::kafkaEventBus)
        .withBean(IntegrationEventTranslator.class, EventStoreAutoConfigurationTest::translator)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(DomainEventStore.class);
              assertThat(context).doesNotHaveBean(ChaosEventBus.class);
            });
  }

  @Test
  void domainEventTypesAndATranslatorBringTheDomainEventStoreAndTheRelayBus() {
    runner
        .withBean("kafkaEventBus", EventBus.class, EventStoreAutoConfigurationTest::kafkaEventBus)
        .withBean(DomainEventTypes.class, EventStoreAutoConfigurationTest::domainEventTypes)
        .withBean(IntegrationEventTranslator.class, EventStoreAutoConfigurationTest::translator)
        .run(
            context -> {
              assertThat(context).hasSingleBean(DomainEventStore.class);
              assertThat(context).hasSingleBean(ChaosEventBus.class);
              assertThat(context).hasBean("chaosEventBus");
            });
  }

  @Test
  void domainEventsWithoutTheKafkaModuleFailWithAnExplanation() {
    runner
        .withBean(DomainEventTypes.class, EventStoreAutoConfigurationTest::domainEventTypes)
        .withBean(IntegrationEventTranslator.class, EventStoreAutoConfigurationTest::translator)
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("kafkaEventBus")
                    .hasMessageContaining("spring-boot-cqrs-kafka"));
  }
}
