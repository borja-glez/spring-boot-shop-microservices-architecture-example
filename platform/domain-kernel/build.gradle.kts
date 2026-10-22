plugins {
    `java-library`
    // No Spring Boot BOM and no runtime dependencies: the kernel is shared by Boot 3 and Boot 4 services.
    id("shop.java-base-conventions")
}

description = "Framework-free DDD building blocks: aggregates, domain events, domain errors and query value objects."

dependencies {
    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
