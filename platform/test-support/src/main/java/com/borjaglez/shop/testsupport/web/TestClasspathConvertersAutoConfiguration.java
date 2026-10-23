package com.borjaglez.shop.testsupport.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.HttpMessageConverter;

/**
 * Keeps the test applications negotiating like the services. The contract test libraries (the
 * OpenAPI parser behind {@code OpenApiContract} and the AsyncAPI verifier) bring Jackson 2's YAML
 * module to every test classpath, and Spring registers a YAML message converter whenever that
 * module is present, so a test would get YAML for {@code Accept: application/yaml}, which the
 * services do not offer. That converter is removed. Registered for {@code @SpringBootTest} and for
 * the {@code @WebMvcTest} slice.
 */
@AutoConfiguration
@ConditionalOnClass(ServerHttpMessageConvertersCustomizer.class)
public class TestClasspathConvertersAutoConfiguration {

  /** Named, not referenced: the class is deprecated in Spring Framework 7. */
  static final String JACKSON_2_YAML_CONVERTER =
      "org.springframework.http.converter.yaml.MappingJackson2YamlHttpMessageConverter";

  @Bean
  ServerHttpMessageConvertersCustomizer withoutJackson2YamlConverter() {
    return builder ->
        builder.configureMessageConvertersList(
            converters ->
                converters.removeIf(TestClasspathConvertersAutoConfiguration::isJackson2Yaml));
  }

  static boolean isJackson2Yaml(HttpMessageConverter<?> converter) {
    return converter.getClass().getName().equals(JACKSON_2_YAML_CONVERTER);
  }
}
