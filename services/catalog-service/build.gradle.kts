plugins {
    id("shop.boot-service-conventions")
    id("shop.hibernate-enhancement-conventions")
    id("shop.openapi-conventions")
    id("shop.coverage-conventions")
    id("shop.mutation-conventions")
}

description = "Product catalog: publishing, pricing and public search"

dependencies {
    implementation(project(":platform:contracts"))
    implementation(project(":platform:es-kit"))
    implementation(libs.cqrs.boot4.starter)
    implementation(libs.cqrs.kafka)
    implementation(libs.cqrs.rabbitmq)
    implementation(libs.spring.boot.starter.kafka)
    implementation(libs.spring.boot.starter.amqp)
    implementation(libs.specrepo.boot4.starter)
    implementation(libs.specrepo.http)

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.validation)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.database.postgresql)

    // Every suite also gets platform/test-support from shop.boot-service-conventions.
    testImplementation(project(":platform:architecture-rules"))
    testImplementation(libs.cqrs.test)
    integrationTestImplementation(libs.cqrs.asyncapi.test)
    integrationTestImplementation(libs.spring.boot.starter.webmvc.test)
    integrationTestImplementation(libs.spring.boot.starter.data.jpa.test)
    componentTestImplementation(libs.cqrs.asyncapi.test)
    componentTestImplementation(libs.spring.boot.starter.restclient)
}

shopOpenApi {
    basePackage.set("com.borjaglez.shop.catalog.infrastructure.in")
    specs {
        register("v1") {
            file.set(layout.projectDirectory.file("src/main/openapi/catalog-api.v1.yaml"))
        }
    }
}

// The core is what the mutation gate guards: the domain model and the use cases.
shopMutation {
    targetPackages.set(
        listOf(
            "com.borjaglez.shop.catalog.domain.*",
            "com.borjaglez.shop.catalog.application.*",
        ),
    )
}
