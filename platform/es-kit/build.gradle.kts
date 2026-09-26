plugins {
    id("shop.library-conventions")
}

description = "Event sourcing kit: event store that doubles as outbox, relay and idempotent consumers"

dependencies {
    api(project(":platform:service-support"))
    api(libs.cqrs.core)
    api(libs.specrepo.boot4.starter)
    api(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.autoconfigure)
    // Only for KafkaOutboxDestination; services that use it bring the Kafka module themselves.
    compileOnly(libs.cqrs.kafka)
    compileOnly(libs.spring.kafka)
    api(libs.micrometer.core)

    annotationProcessor(libs.spring.boot.autoconfigure.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(project(":platform:test-support"))
    testImplementation(libs.cqrs.boot4.starter)
    testImplementation(libs.cqrs.kafka)
    testImplementation(libs.spring.kafka)
    testImplementation(libs.spring.boot.starter.jackson)
    testImplementation(libs.spring.boot.starter.flyway)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.flyway.database.postgresql)
}
