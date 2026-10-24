package com.borjaglez.shop.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.kernel.domain.DomainEvent;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;

class LayersTest {

  private static final ClassFileImporter IMPORTER = new ClassFileImporter();

  private static JavaClass classOf(Class<?> type) {
    return IMPORTER.importClass(type);
  }

  @Test
  void locatesAClassInItsServiceAndLayer() {
    JavaClass handler =
        classOf(com.borjaglez.shop.fixture.good.orders.application.service.PlaceOrderHandler.class);

    assertThat(Layers.locate(handler))
        .contains(new Layers.Location("orders", "application.service"));
    assertThat(Layers.inLayer(handler, "application")).isTrue();
    assertThat(Layers.inLayer(handler, "app")).isFalse();
    assertThat(Layers.inLayer(handler, "domain")).isFalse();
  }

  @Test
  void aClassInTheRootOfAServiceHasAnEmptyPath() {
    JavaClass root = classOf(com.borjaglez.shop.fixture.good.unit.UnitMarker.class);

    assertThat(Layers.locate(root)).contains(new Layers.Location("unit", ""));
  }

  @Test
  void platformPackagesAreNotServices() {
    assertThat(Layers.locate(classOf(DomainEvent.class))).isEmpty();
    assertThat(Layers.locate(classOf(Layers.class))).isEmpty();
    assertThat(Layers.locate(classOf(com.borjaglez.shop.contracts.FixtureMessage.class))).isEmpty();
  }

  @Test
  void classesOutsideTheShopAreNotServices() {
    assertThat(Layers.locate(classOf(String.class))).isEmpty();
    assertThat(Layers.inLayer(classOf(String.class), "domain")).isFalse();
  }

  @Test
  void aTargetIsInTheLayerOfTheSameServiceOnly() {
    JavaClass handler =
        classOf(com.borjaglez.shop.fixture.good.orders.application.service.PlaceOrderHandler.class);
    JavaClass order = classOf(com.borjaglez.shop.fixture.good.orders.domain.Order.class);
    JavaClass invoice = classOf(com.borjaglez.shop.fixture.good.billing.domain.Invoice.class);

    assertThat(Layers.inLayerOfSameService(handler, order, "domain")).isTrue();
    assertThat(Layers.inLayerOfSameService(handler, invoice, "domain")).isFalse();
    assertThat(Layers.inLayerOfSameService(handler, order, "application")).isFalse();
    assertThat(Layers.inLayerOfSameService(handler, classOf(String.class), "domain")).isFalse();
  }

  @Test
  void anAdapterIsNamedByServiceDirectionAndName() {
    assertThat(
            Layers.adapterOf(
                classOf(
                    com.borjaglez.shop.fixture.good.orders.infrastructure.in.rest.v1.model
                        .OrderModel.class)))
        .contains("orders/in.rest");
    assertThat(
            Layers.adapterOf(
                classOf(
                    com.borjaglez.shop.fixture.good.orders.infrastructure.config.OrdersConfiguration
                        .class)))
        .isEmpty();
    assertThat(Layers.adapterOf(classOf(String.class))).isEmpty();
  }
}
