package com.borjaglez.shop.fixture.good.orders.infrastructure.out.messaging;

import com.borjaglez.shop.fixture.good.orders.application.port.in.ShipOrder;

public class OrderShipments {

  public String publish(ShipOrder shipment) {
    return shipment.orderId();
  }
}
