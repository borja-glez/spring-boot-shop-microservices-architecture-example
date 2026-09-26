plugins {
    id("shop.boot-service-conventions")
    id("shop.hibernate-enhancement-conventions")
}

description = "Reporting: sales, products, rejections and customers from its own projections of the event stream"

dependencies {
    implementation(project(":platform:contracts"))
    implementation(project(":platform:es-kit"))
    implementation(libs.cqrs.boot4.starter)
    implementation(libs.cqrs.kafka)
    implementation(libs.spring.boot.starter.kafka)
    implementation(libs.specrepo.boot4.starter)
    implementation(libs.specrepo.http)

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.validation)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.database.postgresql)

    testImplementation(libs.spring.boot.starter.webmvc.test)
}
