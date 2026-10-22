package com.borjaglez.shop.kernel.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ClientFilterTest {

  @Test
  void nullListsBecomeEmpty() {
    ClientFilter filter = new ClientFilter(null, null, null);

    assertThat(filter.filters()).isEmpty();
    assertThat(filter.orFilters()).isEmpty();
    assertThat(filter.sorts()).isEmpty();
  }

  @Test
  void listsAreImmutableCopies() {
    List<String> source = new ArrayList<>(List.of("a"));
    ClientFilter filter = new ClientFilter(source, List.of("b"), List.of("c"));
    source.add("z");

    assertThat(filter.filters()).containsExactly("a");
    assertThat(filter.orFilters()).containsExactly("b");
    assertThat(filter.sorts()).containsExactly("c");
    assertThatThrownBy(() -> filter.filters().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void noneHasNoFiltersOrSorts() {
    ClientFilter none = ClientFilter.none();

    assertThat(none.filters()).isEmpty();
    assertThat(none.orFilters()).isEmpty();
    assertThat(none.sorts()).isEmpty();
  }
}
