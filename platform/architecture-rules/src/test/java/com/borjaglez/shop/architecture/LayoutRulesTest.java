package com.borjaglez.shop.architecture;

import static com.borjaglez.shop.architecture.Fixtures.BAD;
import static com.borjaglez.shop.architecture.Fixtures.BAD_UNIT_TESTS;
import static com.borjaglez.shop.architecture.Fixtures.GOOD;
import static com.borjaglez.shop.architecture.Fixtures.GOOD_UNIT_TESTS;
import static com.borjaglez.shop.architecture.Fixtures.assertAccepts;
import static com.borjaglez.shop.architecture.Fixtures.assertRejects;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class LayoutRulesTest {

  @Test
  void handlersLiveInApplicationServicesAcceptsTheGoodFixture() {
    assertAccepts(LayoutRules.HANDLERS_LIVE_IN_APPLICATION_SERVICES, GOOD);
  }

  @Test
  void handlersLiveInApplicationServicesRejectsTheBadFixture() {
    assertRejects(
        LayoutRules.HANDLERS_LIVE_IN_APPLICATION_SERVICES,
        BAD,
        "RogueCommandHandler",
        "MisplacedEventHandler");
  }

  @Test
  void controllersLiveInRestAdaptersAcceptsTheGoodFixture() {
    assertAccepts(LayoutRules.CONTROLLERS_LIVE_IN_REST_ADAPTERS, GOOD);
  }

  @Test
  void controllersLiveInRestAdaptersRejectsTheBadFixture() {
    assertRejects(LayoutRules.CONTROLLERS_LIVE_IN_REST_ADAPTERS, BAD, "MisplacedController");
  }

  @Test
  void entitiesLiveInPersistenceAdaptersAcceptsTheGoodFixture() {
    assertAccepts(LayoutRules.ENTITIES_LIVE_IN_PERSISTENCE_ADAPTERS, GOOD);
  }

  @Test
  void entitiesLiveInPersistenceAdaptersRejectsTheBadFixture() {
    assertRejects(LayoutRules.ENTITIES_LIVE_IN_PERSISTENCE_ADAPTERS, BAD, "AnnotatedEntity");
  }

  @Test
  void configurationLivesInConfigAcceptsTheGoodFixture() {
    assertAccepts(LayoutRules.CONFIGURATION_LIVES_IN_CONFIG, GOOD);
  }

  @Test
  void configurationLivesInConfigRejectsTheBadFixture() {
    assertRejects(LayoutRules.CONFIGURATION_LIVES_IN_CONFIG, BAD, "MisplacedConfiguration");
  }

  @Test
  void outboundPortsAreInterfacesOrRecordsAcceptsTheGoodFixture() {
    assertAccepts(LayoutRules.OUTBOUND_PORTS_ARE_INTERFACES_OR_RECORDS, GOOD);
  }

  @Test
  void outboundPortsAreInterfacesOrRecordsRejectsTheBadFixture() {
    assertRejects(LayoutRules.OUTBOUND_PORTS_ARE_INTERFACES_OR_RECORDS, BAD, "ConcretePort");
  }

  @Test
  void unitTestsDoNotUseSpringAcceptsTheGoodFixture() {
    assertAccepts(LayoutRules.UNIT_TESTS_DO_NOT_USE_SPRING, GOOD_UNIT_TESTS);
  }

  @Test
  void unitTestsDoNotUseSpringRejectsTheBadFixture() {
    assertRejects(LayoutRules.UNIT_TESTS_DO_NOT_USE_SPRING, BAD_UNIT_TESTS, "SpringBackedTest");
  }

  @Test
  void unitTestsDoNotUseSpringIgnoresProductionClasses() {
    assertThatCode(() -> LayoutRules.UNIT_TESTS_DO_NOT_USE_SPRING.check(BAD))
        .doesNotThrowAnyException();
  }
}
