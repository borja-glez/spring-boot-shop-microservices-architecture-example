package com.borjaglez.shop.eskit.fixtures;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@CqrsMessage(service = "eskit-test", module = "counter", name = "counter-incremented")
public class CounterIncremented extends Event {

  private String counterId;
  private int amount;
}
