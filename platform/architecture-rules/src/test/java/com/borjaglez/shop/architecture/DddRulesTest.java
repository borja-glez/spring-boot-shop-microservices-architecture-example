package com.borjaglez.shop.architecture;

import static com.borjaglez.shop.architecture.Fixtures.BAD;
import static com.borjaglez.shop.architecture.Fixtures.GOOD;
import static com.borjaglez.shop.architecture.Fixtures.assertAccepts;
import static com.borjaglez.shop.architecture.Fixtures.assertRejects;

import org.junit.jupiter.api.Test;

class DddRulesTest {

  @Test
  void domainEventsAreRecordsAcceptsTheGoodFixture() {
    assertAccepts(DddRules.DOMAIN_EVENTS_ARE_RECORDS, GOOD);
  }

  @Test
  void domainEventsAreRecordsRejectsTheBadFixture() {
    assertRejects(DddRules.DOMAIN_EVENTS_ARE_RECORDS, BAD, "ClassEvent");
  }

  @Test
  void eventFamiliesAreSealedAcceptsTheGoodFixture() {
    assertAccepts(DddRules.EVENT_FAMILIES_ARE_SEALED, GOOD);
  }

  @Test
  void eventFamiliesAreSealedRejectsTheBadFixture() {
    assertRejects(DddRules.EVENT_FAMILIES_ARE_SEALED, BAD, "OpenEventFamily");
  }

  @Test
  void noPublicSettersInTheDomainAcceptsTheGoodFixture() {
    assertAccepts(DddRules.NO_PUBLIC_SETTERS_IN_THE_DOMAIN, GOOD);
  }

  @Test
  void noPublicSettersInTheDomainRejectsTheBadFixture() {
    assertRejects(DddRules.NO_PUBLIC_SETTERS_IN_THE_DOMAIN, BAD, "MutableOrder.setStatus");
  }

  @Test
  void noPublicMutableFieldsInTheDomainAcceptsTheGoodFixture() {
    assertAccepts(DddRules.NO_PUBLIC_MUTABLE_FIELDS_IN_THE_DOMAIN, GOOD);
  }

  @Test
  void noPublicMutableFieldsInTheDomainRejectsTheBadFixture() {
    assertRejects(DddRules.NO_PUBLIC_MUTABLE_FIELDS_IN_THE_DOMAIN, BAD, "MutableOrder.status");
  }

  @Test
  void repositoriesSaveAggregateRootsAcceptsTheGoodFixture() {
    assertAccepts(DddRules.REPOSITORIES_SAVE_AGGREGATE_ROOTS, GOOD);
  }

  @Test
  void repositoriesSaveAggregateRootsRejectsTheBadFixture() {
    assertRejects(DddRules.REPOSITORIES_SAVE_AGGREGATE_ROOTS, BAD, "ProductRepository.save");
  }

  @Test
  void domainExceptionsExtendTheKernelAcceptsTheGoodFixture() {
    assertAccepts(DddRules.DOMAIN_EXCEPTIONS_EXTEND_THE_KERNEL, GOOD);
  }

  @Test
  void domainExceptionsExtendTheKernelRejectsTheBadFixture() {
    assertRejects(DddRules.DOMAIN_EXCEPTIONS_EXTEND_THE_KERNEL, BAD, "PlainException");
  }

  @Test
  void theDomainDoesNotSeeContractsAcceptsTheGoodFixture() {
    assertAccepts(DddRules.THE_DOMAIN_DOES_NOT_SEE_CONTRACTS, GOOD);
  }

  @Test
  void theDomainDoesNotSeeContractsRejectsTheBadFixture() {
    assertRejects(DddRules.THE_DOMAIN_DOES_NOT_SEE_CONTRACTS, BAD, "DomainSeesContracts");
  }
}
