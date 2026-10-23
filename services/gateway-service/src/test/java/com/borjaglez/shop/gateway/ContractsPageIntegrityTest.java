package com.borjaglez.shop.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * The contracts page loads the AsyncAPI web component and its stylesheet from jsDelivr with
 * integrity hashes, which belong to one version. The version comes from the version catalog ({@code
 * asyncapi-web-component} in {@code gradle/libs.versions.toml}); changing it without new hashes
 * would only show up in the browser, so the pair is pinned here.
 */
class ContractsPageIntegrityTest {

  private static final String VERSION = "3.1.8";
  private static final String COMPONENT_INTEGRITY =
      "sha384-1lhhmwYf1Eif0PQgDyo9eNFJLiRmcSgsv4mkpEtxLPzd+gEPIjxuoEdCQq9PsSm5";
  private static final String STYLESHEET_INTEGRITY =
      "sha384-oo9RoQcacP++XdMX6CjTucTvASEORHX3chFik0/V2kHcsHiVboGyWZztGeq/0bum";

  private static final String RECOMPUTE =
      """
      the integrity hashes of the contracts page belong to @asyncapi/web-component %s. For the \
      new version, recompute the sha384 of lib/asyncapi-web-component.js (@asyncapi/web-component) \
      and styles/default.min.css (@asyncapi/react-component), for example with \
      `curl -sL <url> | openssl dgst -sha384 -binary | openssl base64 -A`, put them in \
      static/api/docs/index.html and update VERSION and the hashes of this test\
      """
          .formatted(VERSION);

  @Test
  void theIntegrityHashesBelongToTheVersionOfTheCatalog() throws IOException {
    // The page as the build ships it, with the version of the catalog replaced in.
    String page =
        new ClassPathResource("static/api/docs/index.html")
            .getContentAsString(StandardCharsets.UTF_8);

    assertThat(version(page, "web-component")).as(RECOMPUTE).isEqualTo(VERSION);
    assertThat(version(page, "react-component")).as(RECOMPUTE).isEqualTo(VERSION);
    assertThat(page)
        .as(RECOMPUTE)
        .contains("integrity=\"" + COMPONENT_INTEGRITY + "\"")
        .contains("ASYNCAPI_CSS_INTEGRITY = '" + STYLESHEET_INTEGRITY + "'");
  }

  private static String version(String page, String asyncApiPackage) {
    Matcher matcher = Pattern.compile("@asyncapi/" + asyncApiPackage + "@([^/]+)/").matcher(page);
    assertThat(matcher.find()).as("no @asyncapi/%s URL in the page", asyncApiPackage).isTrue();
    return matcher.group(1);
  }
}
