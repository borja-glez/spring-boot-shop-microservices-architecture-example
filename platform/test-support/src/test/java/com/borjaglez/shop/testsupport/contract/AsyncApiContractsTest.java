package com.borjaglez.shop.testsupport.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

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

  // As deep in the repository as services/<service>/src/main/asyncapi/<service>.yaml.
  private static final Path SERVICE_LIKE_CONTRACT =
      Path.of("src/test/resources/widgets-kafka.asyncapi.yaml");

  private static final Path SHARED_COMPONENTS = Path.of("../api-contracts/asyncapi/common.yaml");

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
    var common = AsyncApiContract.load(SHARED_COMPONENTS);

    assertThat(common.messages()).isEmpty();
  }

  @Test
  void aServiceDocumentReachesTheSharedComponents() throws IOException {
    assertThatCode(
            () ->
                AsyncApiContracts.verifier(SERVICE_LIKE_CONTRACT, serializer, naming)
                    .sample(new WidgetCreated(UUID.randomUUID(), "Sprocket"))
                    .verifyPayloads())
        .doesNotThrowAnyException();

    String trait =
        "../../../../../platform/api-contracts/asyncapi/common.yaml"
            + "#/components/messageTraits/KafkaEvent";
    List<String> references = externalReferences(read(SERVICE_LIKE_CONTRACT));
    assertThat(references).contains(trait);
    for (String reference : references) {
      assertThat(resolve(SERVICE_LIKE_CONTRACT, reference)).as(reference).isNotNull();
    }
    assertThat(resolve(SERVICE_LIKE_CONTRACT, trait))
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .containsEntry("contentType", "application/json");
    // The example of common.yaml is the path a service document uses.
    assertThat(Files.readString(SHARED_COMPONENTS)).contains("$ref: '" + trait + "'");
  }

  @Test
  void failsClearlyWhenTheFileDoesNotExist() {
    Path missing = Path.of("nope/missing.yaml");

    assertThatThrownBy(() -> AsyncApiContracts.verifier(missing, serializer, naming))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(missing.toAbsolutePath().normalize().toString());
  }

  private static Object read(Path document) throws IOException {
    try (Reader reader = Files.newBufferedReader(document)) {
      return new Yaml().load(reader);
    }
  }

  /** The {@code $ref}s of the document that point to another file. */
  private static List<String> externalReferences(Object node) {
    List<String> references = new ArrayList<>();
    if (node instanceof Map<?, ?> map) {
      map.forEach(
          (key, value) -> {
            if ("$ref".equals(key) && !value.toString().startsWith("#")) {
              references.add(value.toString());
            } else {
              references.addAll(externalReferences(value));
            }
          });
    } else if (node instanceof List<?> list) {
      list.forEach(element -> references.addAll(externalReferences(element)));
    }
    return references;
  }

  /** The node a relative {@code $ref} of the document points to, or null if there is none. */
  private static Object resolve(Path document, String reference) throws IOException {
    int hash = reference.indexOf('#');
    String file = hash < 0 ? reference : reference.substring(0, hash);
    Path target = document.toAbsolutePath().getParent().resolve(file).normalize();
    if (!Files.isRegularFile(target)) {
      return null;
    }
    Object node = read(target);
    String pointer = hash < 0 ? "" : reference.substring(hash + 1);
    for (String token : pointer.split("/")) {
      if (token.isEmpty()) {
        continue;
      }
      if (!(node instanceof Map<?, ?> map)) {
        return null;
      }
      node = map.get(token.replace("~1", "/").replace("~0", "~"));
    }
    return node;
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
