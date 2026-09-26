package com.borjaglez.shop.support.autoconfigure;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.borjaglez.shop.support.web.CorrelationIdFilter;
import com.borjaglez.shop.support.web.CorrelationScope;
import com.borjaglez.shop.support.web.CurrentUserArgumentResolver;
import com.borjaglez.shop.support.web.MessageContextCorrelationScope;
import com.borjaglez.shop.support.web.ProblemDetailsExceptionHandler;
import com.borjaglez.shop.support.web.problem.DataAccessProblemMapper;
import com.borjaglez.shop.support.web.problem.DomainProblemMapper;
import com.borjaglez.shop.support.web.problem.ProblemMapper;
import com.borjaglez.shop.support.web.problem.SpecificationHttpProblemMapper;
import com.borjaglez.shop.support.web.problem.SpecificationQueryProblemMapper;
import com.borjaglez.shop.support.web.problem.UserHeaderProblemMapper;
import com.borjaglez.shop.support.web.problem.ValidationProblemMapper;

/**
 * Web conventions shared by every shop service: problem details, correlation ids and the current
 * user. Integrations with spring-boot-cqrs, specification-repository, Bean Validation and Spring
 * DAO switch on only when those libraries are on the classpath.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ShopWebAutoConfiguration {

  @Bean
  FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter(
      ObjectProvider<CorrelationScope> scopes) {
    var registration =
        new FilterRegistrationBean<>(new CorrelationIdFilter(scopes.orderedStream().toList()));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return registration;
  }

  @Bean
  @ConditionalOnMissingBean
  ProblemDetailsExceptionHandler problemDetailsExceptionHandler(
      ObjectProvider<ProblemMapper> mappers) {
    List<ProblemMapper> ordered = mappers.orderedStream().toList();
    return new ProblemDetailsExceptionHandler(ordered);
  }

  @Bean
  @Order(0)
  DomainProblemMapper domainProblemMapper() {
    return new DomainProblemMapper();
  }

  @Bean
  @Order(10)
  UserHeaderProblemMapper userHeaderProblemMapper() {
    return new UserHeaderProblemMapper();
  }

  @Bean
  WebMvcConfigurer currentUserWebMvcConfigurer() {
    var resolver = new CurrentUserArgumentResolver();
    return new WebMvcConfigurer() {
      @Override
      public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(resolver);
      }
    };
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "com.borjaglez.cqrs.context.MessageContext")
  static class CqrsIntegration {

    @Bean
    MessageContextCorrelationScope messageContextCorrelationScope() {
      return new MessageContextCorrelationScope();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.springframework.dao.OptimisticLockingFailureException")
  static class DataAccessIntegration {

    @Bean
    @Order(20)
    DataAccessProblemMapper dataAccessProblemMapper() {
      return new DataAccessProblemMapper();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "jakarta.validation.ConstraintViolationException")
  static class ValidationIntegration {

    @Bean
    @Order(30)
    ValidationProblemMapper validationProblemMapper() {
      return new ValidationProblemMapper();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "com.borjaglez.specrepository.core.DisallowedFieldException")
  static class SpecificationQueryIntegration {

    @Bean
    @Order(40)
    SpecificationQueryProblemMapper specificationQueryProblemMapper() {
      return new SpecificationQueryProblemMapper();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "com.borjaglez.specrepository.http.HttpFilterSyntaxException")
  static class SpecificationHttpIntegration {

    @Bean
    @Order(50)
    SpecificationHttpProblemMapper specificationHttpProblemMapper() {
      return new SpecificationHttpProblemMapper();
    }
  }
}
