package com.borjaglez.shop.reporting.infrastructure;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.AbstractMessageListenerContainer;
import org.springframework.stereotype.Component;

import com.borjaglez.shop.reporting.application.rebuild.EventReplay;

/**
 * Replays the events topic through spring-boot-cqrs's own Kafka listener container: stops it, moves
 * its consumer group back to the earliest offsets with the Kafka admin API, and starts it again.
 * The library has no replay API, so this reaches for its container bean by name.
 */
@Component
class KafkaEventReplay implements EventReplay {

  private static final Logger log = LoggerFactory.getLogger(KafkaEventReplay.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final int ATTEMPTS = 10;

  private final AbstractMessageListenerContainer<?, ?> container;
  private final KafkaAdmin admin;

  KafkaEventReplay(
      @Qualifier("cqrsKafkaEventListenerContainer")
          AbstractMessageListenerContainer<?, ?> container,
      KafkaAdmin admin) {
    this.container = container;
    this.admin = admin;
  }

  @Override
  public void pause() {
    container.stop();
  }

  @Override
  public void rewind() {
    String group = container.getGroupId();
    List<String> topics = Arrays.asList(container.getContainerProperties().getTopics());
    try (AdminClient client = AdminClient.create(admin.getConfigurationProperties())) {
      Map<TopicPartition, OffsetSpec> earliest = new HashMap<>();
      for (TopicDescription topic :
          client.describeTopics(topics).allTopicNames().get(seconds(), TimeUnit.SECONDS).values()) {
        topic
            .partitions()
            .forEach(
                p ->
                    earliest.put(
                        new TopicPartition(topic.name(), p.partition()), OffsetSpec.earliest()));
      }
      Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
      for (Map.Entry<TopicPartition, ListOffsetsResultInfo> entry :
          client.listOffsets(earliest).all().get(seconds(), TimeUnit.SECONDS).entrySet()) {
        offsets.put(entry.getKey(), new OffsetAndMetadata(entry.getValue().offset()));
      }
      alter(client, group, offsets);
      log.info("Consumer group {} rewound to the start of {}", group, topics);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while rewinding " + group, e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException("Could not rewind consumer group " + group, e);
    }
  }

  /** The broker refuses while the group still has members; they leave a moment after stop(). */
  private static void alter(
      AdminClient client, String group, Map<TopicPartition, OffsetAndMetadata> offsets)
      throws InterruptedException, ExecutionException, TimeoutException {
    for (int attempt = 1; ; attempt++) {
      try {
        client.alterConsumerGroupOffsets(group, offsets).all().get(seconds(), TimeUnit.SECONDS);
        return;
      } catch (ExecutionException e) {
        if (attempt == ATTEMPTS) {
          throw e;
        }
        log.debug("Group {} not ready to rewind ({}); retrying", group, e.getCause().toString());
        Thread.sleep(500L * attempt);
      }
    }
  }

  @Override
  public void resume() {
    container.start();
  }

  private static long seconds() {
    return TIMEOUT.toSeconds();
  }
}
