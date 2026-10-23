package com.borjaglez.shop.testsupport.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.asyncapi.AsyncApiContract;
import com.borjaglez.cqrs.asyncapi.AsyncApiVerifier;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

class AsyncApiContractsTest {

  // Relative to the repository root, which the build passes in shop.repoRoot.
  private static final String CONTRACT =
      "platform/test-support/src/test/resources/asyncapi/widgets.asyncapi.yaml";

  private final MessageSerializer serializer = new JsonSerializer(JsonMapper.builder().build());
  private final MessageNamingStrategy naming = new DefaultMessageNamingStrategy("shop");

  private AsyncApiVerifier verifier() {
    return AsyncApiContracts.verifier(CONTRACT, serializer, naming);
  }

  @Test
  void loadsTheDocumentThroughTheRepoRoot() {
    assertThat(System.getProperty("shop.repoRoot")).isNotBlank();

    assertThatCode(
            () ->
                verifier()
                    .sample(new WidgetCreated(UUID.randomUUID(), "Sprocket"))
                    .verifyCoverage())
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsAConformingMessageAndClosedSchemas() {
    AsyncApiVerifier verifier = verifier().sample(new WidgetCreated(UUID.randomUUID(), "Sprocket"));

    assertThatCode(verifier::verifyPayloads).doesNotThrowAnyException();
    assertThatCode(verifier::verifyClosedSchemas).doesNotThrowAnyException();
  }

  @Test
  void rejectsAMessageThatBreaksTheSchema() {
    AsyncApiVerifier verifier = verifier().sample(new WidgetCreated(UUID.randomUUID(), ""));

    assertThatThrownBy(verifier::verifyPayloads)
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("shop.widgets.1.event.widget.widget-created")
        .hasMessageContaining("name");
  }

  @Test
  void rejectsAFieldTheContractDoesNotDeclare() {
    AsyncApiVerifier verifier =
        verifier().sample(new WidgetWithColour(UUID.randomUUID(), "Sprocket", "blue"));

    assertThatThrownBy(verifier::verifyPayloads)
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("colour");
  }

  @Test
  void sharedComponentsDocumentIsAValidContractWithoutChannels() {
    var common =
        AsyncApiContract.load(
            Path.of(System.getProperty("shop.repoRoot"))
                .resolve("platform/api-contracts/asyncapi/common.yaml"));

    assertThat(common.messages()).isEmpty();
  }

  @Test
  void failsClearlyWhenTheFileDoesNotExist() {
    assertThatThrownBy(() -> AsyncApiContracts.verifier("nope/missing.yaml", serializer, naming))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nope")
        .hasMessageContaining("missing.yaml");
  }

  @Test
  void failsClearlyWhenTheRepoRootIsNotConfigured() {
    String previous = System.getProperty("shop.repoRoot");
    System.clearProperty("shop.repoRoot");
    try {
      assertThatThrownBy(() -> AsyncApiContracts.verifier(CONTRACT, serializer, naming))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("shop.repoRoot");
    } finally {
      if (previous == null) {
        System.clearProperty("shop.repoRoot");
      } else {
        System.setProperty("shop.repoRoot", previous);
      }
    }
  }

  @Getter
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  @AllArgsConstructor
  @CqrsMessage(service = "widgets", module = "widget", name = "widget-created")
  public static class WidgetCreated extends Event {

    private UUID widgetId;
    private String name;
  }

  /** Same logical name as {@link WidgetCreated}, with a field the contract does not declare. */
  @Getter
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  @AllArgsConstructor
  @CqrsMessage(service = "widgets", module = "widget", name = "widget-created")
  public static class WidgetWithColour extends Event {

    private UUID widgetId;
    private String name;
    private String colour;
  }

  private record JsonSerializer(JsonMapper mapper) implements MessageSerializer {

    @Override
    public byte[] serialize(Object message) {
      return mapper.writeValueAsBytes(message);
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> type) {
      return mapper.readValue(data, type);
    }
  }
}
