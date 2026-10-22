package com.borjaglez.shop.testsupport.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.shop.kernel.error.NotFoundException;
import com.borjaglez.shop.support.web.CorrelationIdFilter;
import com.borjaglez.shop.support.web.ProblemDetailsExceptionHandler;
import com.borjaglez.shop.support.web.problem.DomainProblemMapper;
import com.borjaglez.shop.testsupport.contract.rest.v1.api.WidgetsApi;
import com.borjaglez.shop.testsupport.contract.rest.v1.model.NewWidget;
import com.borjaglez.shop.testsupport.contract.rest.v1.model.Widget;
import com.borjaglez.shop.testsupport.contract.rest.v1.model.WidgetPage;
import com.borjaglez.shop.testsupport.contract.rest.v1.model.WidgetStatus;

import tools.jackson.databind.json.JsonMapper;

class OpenApiContractTest {

  private static final OpenApiContract CONTRACT =
      OpenApiContract.of(Path.of("src/test/resources/openapi/widgets-api.v1.yaml"));

  private static final OffsetDateTime CREATED_AT =
      OffsetDateTime.of(2026, 10, 22, 9, 30, 0, 0, ZoneOffset.UTC);

  /** The shop's controllers: generated interface, problem rendering and correlation ids. */
  private final MockMvc shop =
      standaloneSetup(new WidgetsController())
          .setControllerAdvice(
              new ProblemDetailsExceptionHandler(List.of(new DomainProblemMapper())))
          .addFilters(new CorrelationIdFilter(List.of()))
          .build();

  /** A controller that answers outside the contract. */
  private final MockMvc offContract = standaloneSetup(new OffContractController()).build();

  @Test
  void acceptsAResponseThatMatchesTheSpec() throws Exception {
    shop.perform(
            get("/api/v1/widgets")
                .param("page", "0")
                .param("size", "10")
                .param("filter", "status:eq:ACTIVE")
                .param("orFilter", "name:eq:Lamp;name:eq:Desk")
                .param("sort", "name,desc"))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matches());
  }

  @Test
  void acceptsARequestBodyAndHeaderThatMatchTheSpec() throws Exception {
    shop.perform(
            post("/api/v1/widgets")
                .header("X-Shop-User", "seller-ana")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Desk", "price": 120.50, "color": null}
                    """))
        .andExpect(status().isCreated())
        .andExpect(CONTRACT.matches());
  }

  @Test
  void acceptsAProblemRenderedByTheShop() throws Exception {
    shop.perform(get("/api/v1/widgets/w-404").header("X-Correlation-Id", "test-404"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("widget-not-found"))
        .andExpect(CONTRACT.matches());
  }

  @Test
  void checksOnlyTheResponseOfARequestThatBreaksTheSpecOnPurpose() throws Exception {
    var invalidBody =
        post("/api/v1/widgets")
            .header("X-Shop-User", "seller-ana")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"name": "", "price": -1}
                """);

    shop.perform(invalidBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(CONTRACT.matchesResponse());
    assertThatThrownBy(() -> shop.perform(invalidBody).andExpect(CONTRACT.matches()))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("validation.request.body.schema.minLength");
  }

  @Test
  void rejectsAnUndeclaredStatus() {
    assertThatThrownBy(
            () ->
                offContract.perform(get("/api/v1/widgets/w-pending")).andExpect(CONTRACT.matches()))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("validation.response.status.unknown")
        .hasMessageContaining("202");
  }

  @Test
  void rejectsAMissingRequiredField() {
    assertThatThrownBy(
            () -> offContract.perform(get("/api/v1/widgets/w-1")).andExpect(CONTRACT.matches()))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("validation.response.body.schema.required")
        .hasMessageContaining("name");
  }

  @Test
  void rejectsAnUnknownOperation() {
    assertThatThrownBy(
            () -> offContract.perform(delete("/api/v1/widgets/w-1")).andExpect(CONTRACT.matches()))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("validation.request.operation.notAllowed")
        .hasMessageContaining("DELETE");
  }

  @Test
  void rejectsARequestThatBreaksTheSpec() {
    assertThatThrownBy(
            () ->
                shop.perform(
                        post("/api/v1/widgets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                """
                                {"name": "Desk", "price": 120.50}
                                """))
                    .andExpect(CONTRACT.matches()))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("validation.request.parameter.header.missing")
        .hasMessageContaining("X-Shop-User");
  }

  @Test
  void generatedModelRoundTripsWithBootObjectMapper() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .run(
            context -> {
              JsonMapper mapper = context.getBean(JsonMapper.class);
              Widget widget = lamp().color("green").owner("seller-ana");

              String json = mapper.writeValueAsString(widget);

              assertThat(mapper.readTree(json).propertyNames())
                  .containsExactlyInAnyOrder(
                      "id", "name", "price", "status", "tags", "createdAt", "color", "owner");
              assertThat(mapper.readValue(json, Widget.class)).isEqualTo(widget);
            });
  }

  @Test
  void rejectsADocumentThatDoesNotExist() {
    assertThatThrownBy(() -> OpenApiContract.of(Path.of("src/test/resources/openapi/none.yaml")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("none.yaml");
  }

  private static Widget lamp() {
    return new Widget(
        "w-1",
        "Lamp",
        new BigDecimal("24.90"),
        WidgetStatus.ACTIVE,
        List.of("light", "desk"),
        CREATED_AT);
  }

  @RestController
  static class WidgetsController implements WidgetsApi {

    @Override
    public ResponseEntity<WidgetPage> listWidgets(
        Integer page, Integer size, List<String> filter, List<String> orFilter, List<String> sort) {
      return ResponseEntity.ok(new WidgetPage(page, size, 1L, 1, List.of(lamp())));
    }

    @Override
    public ResponseEntity<Widget> createWidget(String user, NewWidget newWidget) {
      Widget created =
          new Widget(
                  "w-2",
                  newWidget.getName(),
                  newWidget.getPrice(),
                  WidgetStatus.ACTIVE,
                  List.of(),
                  CREATED_AT)
              .color(newWidget.getColor())
              .owner(user);
      return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Override
    public ResponseEntity<Widget> getWidget(String id, String correlationId) {
      if (!id.equals("w-1")) {
        throw new NotFoundException("widget-not-found", "Widget " + id + " does not exist.");
      }
      return ResponseEntity.ok(lamp());
    }
  }

  @RestController
  static class OffContractController {

    @GetMapping("/api/v1/widgets/{id}")
    ResponseEntity<Widget> widget(@PathVariable String id) {
      if (id.equals("w-pending")) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(lamp());
      }
      Widget withoutName = lamp().name(null);
      return ResponseEntity.ok(withoutName);
    }

    @DeleteMapping("/api/v1/widgets/{id}")
    ResponseEntity<Void> delete(@PathVariable String id) {
      return ResponseEntity.noContent().build();
    }
  }
}
