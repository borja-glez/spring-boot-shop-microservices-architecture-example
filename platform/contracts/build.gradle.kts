plugins {
    `java-library`
    // No Spring Boot BOM: contracts are read by Boot 3 and Boot 4 services alike.
    id("shop.java-base-conventions")
}

description = "Messages exchanged between services. Framework-free apart from the spring-boot-cqrs API."

dependencies {
    api(libs.cqrs.api)

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
