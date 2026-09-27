plugins {
    id("shop.boot-service-conventions")
}

description = "Single public entry point: routes /api/** to the services"

dependencies {
    implementation(libs.spring.cloud.gateway.server.webmvc)
    implementation(libs.spring.boot.starter.validation)

    testImplementation(libs.spring.boot.starter.restclient)
}

// Native build: AOT processing reads class metadata with ASM, ignoring the Java 24+ entries of
// multi-release jars. With the Class-File API reader Spring uses on Java 24 and later, the hints
// processor of Spring Cloud Gateway 5.0.3 registers compiler-generated classes and fails
// (spring-cloud/spring-cloud-gateway#4275, fixed in 5.0.4).
tasks.withType<org.springframework.boot.gradle.tasks.aot.ProcessAot>().configureEach {
    jvmArgs("-Djdk.util.jar.enableMultiRelease=false")
}
