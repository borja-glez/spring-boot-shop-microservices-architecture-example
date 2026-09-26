package com.borjaglez.shop.support.chaos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.MiddlewareChain;

/**
 * Delays or fails the handling of chosen message types, by simple class name ({@code
 * shop.chaos.messages}). It sits on the local buses, so it also affects the commands that arrive
 * over RabbitMQ: the caller sees a slow service or a remote error, as with a real outage.
 */
public class MessageChaosMiddleware implements BusMiddleware {

  private record Faults(ChaosFault delay, ChaosFault fail) {}

  private final Map<String, Faults> byType = new LinkedHashMap<>();

  public MessageChaosMiddleware(Chaos chaos, List<String> messageTypes) {
    for (String type : messageTypes) {
      byType.put(
          type,
          new Faults(
              chaos.delay(type + ".delay-ms", "Waits this long before handling each " + type),
              chaos.toggle(type + ".fail", "Fails every " + type + " with a technical error")));
    }
  }

  @Override
  public Object process(Object message, MiddlewareChain chain) throws Exception {
    String type = message.getClass().getSimpleName();
    Faults faults = byType.get(type);
    if (faults != null) {
      long millis = faults.delay().delay().toMillis();
      if (millis > 0) {
        Thread.sleep(millis);
      }
      if (faults.fail().active()) {
        throw new ChaosException("Chaos: " + type + " failed on purpose");
      }
    }
    return chain.proceed(message);
  }
}
