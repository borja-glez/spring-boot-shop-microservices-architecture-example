package com.borjaglez.shop.support.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the id of the user making the request into a {@code String} controller parameter.
 *
 * <p>The example has no authentication: the frontend lets the visitor pick a user and sends it in
 * the {@value CurrentUserArgumentResolver#HEADER} header. Replace the resolver with one backed by
 * Spring Security to make it real.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {

  /** Whether a missing header is rejected with 401 ({@code true}) or injected as {@code null}. */
  boolean required() default true;
}
