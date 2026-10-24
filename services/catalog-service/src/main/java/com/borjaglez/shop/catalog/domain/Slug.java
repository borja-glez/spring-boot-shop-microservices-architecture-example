package com.borjaglez.shop.catalog.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** [DDD · Value Object] URL-safe identifier of a product: its name followed by its SKU. */
public record Slug(String value) {

  private static final Pattern MARKS = Pattern.compile("\\p{M}+");
  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
  private static final Pattern EDGE_DASHES = Pattern.compile("(^-+)|(-+$)");

  public Slug {
    Objects.requireNonNull(value, "value must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
  }

  public static Slug of(String name, Sku sku) {
    return new Slug(slugify(name) + "-" + sku.value().toLowerCase(Locale.ROOT));
  }

  /** Lowercase ASCII words separated by dashes, accents removed: "Té verde!" gives "te-verde". */
  static String slugify(String text) {
    String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
    String ascii = MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    String dashed = NON_ALPHANUMERIC.matcher(ascii).replaceAll("-");
    return EDGE_DASHES.matcher(dashed).replaceAll("");
  }
}
