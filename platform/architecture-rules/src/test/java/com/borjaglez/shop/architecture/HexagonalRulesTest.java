package com.borjaglez.shop.architecture;

import static com.borjaglez.shop.architecture.Fixtures.BAD;
import static com.borjaglez.shop.architecture.Fixtures.GOOD;
import static com.borjaglez.shop.architecture.Fixtures.assertAccepts;
import static com.borjaglez.shop.architecture.Fixtures.assertRejects;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.importer.ClassFileImporter;

class HexagonalRulesTest {

  @Test
  void coreIsFrameworkFreeAcceptsTheGoodFixture() {
    assertAccepts(HexagonalRules.CORE_IS_FRAMEWORK_FREE, GOOD);
  }

  @Test
  void coreIsFrameworkFreeRejectsTheBadFixture() {
    assertRejects(HexagonalRules.CORE_IS_FRAMEWORK_FREE, BAD, "FrameworkDependentOrder");
  }

  @Test
  void domainOnlyKnowsItselfAndTheKernelAcceptsTheGoodFixture() {
    assertAccepts(HexagonalRules.DOMAIN_ONLY_KNOWS_ITSELF_AND_THE_KERNEL, GOOD);
  }

  @Test
  void domainOnlyKnowsItselfAndTheKernelRejectsTheBadFixture() {
    assertRejects(
        HexagonalRules.DOMAIN_ONLY_KNOWS_ITSELF_AND_THE_KERNEL,
        BAD,
        "DomainSeesAdapter",
        "DomainSeesContracts");
  }

  @Test
  void applicationDependenciesAcceptsTheGoodFixture() {
    assertAccepts(HexagonalRules.APPLICATION_DEPENDENCIES, GOOD);
  }

  @Test
  void applicationDependenciesRejectsTheBadFixture() {
    assertRejects(
        HexagonalRules.APPLICATION_DEPENDENCIES, BAD, "AdapterAwareService", "CrossServiceCaller");
  }

  @Test
  void dependenciesPointInwardsAcceptsTheGoodFixture() {
    assertAccepts(HexagonalRules.DEPENDENCIES_POINT_INWARDS, GOOD);
  }

  @Test
  void dependenciesPointInwardsRejectsTheBadFixture() {
    assertRejects(
        HexagonalRules.DEPENDENCIES_POINT_INWARDS, BAD, "DomainSeesAdapter", "AdapterAwareService");
  }

  @Test
  void inboundAndOutboundAdaptersAreSeparateAcceptsTheGoodFixture() {
    assertAccepts(HexagonalRules.INBOUND_AND_OUTBOUND_ADAPTERS_ARE_SEPARATE, GOOD);
  }

  @Test
  void inboundAndOutboundAdaptersAreSeparateRejectsTheBadFixture() {
    assertRejects(
        HexagonalRules.INBOUND_AND_OUTBOUND_ADAPTERS_ARE_SEPARATE,
        BAD,
        "BadRestAdapter",
        "ReverseAdapter");
  }

  @Test
  void adaptersAreIndependentAcceptsTheGoodFixture() {
    assertAccepts(HexagonalRules.ADAPTERS_ARE_INDEPENDENT, GOOD);
  }

  @Test
  void adaptersAreIndependentRejectsTheBadFixture() {
    assertRejects(HexagonalRules.ADAPTERS_ARE_INDEPENDENT, BAD, "BadRestAdapter", "ReverseAdapter");
  }

  @Test
  void noFieldInjectionAcceptsTheGoodFixture() {
    assertAccepts(HexagonalRules.NO_FIELD_INJECTION, GOOD);
  }

  @Test
  void noFieldInjectionRejectsTheBadFixture() {
    assertRejects(
        HexagonalRules.NO_FIELD_INJECTION,
        BAD,
        "FieldInjectedService",
        "InjectedField",
        "ValueField",
        "ResourceField",
        "PersistenceContextField");
  }

  @Test
  void beansDeclareTheirAdapterTypeAcceptsTheGoodFixture() {
    assertAccepts(HexagonalRules.BEANS_DECLARE_THEIR_ADAPTER_TYPE, GOOD);
  }

  @Test
  void beansDeclareTheirAdapterTypeRejectsTheBadFixture() {
    assertRejects(
        HexagonalRules.BEANS_DECLARE_THEIR_ADAPTER_TYPE,
        BAD,
        "PortTypedConfiguration.productRepository()",
        "PortTypedConfiguration.stockPort()");
  }

  @Test
  void servicesAreIsolatedAcceptsTheGoodFixture() {
    assertAccepts(HexagonalRules.SERVICES_ARE_ISOLATED, GOOD);
  }

  @Test
  void servicesAreIsolatedRejectsTheBadFixture() {
    assertRejects(HexagonalRules.SERVICES_ARE_ISOLATED, BAD, "CrossServiceCaller");
  }

  @Test
  void servicesAreIsolatedFailsOnAnEmptyImport() {
    assertThatThrownBy(
            () ->
                HexagonalRules.SERVICES_ARE_ISOLATED.check(new ClassFileImporter().importClasses()))
        .isInstanceOf(AssertionError.class);
  }
}
