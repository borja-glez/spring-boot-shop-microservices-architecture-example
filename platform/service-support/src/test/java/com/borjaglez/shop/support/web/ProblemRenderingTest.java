package com.borjaglez.shop.support.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import com.borjaglez.shop.support.web.problem.DomainProblemMapper;

/** Details of the rendering that a request through MockMvc does not reach. */
class ProblemRenderingTest {

  private final ProblemDetailsExceptionHandler handler =
      new ProblemDetailsExceptionHandler(List.of(new DomainProblemMapper()));

  private static ServletWebRequest request(String uri) {
    return new ServletWebRequest(new MockHttpServletRequest("GET", uri));
  }

  @Test
  void aCyclicCauseChainEndsInAnInternalError() {
    Exception first = new Exception("first");
    Exception second = new Exception("second", first);
    first.initCause(second);

    ResponseEntity<ProblemDetail> response = handler.handleAny(first, request("/x"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().getProperties()).containsEntry("code", "internal-error");
  }

  @Test
  void aVeryDeepCauseChainEndsInAnInternalError() {
    Exception exception = new Exception("root");
    for (int i = 0; i < 20; i++) {
      exception = new Exception("level " + i, exception);
    }

    assertThat(handler.handleAny(exception, request("/x")).getStatusCode())
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  void aProblemWithoutCodeGetsOneFromItsStatusAndKeepsItsInstance() {
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problem.setInstance(URI.create("/own-instance"));

    ResponseEntity<Object> response =
        handler.createResponseEntity(
            problem, new HttpHeaders(), HttpStatus.BAD_REQUEST, request("/x"));

    ProblemDetail body = (ProblemDetail) response.getBody();
    assertThat(body.getProperties()).containsEntry("code", "bad-request");
    assertThat(body.getInstance()).isEqualTo(URI.create("/own-instance"));
    assertThat(body.getProperties()).doesNotContainKey("correlationId");
  }

  @Test
  void aStatusWithoutAConstantGetsAGenericCode() {
    ProblemDetail problem = ProblemDetail.forStatus(499);

    ResponseEntity<Object> response =
        handler.createResponseEntity(
            problem, new HttpHeaders(), HttpStatusCode.valueOf(499), request("/x"));

    assertThat(((ProblemDetail) response.getBody()).getProperties())
        .containsEntry("code", "http-499");
  }

  @Test
  void aBodyThatIsNotAProblemIsLeftAlone() {
    ResponseEntity<Object> response =
        handler.createResponseEntity(
            "plain", new HttpHeaders(), HttpStatus.BAD_REQUEST, request("/x"));

    assertThat(response.getBody()).isEqualTo("plain");
  }

  @Test
  void aRequestThatIsNotAServletRequestHasNoInstance() {
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

    handler.createResponseEntity(
        problem, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));

    assertThat(problem.getInstance()).isNull();
  }

  @Test
  void theProblemCarriesTheCorrelationIdAndTheRequestPath() {
    MockHttpServletRequest raw = new MockHttpServletRequest("GET", "/x");
    raw.setAttribute(CorrelationIdFilter.ATTRIBUTE, "abc");

    ResponseEntity<ProblemDetail> response =
        handler.handleAny(new Exception("x"), new ServletWebRequest(raw));

    assertThat(response.getBody().getProperties()).containsEntry("correlationId", "abc");
    assertThat(response.getBody().getInstance()).isEqualTo(URI.create("/x"));
  }

  @Test
  void aProblemWithOtherPropertiesStillGetsACode() {
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
    problem.setProperty("extra", "value");

    handler.createResponseEntity(problem, new HttpHeaders(), HttpStatus.CONFLICT, request("/x"));

    assertThat(problem.getProperties()).containsEntry("code", "conflict");
  }
}
