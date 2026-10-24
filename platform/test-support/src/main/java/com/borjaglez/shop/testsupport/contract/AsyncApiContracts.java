package com.borjaglez.shop.testsupport.contract;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.borjaglez.cqrs.asyncapi.AsyncApiContract;
import com.borjaglez.cqrs.asyncapi.AsyncApiVerifier;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;

/**
 * Entry point to check the messages of a service against its AsyncAPI document.
 *
 * <pre>{@code
 * AsyncApiContracts.verifier(Path.of("src/main/asyncapi/catalog.yaml"), serializer, naming)
 *     .sample(new ProductPublished(...))
 *     .verifyPayloads();
 * }</pre>
 *
 * <p>A service keeps its document in {@code services/<service>/src/main/asyncapi/<service>.yaml}.
 * The path is relative to the module, as for {@link OpenApiContract#of(Path)}: Gradle runs the
 * tests of a module in its directory. The document is read from the file system, so its relative
 * {@code $ref}s, to the payload schemas in {@code platform/contracts} and to the shared components
 * of {@code platform/api-contracts/asyncapi/common.yaml}, resolve against its location.
 */
public final class AsyncApiContracts {

  private AsyncApiContracts() {}

  /**
   * Loads the AsyncAPI 3 document and creates a verifier for it.
   *
   * @param asyncApiFile path of the document, relative to the module directory
   * @param serializer the serializer the service sends messages with
   * @param naming the naming strategy that gives commands, queries and events their logical name
   * @throws IllegalArgumentException if the document does not exist or is not a valid contract
   */
  public static AsyncApiVerifier verifier(
      Path asyncApiFile, MessageSerializer serializer, MessageNamingStrategy naming) {
    Objects.requireNonNull(asyncApiFile, "asyncApiFile must not be null");
    Path document = asyncApiFile.toAbsolutePath().normalize();
    if (!Files.isRegularFile(document)) {
      throw new IllegalArgumentException("No AsyncAPI document at " + document);
    }
    return AsyncApiVerifier.of(AsyncApiContract.load(document), serializer, naming);
  }
}
