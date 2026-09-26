plugins {
    id("shop.library-conventions")
}

description = "Cross-cutting web support shared by every service: problem details, correlation ids, current user"

dependencies {
    api(libs.spring.boot.starter.webmvc)

    // Optional integrations, activated only when the service has them on its classpath.
    compileOnly(libs.cqrs.core)
    compileOnly(libs.specrepo.core)
    compileOnly(libs.specrepo.http)
    compileOnly(libs.jakarta.validation.api)
    compileOnly(libs.spring.tx)
    compileOnly(libs.opentelemetry.exporter.otlp)
    compileOnly(libs.opentelemetry.logback.appender)
    compileOnly(libs.micrometer.tracing)

    annotationProcessor(libs.spring.boot.autoconfigure.processor)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.validation)
    testImplementation(libs.spring.boot.data.commons)
    testImplementation(libs.spring.tx)
    testImplementation(libs.cqrs.core)
    testImplementation(libs.specrepo.core)
    testImplementation(libs.specrepo.http)
    testImplementation(libs.opentelemetry.exporter.otlp)
    testImplementation(libs.opentelemetry.logback.appender)
    testImplementation(libs.micrometer.tracing)
    testImplementation(libs.micrometer.tracing.bridge.otel)
}
