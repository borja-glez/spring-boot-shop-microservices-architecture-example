package com.borjaglez.shop.eskit;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.borjaglez.shop.kernel.domain.DomainEvent;

/**
 * The domain events a service stores, each with the name it is stored under. The store keeps names,
 * never class names, so event classes can be moved or renamed without rewriting history.
 *
 * <p>Every service whose aggregates emit domain events declares one bean, listing them explicitly:
 * nothing is scanned. Native images get binding hints for these classes at build time ({@link
 * DomainEventTypesAotProcessor}), which creates the bean then, so declare it with a {@code static}
 * {@code @Bean} method that needs no other bean.
 */
public final class DomainEventTypes {

  private final Map<String, Class<? extends DomainEvent>> typesByName;
  private final Map<Class<?>, String> namesByType;

  private DomainEventTypes(
      Map<String, Class<? extends DomainEvent>> typesByName, Map<Class<?>, String> namesByType) {
    this.typesByName = typesByName;
    this.namesByType = namesByType;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The name an event class is stored under. */
  public String nameOf(Class<?> type) {
    String name = namesByType.get(type);
    if (name == null) {
      throw new IllegalArgumentException(type.getName() + " is not a registered domain event");
    }
    return name;
  }

  /** The event class stored under a name. */
  public Class<? extends DomainEvent> typeOf(String name) {
    Class<? extends DomainEvent> type = typesByName.get(name);
    if (type == null) {
      throw new IllegalArgumentException("Unknown domain event type " + name);
    }
    return type;
  }

  /** The registered event classes, in registration order. */
  public Collection<Class<? extends DomainEvent>> types() {
    return List.copyOf(typesByName.values());
  }

  /** Registers each event class under its own name. */
  public static final class Builder {

    private final Map<String, Class<? extends DomainEvent>> typesByName = new LinkedHashMap<>();
    private final Map<Class<?>, String> namesByType = new HashMap<>();

    private Builder() {}

    /**
     * @throws IllegalStateException if the name or the class is already registered
     */
    public Builder register(String name, Class<? extends DomainEvent> type) {
      Class<? extends DomainEvent> registered = typesByName.get(name);
      if (registered != null) {
        throw new IllegalStateException(
            "Domain event name " + name + " is already used by " + registered.getName());
      }
      String registeredName = namesByType.get(type);
      if (registeredName != null) {
        throw new IllegalStateException(
            type.getName() + " is already registered as " + registeredName);
      }
      typesByName.put(name, type);
      namesByType.put(type, name);
      return this;
    }

    public DomainEventTypes build() {
      return new DomainEventTypes(new LinkedHashMap<>(typesByName), new HashMap<>(namesByType));
    }
  }
}
