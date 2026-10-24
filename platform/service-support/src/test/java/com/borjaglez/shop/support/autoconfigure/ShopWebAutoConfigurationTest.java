package com.borjaglez.shop.support.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import com.borjaglez.shop.support.web.ProblemDetailsExceptionHandler;
import com.borjaglez.shop.support.web.problem.UserHeaderProblemMapper;

class ShopWebAutoConfigurationTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ShopWebAutoConfiguration.class));

  @Test
  void rendersProblemsWithTheMappersOfTheService() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(ProblemDetailsExceptionHandler.class);
          assertThat(context).hasSingleBean(UserHeaderProblemMapper.class);
        });
  }

  @Test
  void aServiceCanReplaceTheProblemHandler() {
    ProblemDetailsExceptionHandler own = new ProblemDetailsExceptionHandler(List.of());
    runner
        .withBean(ProblemDetailsExceptionHandler.class, () -> own)
        .run(
            context ->
                assertThat(context.getBean(ProblemDetailsExceptionHandler.class)).isSameAs(own));
  }
}
