package com.borjaglez.shop.catalog.infrastructure.in.rest.v1;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.borjaglez.shop.catalog.infrastructure.out.persistence.ProductFilters;

/**
 * The whitelist of the public search has one source, {@link ProductFilters}, which the read side
 * enforces; the contract describes it twice, in the {@code x-specrepo-*} extensions of the
 * operations and in the patterns of the filter parameters. Both must list exactly its fields.
 */
class FilterWhitelistMatchesContractTest {

  private static final Path CONTRACT = Path.of("src/main/openapi/catalog-api.v1.yaml");
  private static final Path SPECREPO_FRAGMENT =
      Path.of("../../platform/api-contracts/openapi/specrepo-filters.yaml");

  /** The field of a condition or a sort in the generic syntax: a path in dot notation. */
  private static final String ANY_FIELD = "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*";

  private static final String SEARCH = "/api/v1/catalog/products";
  private static final String FACETS = "/api/v1/catalog/products/facets";

  @Test
  void theSearchDeclaresTheFieldsOfTheWhitelist() throws IOException {
    Map<String, Object> search = operation(SEARCH);

    assertThat(strings(search.get("x-specrepo-filterable-fields")))
        .containsExactlyElementsOf(ProductFilters.FILTERABLE_FIELDS);
    assertThat(strings(search.get("x-specrepo-sortable-fields")))
        .containsExactlyElementsOf(ProductFilters.SORTABLE_FIELDS);
  }

  @Test
  void theFacetsDeclareTheFiltersOfTheSearchAndNoSort() throws IOException {
    Map<String, Object> facets = operation(FACETS);

    assertThat(strings(facets.get("x-specrepo-filterable-fields")))
        .containsExactlyElementsOf(ProductFilters.FILTERABLE_FIELDS);
    assertThat(strings(facets.get("x-specrepo-sortable-fields"))).isEmpty();
  }

  @Test
  void theFilterPatternsAreTheSyntaxOfSpecificationRepositoryOnTheWhitelist() throws IOException {
    String filterable = alternatives(ProductFilters.FILTERABLE_FIELDS);

    assertThat(pattern(load(CONTRACT), "ProductFilter"))
        .isEqualTo(
            pattern(load(SPECREPO_FRAGMENT), "SpecrepoFilter").replace(ANY_FIELD, filterable));
    assertThat(pattern(load(CONTRACT), "ProductOrFilter"))
        .isEqualTo(
            pattern(load(SPECREPO_FRAGMENT), "SpecrepoOrFilter").replace(ANY_FIELD, filterable));
  }

  @Test
  void theSortPatternIsTheSyntaxOfSpecificationRepositoryOnTheSortableFields() throws IOException {
    assertThat(pattern(load(CONTRACT), "ProductSort"))
        .isEqualTo(
            pattern(load(SPECREPO_FRAGMENT), "SpecrepoSort")
                .replace(ANY_FIELD, alternatives(ProductFilters.SORTABLE_FIELDS)));
  }

  @Test
  void bothOperationsTakeTheNarrowedParameters() throws IOException {
    assertThat(parameterRefs(operation(SEARCH)))
        .contains(
            "#/components/parameters/ProductFilter",
            "#/components/parameters/ProductOrFilter",
            "#/components/parameters/ProductSort");
    assertThat(parameterRefs(operation(FACETS)))
        .containsExactly(
            "#/components/parameters/ProductFilter", "#/components/parameters/ProductOrFilter");
  }

  /** {@code (name|price\.amount|...)}: the fields as regular expression alternatives. */
  private static String alternatives(List<String> fields) {
    return fields.stream().map(field -> field.replace(".", "\\.")).collect(joining("|", "(", ")"));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> operation(String path) throws IOException {
    Map<String, Object> paths = (Map<String, Object>) load(CONTRACT).get("paths");
    return (Map<String, Object>) ((Map<String, Object>) paths.get(path)).get("get");
  }

  @SuppressWarnings("unchecked")
  private static List<String> parameterRefs(Map<String, Object> operation) {
    return ((List<Map<String, Object>>) operation.get("parameters"))
        .stream().map(parameter -> (String) parameter.get("$ref")).toList();
  }

  @SuppressWarnings("unchecked")
  private static String pattern(Map<String, Object> document, String parameter) {
    Map<String, Object> components = (Map<String, Object>) document.get("components");
    Map<String, Object> parameters = (Map<String, Object>) components.get("parameters");
    Map<String, Object> schema =
        (Map<String, Object>) ((Map<String, Object>) parameters.get(parameter)).get("schema");
    return (String) ((Map<String, Object>) schema.get("items")).get("pattern");
  }

  @SuppressWarnings("unchecked")
  private static List<String> strings(Object list) {
    return (List<String>) list;
  }

  private static Map<String, Object> load(Path document) throws IOException {
    try (Reader reader = Files.newBufferedReader(document)) {
      return new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
    }
  }
}
