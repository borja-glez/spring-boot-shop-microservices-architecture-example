package com.borjaglez.shop.eskit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.shop.eskit.fixtures.CounterIncremented;

/** A native image cannot scan the classpath: the event types come from an index written by AOT. */
class EventTypeIndexTest {

  private static final String FIXTURES = "com.borjaglez.shop.eskit.fixtures";
  private static final String WIRE_NAME = "shop.eskit-test.1.event.counter.counter-incremented";

  @Test
  void theRegistryReadsTheIndexInsteadOfScanning(@TempDir Path dir) throws IOException {
    Path index = dir.resolve(EventTypeRegistry.INDEX);
    Files.createDirectories(index.getParent());
    Files.writeString(
        index,
        CounterIncremented.class.getName() + "\n" + "com.elsewhere.Ignored\n",
        StandardCharsets.UTF_8);

    try (URLClassLoader loader =
        new URLClassLoader(new URL[] {dir.toUri().toURL()}, getClass().getClassLoader())) {
      EventTypeRegistry registry =
          EventTypeRegistry.scanning(
              List.of(FIXTURES), new DefaultMessageNamingStrategy("shop"), loader);

      assertThat(registry.typeOf(WIRE_NAME)).isEqualTo(CounterIncremented.class);
    }
  }

  private static DefaultListableBeanFactory beanFactory(boolean withRegistry) {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test", Map.of(EventTypeIndexAotProcessor.PACKAGES_PROPERTY, FIXTURES)));
    beanFactory.registerSingleton("environment", environment);
    if (withRegistry) {
      beanFactory.registerBeanDefinition(
          "eventTypeRegistry", new RootBeanDefinition(EventTypeRegistry.class));
    }
    return beanFactory;
  }

  @Test
  void aotWritesTheIndexAndTheHintsToReadIt() throws IOException {
    BeanFactoryInitializationAotContribution contribution =
        new EventTypeIndexAotProcessor().processAheadOfTime(beanFactory(true));
    InMemoryGeneratedFiles files = new InMemoryGeneratedFiles();
    RuntimeHints hints = new RuntimeHints();
    GenerationContext context = mock(GenerationContext.class);
    when(context.getGeneratedFiles()).thenReturn(files);
    when(context.getRuntimeHints()).thenReturn(hints);

    contribution.applyTo(context, mock(BeanFactoryInitializationCode.class));

    assertThat(
            files.getGeneratedFileContent(
                InMemoryGeneratedFiles.Kind.RESOURCE, EventTypeRegistry.INDEX))
        .contains(CounterIncremented.class.getName());
    assertThat(RuntimeHintsPredicates.resource().forResource(EventTypeRegistry.INDEX))
        .accepts(hints);
    assertThat(RuntimeHintsPredicates.reflection().onType(CounterIncremented.class)).accepts(hints);
  }

  @Test
  void applicationsWithoutAnEventStoreGetNoIndex() {
    assertThat(new EventTypeIndexAotProcessor().processAheadOfTime(beanFactory(false))).isNull();
  }
}
