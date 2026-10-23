package com.borjaglez.shop.eskit;

import java.util.Arrays;
import java.util.List;

import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import com.borjaglez.shop.kernel.domain.DomainEvent;

/**
 * Registers binding hints for the domain events of every {@link DomainEventTypes} bean: a native
 * image can only serialize and read back the classes it was told about at build time, and the
 * domain events are listed by the services, not scanned.
 */
class DomainEventTypesAotProcessor implements BeanFactoryInitializationAotProcessor {

  private final BindingReflectionHintsRegistrar bindings = new BindingReflectionHintsRegistrar();

  @Override
  public BeanFactoryInitializationAotContribution processAheadOfTime(
      ConfigurableListableBeanFactory beanFactory) {
    String[] names = beanFactory.getBeanNamesForType(DomainEventTypes.class, false, false);
    if (names.length == 0) {
      return null;
    }
    List<Class<? extends DomainEvent>> events =
        Arrays.stream(names)
            .flatMap(name -> beanFactory.getBean(name, DomainEventTypes.class).types().stream())
            .toList();
    return (generationContext, code) ->
        events.forEach(
            type ->
                bindings.registerReflectionHints(
                    generationContext.getRuntimeHints().reflection(), type));
  }
}
