// Java setup shared by every JVM module, without any Spring Boot BOM: modules pick theirs
// (Boot 4 through shop.java-conventions, Boot 3 through shop.boot3-service-conventions).
plugins {
    java
    id("io.freefair.lombok")
    id("com.diffplug.spotless")
}

val libs = the<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        importOrder("java", "javax", "jakarta", "org", "com", "io")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:unchecked,deprecation,rawtypes"))
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
