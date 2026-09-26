package com.borjaglez.shop.support.web;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.slf4j.MDC;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.cqrs.command.CommandHandlerExecutionException;
import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.shop.support.error.BusinessRuleViolationException;
import com.borjaglez.shop.support.error.ConflictException;
import com.borjaglez.shop.support.error.NotFoundException;
import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.http.HttpFilterSyntaxException;
import com.borjaglez.specrepository.http.HttpUnknownOperatorException;

/** Endpoints that raise every kind of failure the support module has to translate. */
@RestController
class SampleController {

  record CreateThing(@NotBlank String name, @Positive BigDecimal price) {}

  @GetMapping("/not-found")
  void notFound() {
    throw new NotFoundException("product-not-found", "Product 42 does not exist");
  }

  @GetMapping("/conflict")
  void conflict() {
    throw new ConflictException("duplicate-sku", "SKU ABC-1 already exists");
  }

  @GetMapping("/rule")
  void rule() {
    throw new BusinessRuleViolationException("product-discontinued", "Product is discontinued");
  }

  @GetMapping("/wrapped-domain")
  void wrappedDomain() {
    throw new CommandHandlerExecutionException(
        new NotFoundException("product-not-found", "Product 42 does not exist"));
  }

  @GetMapping("/boom")
  void boom() {
    throw new IllegalStateException("secret internal detail");
  }

  @GetMapping("/illegal-argument")
  void illegalArgument() {
    throw new IllegalArgumentException("Unable to locate attribute [nonexistent]");
  }

  @GetMapping("/filter-syntax")
  void filterSyntax() {
    throw new HttpFilterSyntaxException("name:", "operator must not be empty");
  }

  @GetMapping("/filter-operator")
  void filterOperator() {
    throw new HttpUnknownOperatorException("like");
  }

  @GetMapping("/disallowed-field")
  void disallowedField() {
    throw new InvalidDataAccessApiUsageException(
        "wrapped", new DisallowedFieldException("seller.email", "filter"));
  }

  @GetMapping("/filter-value")
  void filterValue() {
    throw new InvalidDataAccessApiUsageException(
        "wrapped",
        new ConversionFailedException(
            TypeDescriptor.valueOf(String.class),
            TypeDescriptor.valueOf(BigDecimal.class),
            "abc",
            new NumberFormatException("abc")));
  }

  @GetMapping("/filter-operator-at-query-time")
  void filterOperatorAtQueryTime() {
    throw new InvalidDataAccessApiUsageException(
        "wrapped", new IllegalArgumentException("No operator handler registered for like"));
  }

  @GetMapping("/optimistic-lock")
  void optimisticLock() {
    throw new OptimisticLockingFailureException("Row was updated by another transaction");
  }

  @GetMapping("/constraint-violation")
  void constraintViolation() {
    throw new ConstraintViolationException("invalid", Set.of());
  }

  @PostMapping("/things")
  Map<String, Object> create(@Valid @RequestBody CreateThing body) {
    return Map.of("name", body.name());
  }

  @GetMapping("/whoami")
  Map<String, String> whoami(@CurrentUser String user) {
    return Map.of("user", user);
  }

  @GetMapping("/whoami-optional")
  Map<String, String> whoamiOptional(@CurrentUser(required = false) String user) {
    return Map.of("user", String.valueOf(user));
  }

  @GetMapping("/correlation")
  Map<String, String> correlation() {
    return Map.of(
        "messageContext",
        MessageContext.current().correlationId(),
        "mdc",
        String.valueOf(MDC.get(CorrelationIdFilter.MDC_KEY)));
  }

  @GetMapping("/page")
  Map<String, Integer> page(Pageable pageable) {
    return Map.of("page", pageable.getPageNumber(), "size", pageable.getPageSize());
  }
}
