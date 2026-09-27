import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

// Spring Boot 4 microservices (services/*). Pass -Pnative to build GraalVM native images.
plugins {
    id("shop.java-conventions")
    id("org.springframework.boot")
}

val libs = the<VersionCatalogsExtension>().named("libs")
val nativeBuild = providers.gradleProperty("native").isPresent

if (nativeBuild) {
    apply(plugin = "org.graalvm.buildtools.native")
}

dependencies {
    implementation(platform(libs.findLibrary("spring-boot-dependencies").get()))
    implementation(platform(libs.findLibrary("spring-cloud-dependencies").get()))
    annotationProcessor(platform(libs.findLibrary("spring-boot-dependencies").get()))
    annotationProcessor(libs.findLibrary("spring-boot-configuration-processor").get())

    implementation(project(":platform:service-support"))
    implementation(libs.findLibrary("spring-boot-starter-actuator").get())
    implementation(libs.findLibrary("micrometer-registry-prometheus").get())
    // Tracing, metrics and logs over OTLP; exports only when OTEL_EXPORTER_OTLP_ENDPOINT is set (service-support).
    implementation(libs.findLibrary("spring-boot-starter-opentelemetry").get())
    // Log records over OTLP, next to traces and metrics (installed by service-support).
    implementation(libs.findLibrary("opentelemetry-logback-appender").get())

    testImplementation(project(":platform:test-support"))
    testImplementation(libs.findLibrary("spring-boot-starter-test").get())
    testImplementation(libs.findLibrary("archunit-junit5").get())
}

tasks.named<BootBuildImage>("bootBuildImage") {
    usesService(imageBuilds())
    val suffix = if (nativeBuild) "-native" else ""
    imageName.set("shop/${project.name}:${project.version}$suffix")
    // Java 25 runtime on the JVM; GraalVM for JDK 25 for native images.
    environment.put("BP_JVM_VERSION", "25")
    // SIGQUIT prints the threads of a native image, as it does on the JVM.
    if (nativeBuild) {
        environment.put("BP_NATIVE_IMAGE_BUILD_ARGUMENTS", "--enable-monitoring=threaddump")
    }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("${project.name}.jar")
}
