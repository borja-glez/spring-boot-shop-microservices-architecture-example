package com.borjaglez.shop.eskit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.io.support.SpringFactoriesLoader;

import com.borjaglez.shop.eskit.fixtures.TallyEvent;

/** A native image can only read the domain events it was told about at build time. */
class DomainEventTypesAotProcessorTest {

  private static DefaultListableBeanFactory beanFactory(DomainEventTypes... types) {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    for (int i = 0; i < types.length; i++) {
      DomainEventTypes registry = types[i];
      beanFactory.registerBeanDefinition(
          "domainEventTypes" + i, new RootBeanDefinition(DomainEventTypes.class, () -> registry));
    }
    return beanFactory;
  }

  private static RuntimeHints contribute(BeanFactoryInitializationAotContribution contribution) {
    RuntimeHints hints = new RuntimeHints();
    GenerationContext context = mock(GenerationContext.class);
    when(context.getRuntimeHints()).thenReturn(hints);
    contribution.applyTo(context, mock(BeanFactoryInitializationCode.class));
    return hints;
  }

  @Test
  void registersBindingHintsForTheEventsOfEveryRegistry() {
    BeanFactoryInitializationAotContribution contribution =
        new DomainEventTypesAotProcessor()
            .processAheadOfTime(
                beanFactory(
                    DomainEventTypes.builder()
                        .register("tally-opened", TallyEvent.Opened.class)
                        .build(),
                    DomainEventTypes.builder()
                        .register("tally-incremented", TallyEvent.Incremented.class)
                        .build()));

    RuntimeHints hints = contribute(contribution);

    assertThat(RuntimeHintsPredicates.reflection().onType(TallyEvent.Opened.class)).accepts(hints);
    assertThat(RuntimeHintsPredicates.reflection().onType(TallyEvent.Incremented.class))
        .accepts(hints);
  }

  @Test
  void applicationsWithoutDomainEventsGetNoHints() {
    assertThat(new DomainEventTypesAotProcessor().processAheadOfTime(beanFactory())).isNull();
  }

  @Test
  void runsDuringAotProcessing() {
    assertThat(
            SpringFactoriesLoader.forResourceLocation("META-INF/spring/aot.factories")
                .load(BeanFactoryInitializationAotProcessor.class))
        .hasAtLeastOneElementOfType(DomainEventTypesAotProcessor.class);
  }
}
