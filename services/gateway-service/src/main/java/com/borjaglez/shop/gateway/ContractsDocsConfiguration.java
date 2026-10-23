package com.borjaglez.shop.gateway;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the contracts page at /api/docs and /api/docs/ without redirecting, so it works behind any
 * proxy.
 */
@Configuration(proxyBeanMethods = false)
class ContractsDocsConfiguration implements WebMvcConfigurer {

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    registry.addViewController("/api/docs").setViewName("forward:/api/docs/index.html");
    registry.addViewController("/api/docs/").setViewName("forward:/api/docs/index.html");
  }
}
