package com.borjaglez.shop.support.chaos;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import com.borjaglez.shop.support.error.BusinessRuleViolationException;

/**
 * One fault the demo can inject: a switch (0 off, 1 on) or a delay in milliseconds. Every fault
 * starts off; only the chaos endpoint, which answers when {@code shop.chaos.enabled=true}, turns
 * them on.
 */
public final class ChaosFault {

  /** Longest delay accepted: longer than any timeout of the platform, short enough to recover. */
  public static final long MAX_DELAY_MILLIS = 60_000;

  public enum Kind {
    TOGGLE,
    DELAY
  }

  private final String key;
  private final String description;
  private final Kind kind;
  private final AtomicLong value = new AtomicLong();

  ChaosFault(String key, String description, Kind kind) {
    this.key = key;
    this.description = description;
    this.kind = kind;
  }

  public String key() {
    return key;
  }

  public String description() {
    return description;
  }

  public Kind kind() {
    return kind;
  }

  public long value() {
    return value.get();
  }

  public boolean active() {
    return value.get() > 0;
  }

  public Duration delay() {
    return Duration.ofMillis(kind == Kind.DELAY ? value.get() : 0);
  }

  void set(long newValue) {
    long max = kind == Kind.TOGGLE ? 1 : MAX_DELAY_MILLIS;
    if (newValue < 0 || newValue > max) {
      throw new BusinessRuleViolationException(
          "chaos-value-out-of-range",
          "Fault " + key + " accepts values from 0 to " + max + ", not " + newValue);
    }
    value.set(newValue);
  }
}
