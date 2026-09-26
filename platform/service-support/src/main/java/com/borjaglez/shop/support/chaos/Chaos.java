package com.borjaglez.shop.support.chaos;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import com.borjaglez.shop.support.error.NotFoundException;

/**
 * The faults a service can inject on purpose, to show how the platform absorbs them. Components
 * register their faults at startup and check them on every call; with chaos disabled nobody can
 * turn them on, so the checks cost one volatile read.
 */
public class Chaos {

  private final Map<String, ChaosFault> faults = new ConcurrentSkipListMap<>();

  /** Registers (or returns the already registered) on/off fault. */
  public ChaosFault toggle(String key, String description) {
    return register(key, description, ChaosFault.Kind.TOGGLE);
  }

  /** Registers (or returns the already registered) delay fault, in milliseconds. */
  public ChaosFault delay(String key, String description) {
    return register(key, description, ChaosFault.Kind.DELAY);
  }

  private ChaosFault register(String key, String description, ChaosFault.Kind kind) {
    ChaosFault fault = faults.computeIfAbsent(key, k -> new ChaosFault(k, description, kind));
    if (fault.kind() != kind) {
      throw new IllegalStateException(
          "Chaos fault " + key + " is already registered as " + fault.kind());
    }
    return fault;
  }

  public List<ChaosFault> faults() {
    return List.copyOf(faults.values());
  }

  public ChaosFault set(String key, long value) {
    ChaosFault fault = faults.get(key);
    if (fault == null) {
      throw new NotFoundException("chaos-fault-not-found", "No chaos fault " + key + " here");
    }
    fault.set(value);
    return fault;
  }

  /** Turns every fault off. */
  public void reset() {
    faults.values().forEach(f -> f.set(0));
  }
}
