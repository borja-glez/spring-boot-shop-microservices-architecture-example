package com.borjaglez.shop.support.autoconfigure;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import com.borjaglez.shop.support.web.problem.MappedProblem.FieldViolation;

/**
 * What native images of the services need to render problems.
 *
 * <p>Spring registers the types that controllers read and return, but not the values a problem
 * carries in its extra members. The {@code errors} member holds {@link FieldViolation} records,
 * whose components Jackson reads reflectively: without these hints a native service cannot write a
 * {@code validation-failed} problem and answers with Spring Boot's default error body instead.
 */
class ShopWebRuntimeHints implements RuntimeHintsRegistrar {

  private final BindingReflectionHintsRegistrar bindings = new BindingReflectionHintsRegistrar();

  @Override
  public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
    bindings.registerReflectionHints(hints.reflection(), FieldViolation.class);
  }
}
