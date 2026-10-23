package com.borjaglez.shop.catalog;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.borjaglez.shop.catalog.application.port.out.StockLevels;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;

/**
 * The catalog as it runs: the whole application, started through its {@code main} method on a
 * random port, with PostgreSQL, Kafka and RabbitMQ in containers. Nothing of the catalog is
 * replaced. Its neighbours are: {@link InventoryStandIn}, which answers the stock questions over
 * RabbitMQ as inventory-service does, and {@link EventsTopic}, which reads the events topic as the
 * other services do. {@link StockQuestions} watches the questions on their way out.
 *
 * <p>Every test class annotated with it shares one cached application context, and so one database
 * and one set of brokers: a test changes only the products it creates ({@link CatalogClient}) and
 * looks only for its own events.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class,
  CatalogComponentTest.Neighbours.class
})
public @interface CatalogComponentTest {

  /** What the catalog meets around it in the platform, as the tests see it. */
  @TestConfiguration(proxyBeanMethods = false)
  @Import(InventoryStandIn.class)
  class Neighbours {

    @Bean
    EventsTopic eventsTopic(
        KafkaConnectionDetails kafka, MessageSerializer serializer, MessageNamingStrategy naming) {
      return new EventsTopic(kafka.getConsumer().getBootstrapServers(), serializer, naming);
    }

    /** Wraps the catalog's own RabbitMQ adapter, which still sends every question. */
    @Bean
    @Primary
    StockQuestions stockQuestions(@Qualifier("stockLevels") StockLevels inventory) {
      return new StockQuestions(inventory);
    }
  }
}
