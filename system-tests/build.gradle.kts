plugins {
    id("shop.java-conventions")
}

description = "Journeys and resilience/stress tests against a running platform (Compose or Kubernetes), through the gateway"

dependencies {
    testImplementation(libs.jackson.databind)
    testImplementation(libs.awaitility)
}

// The tests need a deployed platform, so the regular build skips them. Run them with
//   ./gradlew :system-tests:systemTest [-Pshop.baseUrl=http://localhost:8080]
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("system")
    }
}

tasks.register<Test>("systemTest") {
    description = "Runs the journeys and resilience/stress tests against a running platform."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("system")
    }
    systemProperty(
        "shop.baseUrl",
        providers.gradleProperty("shop.baseUrl").getOrElse("http://localhost:8080"),
    )
    outputs.upToDateWhen { false }
}
