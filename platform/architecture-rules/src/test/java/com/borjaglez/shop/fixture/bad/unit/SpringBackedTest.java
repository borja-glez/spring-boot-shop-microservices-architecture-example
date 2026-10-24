package com.borjaglez.shop.fixture.bad.unit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Abstract so that the build does not run it: it only exists to be analyzed. */
@SpringBootTest
abstract class SpringBackedTest {

  @Test
  void startsSpring() {}
}
