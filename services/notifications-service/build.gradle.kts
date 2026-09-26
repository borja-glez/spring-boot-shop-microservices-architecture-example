plugins {
    id("shop.boot3-service-conventions")
}

description = "Notifications: live order and payment notices on Spring Boot 3.5 (Jackson 2), fed by the Boot 4 services' events"

dependencies {
    implementation(project(":platform:contracts"))
    implementation(libs.cqrs.boot3.starter)
    implementation(libs.cqrs.kafka)
    implementation(libs.spring.kafka)
    implementation(libs.specrepo.boot3.starter)
    implementation(libs.specrepo.http)

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.flyway.core)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.database.postgresql)

    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers1.postgresql)
    testImplementation(libs.testcontainers1.kafka)
    testImplementation(libs.testcontainers1.junit.jupiter)
    testImplementation(libs.awaitility)
}
