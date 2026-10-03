package com.borjaglez.shop.support.web;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.slf4j.MDC;
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
import com.borjaglez.specrepository.core.InvalidFilterException;
import com.borjaglez.specrepository.core.InvalidFilterValueException;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.http.HttpFilterSyntaxException;
import com.borjaglez.specrepository.http.HttpUnknownOperatorException;
import com.borjaglez.specrepository.http.spring.FilterableQuery;

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
        new InvalidFilterValueException(
            "price.amount", "abc", BigDecimal.class, new NumberFormatException("abc")));
  }

  @GetMapping("/filter-operator-at-query-time")
  void filterOperatorAtQueryTime() {
    throw new InvalidDataAccessApiUsageException(
        "wrapped", new InvalidFilterException("name", "unknown operator 'like'"));
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

  /** Only a type for the plan: nothing is queried. */
  static class Item {}

  /** A filterable endpoint, to check the shop's limits and problems on real request parameters. */
  @GetMapping("/items")
  Map<String, Integer> items(
      @FilterableQuery(
              value = Item.class,
              filterableFields = {"id", "name"},
              sortableFields = {"name"})
          QueryPlan<Item> plan) {
    return Map.of("conditions", plan.rootCondition().conditions().size());
  }
}
