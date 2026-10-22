plugins {
    id("shop.library-conventions")
    id("shop.openapi-conventions")
}

description = "Test fixtures shared by the services: Testcontainers, architecture rules and contract checks"

dependencies {
    api(libs.spring.boot.starter.test)
    api(libs.spring.boot.testcontainers)
    api(libs.testcontainers.postgresql)
    api(libs.testcontainers.kafka)
    api(libs.testcontainers.rabbitmq)
    api(libs.testcontainers.junit.jupiter)
    api(project(":platform:architecture-rules"))
    api(libs.archunit.junit5)
    api(libs.awaitility)
    implementation(libs.openapi.request.validator.mockmvc)

    // OpenApiContractTest checks the shop's own problem rendering against common.v1.yaml.
    testImplementation(project(":platform:service-support"))
    testImplementation(libs.spring.boot.starter.validation)
    testImplementation(libs.specrepo.core)
    testImplementation(libs.specrepo.http)
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
