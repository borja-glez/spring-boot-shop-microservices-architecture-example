package com.borjaglez.shop.eskit;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * What native images of the services need beyond Spring's own hints.
 *
 * <ul>
 *   <li>Native images only contain the resources registered at build time. Spring Boot registers
 *       Flyway's default location, {@code db/migration}, but not {@code db/eskit}: without it a
 *       native service finds none of the es-kit migrations and Flyway fails validation.
 *   <li>With {@code default_batch_fetch_size} on PostgreSQL, Hibernate 7 loads batches with one
 *       array parameter and creates the array of the id type reflectively ({@code ArrayJavaType});
 *       neither Hibernate nor Spring registers it, and GraalVM 25 refuses.
 *   <li>When Flyway cannot connect it turns the {@code SQLException} into a specific one by calling
 *       a static method of each candidate class reflectively. Without hints that call fails and
 *       hides the real error (the database is not reachable yet) behind a {@code
 *       MissingReflectionRegistrationError}.
 * </ul>
 */
class EsKitRuntimeHints implements RuntimeHintsRegistrar {

  static final String MIGRATIONS = "db/eskit/**";

  /** Id types of the entities; composite ids are not batch-loaded with arrays. */
  static final List<Class<?>> ID_ARRAYS = List.of(UUID[].class, String[].class, Long[].class);

  static final List<String> FLYWAY_SQL_EXCEPTIONS =
      List.of(
          "org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlServerUntrustedCertificateSqlException",
          "org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlNoIntegratedAuthException",
          "org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlNoDriversForInteractiveAuthException");

  @Override
  public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
    hints.resources().registerPattern(MIGRATIONS);
    ID_ARRAYS.forEach(type -> hints.reflection().registerType(type));
    FLYWAY_SQL_EXCEPTIONS.forEach(
        name ->
            hints
                .reflection()
                .registerType(
                    TypeReference.of(name),
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
  }
}
