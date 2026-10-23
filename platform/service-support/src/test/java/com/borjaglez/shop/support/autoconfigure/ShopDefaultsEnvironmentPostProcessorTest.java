package com.borjaglez.shop.support.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.StandardEnvironment;

class ShopDefaultsEnvironmentPostProcessorTest {

  @Test
  void addsTheDefaultsOnlyOnce() {
    var processor = new ShopDefaultsEnvironmentPostProcessor();
    var environment = new StandardEnvironment();
    var application = new SpringApplication();

    processor.postProcessEnvironment(environment, application);
    int sources = environment.getPropertySources().size();
    processor.postProcessEnvironment(environment, application);

    assertThat(environment.getPropertySources()).hasSize(sources);
    assertThat(environment.getProperty("logging.pattern.correlation")).contains("correlationId");
  }
}
