plugins {
    id("shop.boot-service-conventions")
}

description = "Single public entry point: routes /api/** to the services"

dependencies {
    implementation(libs.spring.cloud.gateway.server.webmvc)
    implementation(libs.spring.boot.starter.validation)

    testImplementation(libs.spring.boot.starter.restclient)
}
