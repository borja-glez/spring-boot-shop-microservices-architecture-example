package com.borjaglez.shop.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

/** The classes the rules are checked against, and the two ways a rule is asserted. */
final class Fixtures {

  private static final String ROOT = "com.borjaglez.shop.fixture.";

  /** Two well-formed services. */
  static final JavaClasses GOOD =
      new ClassFileImporter().importPackages(ROOT + "good.orders", ROOT + "good.billing");

  /** Two services full of violations, one for each rule. */
  static final JavaClasses BAD =
      new ClassFileImporter().importPackages(ROOT + "bad.orders", ROOT + "bad.billing");

  static final JavaClasses GOOD_UNIT_TESTS =
      new ClassFileImporter().importPackages(ROOT + "good.unit");

  static final JavaClasses BAD_UNIT_TESTS =
      new ClassFileImporter().importPackages(ROOT + "bad.unit");

  private Fixtures() {}

  /** The rule holds, and it did evaluate classes: an empty evaluation fails. */
  static void assertAccepts(ArchRule rule, JavaClasses classes) {
    rule.allowEmptyShould(false).check(classes);
  }

  /** The rule fails and its report names every one of the classes. */
  static void assertRejects(ArchRule rule, JavaClasses classes, String... violators) {
    assertThatThrownBy(() -> rule.allowEmptyShould(false).check(classes))
        .isInstanceOf(AssertionError.class)
        .satisfies(
            error -> {
              for (String violator : violators) {
                assertThat(error.getMessage()).contains(violator);
              }
            });
  }
}
