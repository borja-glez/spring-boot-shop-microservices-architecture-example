package com.borjaglez.shop.catalog.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.specrepository.http.spring.FilterableQuery;

/**
 * The public product filters, shared by the search and its facets so both accept exactly the same
 * requests.
 *
 * <p>Private data such as the seller's email is never listed. Text searches on the name and the
 * description ignore case and accents ({@code caseInsensitiveFields}), so a shopper who types
 * "cafe" finds "Café de Colombia". Facets are not sorted; the search lists its sortable fields
 * through {@link #sortableFields()}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@FilterableQuery(
    value = Product.class,
    filterableFields = {
      "name",
      "description",
      "sku",
      "price.amount",
      "status",
      "categories.slug",
      "tags",
      "seller.id",
      "seller.city",
      "publishedAt"
    },
    caseInsensitiveFields = {"name", "description"})
@interface ProductFilter {

  /** The fields the client may sort by; none by default. */
  @AliasFor(annotation = FilterableQuery.class)
  String[] sortableFields() default {};
}
