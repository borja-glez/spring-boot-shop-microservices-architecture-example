package com.borjaglez.shop.catalog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.shop.contracts.catalog.ProductDiscontinued;
import com.borjaglez.shop.contracts.catalog.ProductPriceChanged;
import com.borjaglez.shop.contracts.catalog.ProductPublished;

/**
 * Subscribes to the catalog events through the real event bus, so tests observe what other services
 * would receive and when (after the transaction commits).
 */
@EventHandler
public class RecordedEvents {

  private final List<Event> events = new CopyOnWriteArrayList<>();

  @HandleEvent
  public void on(ProductPublished event) {
    events.add(event);
  }

  @HandleEvent
  public void on(ProductPriceChanged event) {
    events.add(event);
  }

  @HandleEvent
  public void on(ProductDiscontinued event) {
    events.add(event);
  }

  public <E extends Event> List<E> of(Class<E> type) {
    return events.stream().filter(type::isInstance).map(type::cast).toList();
  }

  public void clear() {
    events.clear();
  }
}
