package com.borjaglez.shop.testsupport.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

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
import com.borjaglez.shop.support.web.problem.SpecificationProblemMapper;
import com.borjaglez.shop.support.web.problem.UserHeaderProblemMapper;
import com.borjaglez.shop.testsupport.contract.rest.v1.api.WidgetsApi;
import com.borjaglez.shop.testsupport.contract.rest.v1.model.NewWidgetDto;
import com.borjaglez.shop.testsupport.contract.rest.v1.model.WidgetDto;
import com.borjaglez.shop.testsupport.contract.rest.v1.model.WidgetPageDto;
import com.borjaglez.shop.testsupport.contract.rest.v1.model.WidgetStatusDto;
import com.borjaglez.specrepository.core.DisallowedFieldException;

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
              new ProblemDetailsExceptionHandler(
                  List.of(
                      new DomainProblemMapper(),
                      new SpecificationProblemMapper(),
                      new UserHeaderProblemMapper())))
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
  void acceptsAnInvalidFilterProblemRenderedByTheShop() throws Exception {
    shop.perform(get("/api/v1/widgets").param("filter", "price:gt:10"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"))
        .andExpect(jsonPath("$.field").value("price"))
        .andExpect(CONTRACT.matches());
  }

  @Test
  void toleratesAnInvalidCorrelationId() throws Exception {
    shop.perform(get("/api/v1/widgets/w-1").header("X-Correlation-Id", "not a valid id!"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Correlation-Id", matchesPattern("[0-9a-f-]{36}")))
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
        .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "price")))
        .andExpect(CONTRACT.matchesResponse());
    assertThatThrownBy(() -> shop.perform(invalidBody).andExpect(CONTRACT.matches()))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("validation.request.body.schema.minLength");
  }

  @Test
  void aMissingUserHeaderOfAGeneratedInterfaceIsAMissingUserProblem() throws Exception {
    // The implementation declares no annotation: the header and its pattern come from WidgetsApi.
    assertThat(
            WidgetsController.class
                .getMethod("createWidget", String.class, NewWidgetDto.class)
                .getParameters()[0]
                .getAnnotations())
        .isEmpty();

    shop.perform(
            post("/api/v1/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Desk", "price": 120.50}
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("missing-user"))
        .andExpect(CONTRACT.matchesResponse());
  }

  @Test
  void anInvalidUserHeaderOfAGeneratedInterfaceIsAnInvalidUserProblem() throws Exception {
    shop.perform(
            post("/api/v1/widgets")
                .header("X-Shop-User", "not a valid user!")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Desk", "price": 120.50}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-user"))
        .andExpect(CONTRACT.matchesResponse());
  }

  @Test
  void aJsonOnlyClientGetsTheUserHeaderProblemsAsJson() throws Exception {
    var missingUser =
        post("/api/v1/widgets")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"name": "Desk", "price": 120.50}
                """);
    shop.perform(missingUser)
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("missing-user"))
        .andExpect(CONTRACT.matchesResponse());

    shop.perform(missingUser.header("X-Shop-User", "not a valid user!"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("invalid-user"))
        .andExpect(CONTRACT.matchesResponse());
  }

  @Test
  void aJsonOnlyClientGetsAnInvalidBodyAsJson() throws Exception {
    shop.perform(
            post("/api/v1/widgets")
                .header("X-Shop-User", "seller-ana")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "", "price": -1}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "price")))
        .andExpect(CONTRACT.matchesResponse());
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
        .hasMessageContaining("validation.response.body.schema.type")
        .hasMessageContaining("/name");
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
              WidgetDto widget = lamp().color("green").owner("seller-ana");

              String json = mapper.writeValueAsString(widget);

              assertThat(mapper.readTree(json).propertyNames())
                  .containsExactlyInAnyOrder(
                      "id", "name", "price", "status", "tags", "createdAt", "color", "owner");
              assertThat(mapper.readValue(json, WidgetDto.class)).isEqualTo(widget);
            });
  }

  @Test
  void rejectsADocumentThatDoesNotExist() {
    Path missing = Path.of("src/test/resources/openapi/../openapi/none.yaml");

    assertThatThrownBy(() -> OpenApiContract.of(missing))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(missing.toAbsolutePath().normalize().toString());
  }

  private static WidgetDto lamp() {
    return new WidgetDto(
        "w-1",
        "Lamp",
        new BigDecimal("24.90"),
        WidgetStatusDto.ACTIVE,
        List.of("light", "desk"),
        CREATED_AT);
  }

  @RestController
  static class WidgetsController implements WidgetsApi {

    private static final Set<String> FILTERABLE = Set.of("name", "status");

    @Override
    public ResponseEntity<WidgetPageDto> listWidgets(
        Integer page, Integer size, List<String> filter, List<String> orFilter, List<String> sort) {
      // The allow-list the persistence adapter enforces, declared as x-specrepo-filterable-fields.
      for (String condition : filter == null ? List.<String>of() : filter) {
        String field = condition.substring(0, condition.indexOf(':'));
        if (!FILTERABLE.contains(field)) {
          throw new DisallowedFieldException(field, "filter");
        }
      }
      return ResponseEntity.ok(new WidgetPageDto(page, size, 1L, 1, List.of(lamp())));
    }

    @Override
    public ResponseEntity<WidgetDto> createWidget(String user, NewWidgetDto newWidget) {
      WidgetDto created =
          new WidgetDto(
                  "w-2",
                  newWidget.getName(),
                  newWidget.getPrice(),
                  WidgetStatusDto.ACTIVE,
                  List.of(),
                  CREATED_AT)
              .color(newWidget.getColor())
              .owner(user);
      return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Override
    public ResponseEntity<WidgetDto> getWidget(String id) {
      if (!id.equals("w-1")) {
        throw new NotFoundException("widget-not-found", "Widget " + id + " does not exist.");
      }
      return ResponseEntity.ok(lamp());
    }
  }

  @RestController
  static class OffContractController {

    @GetMapping("/api/v1/widgets/{id}")
    ResponseEntity<WidgetDto> widget(@PathVariable String id) {
      if (id.equals("w-pending")) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(lamp());
      }
      WidgetDto withoutName = lamp().name(null);
      return ResponseEntity.ok(withoutName);
    }

    @DeleteMapping("/api/v1/widgets/{id}")
    ResponseEntity<Void> delete(@PathVariable String id) {
      return ResponseEntity.noContent().build();
    }
  }
}
