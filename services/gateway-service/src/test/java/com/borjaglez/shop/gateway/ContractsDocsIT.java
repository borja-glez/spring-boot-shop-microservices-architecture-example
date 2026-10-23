package com.borjaglez.shop.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContractsDocsIT {

  private static final String CHAOS_CONTRACT =
      "platform/service-support/src/main/openapi/chaos-api.v1.yaml";
  private static final Pattern SWAGGER_UI_PATH =
      Pattern.compile("/api/docs/webjars/swagger-ui/([0-9.]+)/");
  private static final Pattern ASSET_REFERENCE = Pattern.compile("(?:src|href)=\"([^\"]+)\"");
  private static final Pattern RELATIVE_REF = Pattern.compile("\\$ref:\\s*'(\\.[^'#]+)#");

  @LocalServerPort int port;
  RestClient http;

  @BeforeEach
  void client() {
    http =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build();
  }

  @Test
  void docsPageIsServedAsHtml() {
    var response = http.get().uri("/api/docs/").retrieve().toEntity(String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML))
        .isTrue();
    assertThat(response.getBody()).contains("/api/docs/contracts/index.json");
  }

  @Test
  void docsWithoutTrailingSlashServeThePage() {
    var response = http.get().uri("/api/docs").retrieve().toEntity(String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("contracts/index.json");
  }

  @Test
  void indexListsTheChaosContract() {
    String index = http.get().uri("/api/docs/contracts/index.json").retrieve().body(String.class);

    assertThat(index)
        .contains("\"kind\":\"openapi\"")
        .contains("\"path\":\"" + CHAOS_CONTRACT + "\"");
  }

  @Test
  void relativeRefOfTheChaosContractResolvesToAServedFile() {
    URI contract = URI.create("http://localhost:" + port + "/api/docs/contracts/" + CHAOS_CONTRACT);
    String yaml = http.get().uri(contract).retrieve().body(String.class);
    Matcher ref = RELATIVE_REF.matcher(yaml);
    assertThat(ref.find()).isTrue();

    var resolved = http.get().uri(contract.resolve(ref.group(1))).retrieve().toEntity(String.class);

    assertThat(resolved.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resolved.getBody()).startsWith("openapi:");
  }

  @Test
  void swaggerUiAssetsAreServedUnderTheDocsPrefix() {
    String page = http.get().uri("/api/docs/").retrieve().body(String.class);
    Matcher version = SWAGGER_UI_PATH.matcher(page);
    assertThat(version.find()).isTrue();

    var response =
        http.get()
            .uri("/api/docs/webjars/swagger-ui/" + version.group(1) + "/swagger-ui-bundle.js")
            .retrieve()
            .toBodilessEntity();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void pageReferencesOnlyDocsPathsAndPinnedCdnFiles() {
    String page = http.get().uri("/api/docs/").retrieve().body(String.class);
    Matcher reference = ASSET_REFERENCE.matcher(page);

    int found = 0;
    while (reference.find()) {
      found++;
      assertThat(reference.group(1))
          .matches("/api/docs/.*|https://cdn.jsdelivr.net/npm/@asyncapi/[a-z-]+@[0-9.]+/.*");
    }
    assertThat(found).isGreaterThanOrEqualTo(3);
    assertThat(page).doesNotContain("@swaggerUiVersion@");
  }

  @Test
  void componentLibrariesAreServedButNotListed() {
    String index = http.get().uri("/api/docs/contracts/index.json").retrieve().body(String.class);
    var common =
        http.get()
            .uri("/api/docs/contracts/platform/api-contracts/openapi/common.v1.yaml")
            .retrieve()
            .toBodilessEntity();

    assertThat(common.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(index).doesNotContain("common.v1.yaml");
  }
}
