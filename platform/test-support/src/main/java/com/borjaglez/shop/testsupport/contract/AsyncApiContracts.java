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
 * AsyncApiContracts.verifier(
 *         "platform/api-contracts/asyncapi/catalog.yaml", serializer, naming)
 *     .sample(new ProductPublished(...))
 *     .verifyPayloads();
 * }</pre>
 *
 * <p>The document is a path relative to the repository root, which the build passes to every test
 * suite in the {@code shop.repoRoot} system property, so the same path works from any module. The
 * document is read from the file system, and the relative {@code $ref}s to its payload schemas
 * resolve against its location.
 */
public final class AsyncApiContracts {

  /** System property holding the absolute path of the repository root. */
  public static final String REPO_ROOT_PROPERTY = "shop.repoRoot";

  private AsyncApiContracts() {}

  /**
   * Loads the AsyncAPI 3 document and creates a verifier for it.
   *
   * @param asyncApiFile path of the document relative to the repository root
   * @param serializer the serializer the service sends messages with
   * @param naming the naming strategy that gives commands, queries and events their logical name
   * @throws IllegalStateException if {@code shop.repoRoot} is not set
   * @throws IllegalArgumentException if the document does not exist or is not a valid contract
   */
  public static AsyncApiVerifier verifier(
      String asyncApiFile, MessageSerializer serializer, MessageNamingStrategy naming) {
    Objects.requireNonNull(asyncApiFile, "asyncApiFile must not be null");
    Path root = repoRoot();
    Path document = root.resolve(asyncApiFile).normalize();
    if (!Files.isRegularFile(document)) {
      throw new IllegalArgumentException(
          "No AsyncAPI document at %s (%s relative to %s)".formatted(document, asyncApiFile, root));
    }
    return AsyncApiVerifier.of(AsyncApiContract.load(document), serializer, naming);
  }

  private static Path repoRoot() {
    String root = System.getProperty(REPO_ROOT_PROPERTY);
    if (root == null || root.isBlank()) {
      throw new IllegalStateException(
          "The system property %s is not set; the Gradle test suites pass the repository root in it"
              .formatted(REPO_ROOT_PROPERTY));
    }
    return Path.of(root);
  }
}
