package com.borjaglez.shop.support.chaos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChaosExceptionTest {

  @Test
  void keepsItsStackTraceByDefault() {
    assertThat(new ChaosException("Chaos: fail").getStackTrace()).isNotEmpty();
  }

  @Test
  void aFaultThatRepeatsOnEveryRunCanLeaveTheStackTraceOut() {
    var exception = new ChaosException("Chaos: paused", false);

    assertThat(exception).hasMessage("Chaos: paused").hasNoCause();
    assertThat(exception.getStackTrace()).isEmpty();
  }
}
