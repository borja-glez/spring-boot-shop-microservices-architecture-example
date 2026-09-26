package com.borjaglez.shop.catalog.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Builds URL-safe identifiers: lowercase ASCII words separated by dashes, accents removed. */
final class Slugs {

  private static final Pattern MARKS = Pattern.compile("\\p{M}+");
  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
  private static final Pattern EDGE_DASHES = Pattern.compile("(^-+)|(-+$)");

  private Slugs() {}

  static String slugify(String text) {
    String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
    String ascii = MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    String dashed = NON_ALPHANUMERIC.matcher(ascii).replaceAll("-");
    return EDGE_DASHES.matcher(dashed).replaceAll("");
  }
}
