package com.borjaglez.shop.eskit;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.ClassUtils;

/**
 * Scans the event packages at build time and writes the classes it finds to {@link
 * EventTypeRegistry#INDEX}: a native image cannot scan the classpath, and without the index the
 * event store would not know how to read its own events ("Unknown event type").
 */
class EventTypeIndexAotProcessor implements BeanFactoryInitializationAotProcessor {

  static final String PACKAGES_PROPERTY = "shop.event-store.event-packages";
  static final String DEFAULT_PACKAGE = "com.borjaglez.shop.contracts";

  private final BindingReflectionHintsRegistrar bindings = new BindingReflectionHintsRegistrar();

  @Override
  public BeanFactoryInitializationAotContribution processAheadOfTime(
      ConfigurableListableBeanFactory beanFactory) {
    if (beanFactory.getBeanNamesForType(EventTypeRegistry.class, false, false).length == 0) {
      return null;
    }
    List<String> packages =
        Binder.get(beanFactory.getBean("environment", Environment.class))
            .bind(PACKAGES_PROPERTY, Bindable.listOf(String.class))
            .orElse(List.of(DEFAULT_PACKAGE));
    List<String> classNames = EventTypeRegistry.scan(packages);
    ClassLoader classLoader = beanFactory.getBeanClassLoader();
    return (generationContext, code) -> contribute(generationContext, classNames, classLoader);
  }

  void contribute(
      GenerationContext generationContext, List<String> classNames, ClassLoader classLoader) {
    String index = String.join("\n", classNames) + "\n";
    generationContext
        .getGeneratedFiles()
        .addResourceFile(
            EventTypeRegistry.INDEX, new ByteArrayResource(index.getBytes(StandardCharsets.UTF_8)));
    RuntimeHints hints = generationContext.getRuntimeHints();
    hints.resources().registerPattern(EventTypeRegistry.INDEX);
    classNames.forEach(
        name ->
            bindings.registerReflectionHints(
                hints.reflection(), ClassUtils.resolveClassName(name, classLoader)));
  }
}
