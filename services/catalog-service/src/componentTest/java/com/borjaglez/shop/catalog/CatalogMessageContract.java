package com.borjaglez.shop.catalog;

import java.nio.file.Path;
import java.util.Collection;

import com.borjaglez.cqrs.asyncapi.AsyncApiVerifier;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.borjaglez.shop.catalog.EventsTopic.Received;
import com.borjaglez.shop.testsupport.contract.AsyncApiContracts;

/**
 * The AsyncAPI document of the catalog, read with the serializer and the naming strategy of the
 * running application.
 */
class CatalogMessageContract {

  /** Relative to the module, where Gradle runs the tests. */
  static final Path DOCUMENT = Path.of("src/main/asyncapi/catalog.yaml");

  private final MessageSerializer serializer;
  private final MessageNamingStrategy naming;

  CatalogMessageContract(MessageSerializer serializer, MessageNamingStrategy naming) {
    this.serializer = serializer;
    this.naming = naming;
  }

  /** A new verifier of the document, without samples. */
  AsyncApiVerifier verifier() {
    return AsyncApiContracts.verifier(DOCUMENT, serializer, naming);
  }

  /** Checks the payloads of events, as they were on the wire, against their schemas. */
  void assertPayloadsFollowIt(Collection<? extends Received<?>> events) {
    AsyncApiVerifier verifier = verifier();
    events.forEach(event -> verifier.sample(event.name(), event.json()));
    verifier.verifyPayloads();
  }
}
