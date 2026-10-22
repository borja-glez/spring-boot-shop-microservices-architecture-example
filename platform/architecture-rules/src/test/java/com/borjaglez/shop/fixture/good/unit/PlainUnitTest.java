package com.borjaglez.shop.fixture.good.unit;

import org.junit.jupiter.api.Test;

/** Abstract so that the build does not run it: it only exists to be analyzed. */
abstract class PlainUnitTest {

  @Test
  void adds() {
    int sum = 1 + 1;
  }
}
