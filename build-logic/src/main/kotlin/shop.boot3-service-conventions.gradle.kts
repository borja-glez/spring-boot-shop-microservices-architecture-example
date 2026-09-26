import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

// Spring Boot 3.5 services (Jackson 2). They check that services on both Boot generations can talk
// to each other, so they must not see anything built against Boot 4: no service-support, es-kit or
// test-support, and the Boot 3 BOM for every scope. The Gradle plugin is the Boot 4 one; it only
// packages the jar and the image.
plugins {
    id("shop.java-base-conventions")
    id("org.springframework.boot")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    val boot3 = platform(libs.findLibrary("spring-boot3-dependencies").get())
    implementation(boot3)
    annotationProcessor(boot3)
    testImplementation(boot3)
    annotationProcessor(libs.findLibrary("spring-boot-configuration-processor").get())

    implementation(libs.findLibrary("spring-boot-starter-actuator").get())
    implementation(libs.findLibrary("micrometer-registry-prometheus").get())
    // Boot 3 has no OpenTelemetry starter: bridge, OTLP span exporter and OTLP meter registry.
    implementation(libs.findLibrary("micrometer-tracing-bridge-otel").get())
    implementation(libs.findLibrary("opentelemetry-exporter-otlp").get())
    implementation(libs.findLibrary("micrometer-registry-otlp").get())
    // Log records over OTLP (appender installed by the service).
    implementation(libs.findLibrary("opentelemetry-logback-appender-boot3").get())

    testImplementation(libs.findLibrary("spring-boot-starter-test").get())
    testImplementation(libs.findLibrary("archunit-junit5").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

tasks.named<BootBuildImage>("bootBuildImage") {
    usesService(imageBuilds())
    imageName.set("shop/${project.name}:${project.version}")
    environment.put("BP_JVM_VERSION", "21")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("${project.name}.jar")
}
