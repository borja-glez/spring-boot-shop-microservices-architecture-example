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
  void swaggerUiAssetsAreServedFromTheWebjar() {
    var response =
        http.get()
            .uri("/webjars/swagger-ui/5.33.1/swagger-ui-bundle.js")
            .retrieve()
            .toBodilessEntity();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
