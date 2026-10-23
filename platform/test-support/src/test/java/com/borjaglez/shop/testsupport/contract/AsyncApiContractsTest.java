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

  // Relative to the module directory, the working directory of the Gradle test tasks.
  private static final Path CONTRACT = Path.of("src/test/resources/asyncapi/widgets.asyncapi.yaml");

  private final MessageSerializer serializer = new JsonSerializer(JsonMapper.builder().build());
  private final MessageNamingStrategy naming = new DefaultMessageNamingStrategy("shop");

  private AsyncApiVerifier verifier() {
    return AsyncApiContracts.verifier(CONTRACT, serializer, naming);
  }

  @Test
  void loadsTheDocumentRelativeToTheModule() {
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
    var common = AsyncApiContract.load(Path.of("../api-contracts/asyncapi/common.yaml"));

    assertThat(common.messages()).isEmpty();
  }

  @Test
  void failsClearlyWhenTheFileDoesNotExist() {
    Path missing = Path.of("nope/missing.yaml");

    assertThatThrownBy(() -> AsyncApiContracts.verifier(missing, serializer, naming))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(missing.toAbsolutePath().normalize().toString());
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
