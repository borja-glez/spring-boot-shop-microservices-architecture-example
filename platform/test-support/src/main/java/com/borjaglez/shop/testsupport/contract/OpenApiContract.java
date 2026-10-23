package com.borjaglez.shop.testsupport.contract;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.MockMvcRequest;
import com.atlassian.oai.validator.mockmvc.MockMvcResponse;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.Response;
import com.atlassian.oai.validator.report.ValidationReport;

/**
 * An OpenAPI contract that MockMvc exchanges are checked against.
 *
 * <pre>{@code
 * static final OpenApiContract CONTRACT =
 *     OpenApiContract.of(Path.of("src/main/openapi/catalog-api.v1.yaml"));
 *
 * mvc.perform(get("/api/v1/catalog/products/{id}", id))
 *     .andExpect(status().isOk())
 *     .andExpect(CONTRACT.matches());
 * }</pre>
 *
 * <p>The document is read from its file, so its relative {@code $ref}s to the shared components of
 * {@code platform/api-contracts} resolve; Gradle runs the tests in the module directory, so a path
 * relative to the module works. The operation is found by method and path, then the parameters
 * (path, query, headers), the bodies, the status and the content types are checked against it. Any
 * message at level {@code ERROR} of the validator fails the test; the failure lists them with their
 * keys, such as {@code validation.response.body.schema.required}. Bodies are strict: a property the
 * schema does not declare is an error too.
 */
public final class OpenApiContract {

  private final Path spec;
  private final OpenApiInteractionValidator validator;

  private OpenApiContract(Path spec, OpenApiInteractionValidator validator) {
    this.spec = spec;
    this.validator = validator;
  }

  /**
   * Reads the contract; fails if the file does not exist or is not a valid OpenAPI document.
   *
   * @param spec path of the document, relative to the module directory
   */
  public static OpenApiContract of(Path spec) {
    Objects.requireNonNull(spec, "spec must not be null");
    if (!Files.isRegularFile(spec)) {
      throw new IllegalArgumentException(
          "No OpenAPI document at " + spec.toAbsolutePath().normalize());
    }
    OpenApiInteractionValidator validator =
        OpenApiInteractionValidator.createForSpecificationUrl(spec.toUri().toString())
            // Merge allOf schemas before validating: the validator rejects the properties a schema
            // does not declare, which would reject every property of a composed schema.
            .withResolveCombinators(true)
            .build();
    return new OpenApiContract(spec, validator);
  }

  /** Fails unless both the request and the response of the exchange follow the contract. */
  public ResultMatcher matches() {
    return result -> {
      Request request = request(result);
      Response response = response(result);
      verify(request, response, validator.validate(request, response));
    };
  }

  /**
   * Fails unless the response follows the contract of the operation, whatever the request. For
   * tests that break the contract on purpose to check how the server rejects the request.
   */
  public ResultMatcher matchesResponse() {
    return result -> {
      Request request = request(result);
      Response response = response(result);
      verify(
          request,
          response,
          validator.validateResponse(request.getPath(), request.getMethod(), response));
    };
  }

  private void verify(Request request, Response response, ValidationReport report) {
    if (report.hasErrors()) {
      throw new AssertionError(
          "%s %s answered %d, which does not follow %s:%n%s"
              .formatted(
                  request.getMethod(),
                  request.getPath(),
                  response.getStatus(),
                  spec.getFileName(),
                  describe(report.getMessages(), "")));
    }
  }

  private static String describe(List<ValidationReport.Message> messages, String indent) {
    StringBuilder description = new StringBuilder();
    for (ValidationReport.Message message : messages) {
      if (message.getLevel() != ValidationReport.Level.ERROR) {
        continue;
      }
      description.append(indent).append("- ").append(message.getKey());
      message
          .getContext()
          .flatMap(ValidationReport.MessageContext::getPointers)
          .map(ValidationReport.MessageContext.Pointers::getInstance)
          .filter(pointer -> !pointer.isEmpty())
          .ifPresent(pointer -> description.append(" at ").append(pointer));
      description.append(": ").append(message.getMessage()).append(System.lineSeparator());
      for (String info : message.getAdditionalInfo()) {
        description.append(indent).append("    ").append(info).append(System.lineSeparator());
      }
      description.append(describe(message.getNestedMessages(), indent + "    "));
    }
    return description.toString();
  }

  private static Request request(MvcResult result) {
    return MockMvcRequest.of(result.getRequest());
  }

  private static Response response(MvcResult result) {
    return MockMvcResponse.of(result.getResponse());
  }
}
