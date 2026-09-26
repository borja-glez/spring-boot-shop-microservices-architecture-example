package com.borjaglez.shop.eskit;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;

/**
 * Maps event classes to their wire name (the {@code @CqrsMessage} name, as given by the CQRS naming
 * strategy) and back. The store keeps names, never class names, so classes can be moved or renamed
 * without rewriting history.
 */
public final class EventTypeRegistry {

  private final Map<String, Class<? extends Event>> typesByName = new HashMap<>();
  private final MessageNamingStrategy naming;

  private EventTypeRegistry(MessageNamingStrategy naming) {
    this.naming = naming;
  }

  /**
   * Where AOT processing lists the event classes it found ({@link EventTypeIndexAotProcessor}). A
   * native image cannot scan the classpath, so it reads this index instead.
   */
  static final String INDEX = "META-INF/shop/event-types.idx";

  /**
   * Registers every {@code @CqrsMessage} event found under the given packages: from the index
   * written at build time when there is one, otherwise by scanning the classpath.
   */
  public static EventTypeRegistry scanning(
      Collection<String> basePackages, MessageNamingStrategy naming) {
    return scanning(basePackages, naming, EventTypeRegistry.class.getClassLoader());
  }

  static EventTypeRegistry scanning(
      Collection<String> basePackages, MessageNamingStrategy naming, ClassLoader classLoader) {
    EventTypeRegistry registry = new EventTypeRegistry(naming);
    List<String> classNames =
        indexed(basePackages, classLoader).orElseGet(() -> scan(basePackages));
    for (String className : classNames) {
      Class<?> type = ClassUtils.resolveClassName(className, classLoader);
      if (Event.class.isAssignableFrom(type)) {
        registry.register(type.asSubclass(Event.class));
      }
    }
    return registry;
  }

  /** The {@code @CqrsMessage} classes under the given packages, found on the classpath. */
  static List<String> scan(Collection<String> basePackages) {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(CqrsMessage.class));
    List<String> classNames = new ArrayList<>();
    for (String basePackage : basePackages) {
      for (var candidate : scanner.findCandidateComponents(basePackage)) {
        classNames.add(candidate.getBeanClassName());
      }
    }
    return classNames;
  }

  private static Optional<List<String>> indexed(
      Collection<String> basePackages, ClassLoader classLoader) {
    try (InputStream in = classLoader.getResourceAsStream(INDEX)) {
      if (in == null) {
        return Optional.empty();
      }
      String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return Optional.of(
          content
              .lines()
              .map(String::strip)
              .filter(name -> basePackages.stream().anyMatch(p -> name.startsWith(p + ".")))
              .toList());
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + INDEX, e);
    }
  }

  public void register(Class<? extends Event> type) {
    String name = nameOf(type);
    Class<? extends Event> previous = typesByName.putIfAbsent(name, type);
    if (previous != null && previous != type) {
      throw new IllegalStateException(
          "Wire name " + name + " is used by " + previous.getName() + " and " + type.getName());
    }
  }

  public String nameOf(Class<? extends Event> type) {
    if (!type.isAnnotationPresent(CqrsMessage.class)) {
      throw new IllegalArgumentException(
          type.getName() + " must be annotated with @CqrsMessage to be stored");
    }
    return naming.eventName(type);
  }

  public Class<? extends Event> typeOf(String name) {
    Class<? extends Event> type = typesByName.get(name);
    if (type == null) {
      throw new IllegalArgumentException("Unknown event type " + name);
    }
    return type;
  }
}
