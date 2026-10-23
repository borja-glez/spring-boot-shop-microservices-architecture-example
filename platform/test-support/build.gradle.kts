plugins {
    id("shop.library-conventions")
    id("shop.openapi-conventions")
}

description = "Test fixtures shared by the services: Testcontainers, architecture rules and contract checks (OpenAPI and AsyncAPI)"

dependencies {
    api(libs.spring.boot.starter.test)
    api(libs.spring.boot.testcontainers)
    api(libs.testcontainers.postgresql)
    api(libs.testcontainers.kafka)
    api(libs.testcontainers.rabbitmq)
    api(libs.testcontainers.junit.jupiter)
    api(project(":platform:architecture-rules"))
    api(libs.archunit.junit5)
    api(libs.cqrs.core)
    api(libs.cqrs.asyncapi.test)
    api(libs.awaitility)
    implementation(libs.openapi.request.validator.mockmvc)
    // TestClasspathConvertersAutoConfiguration, active only in web applications.
    compileOnly(libs.spring.boot.starter.webmvc)

    // OpenApiContractTest checks the shop's own problem rendering against common.v1.yaml.
    testImplementation(project(":platform:service-support"))
    testImplementation(libs.spring.boot.starter.validation)
    testImplementation(libs.specrepo.core)
    testImplementation(libs.specrepo.http)
    testImplementation(libs.jackson.databind)
}

// Contract of the controllers of OpenApiContractTest: its interfaces and DTOs are test code.
shopOpenApi {
    basePackage.set("com.borjaglez.shop.testsupport.contract")
    specs {
        register("v1") {
            file.set(layout.projectDirectory.file("src/test/resources/openapi/widgets-api.v1.yaml"))
            sourceSet.set("test")
        }
    }
}
