plugins {
    id("shop.library-conventions")
}

description = "Test fixtures shared by the services: Testcontainers and architecture rules"

dependencies {
    api(libs.spring.boot.starter.test)
    api(libs.spring.boot.testcontainers)
    api(libs.testcontainers.postgresql)
    api(libs.testcontainers.kafka)
    api(libs.testcontainers.rabbitmq)
    api(libs.testcontainers.junit.jupiter)
    api(libs.archunit.junit5)
    api(libs.awaitility)
}
