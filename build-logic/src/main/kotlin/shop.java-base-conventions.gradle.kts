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

// Three suites: `test` (unit), `integrationTest` (slices, src/integrationTest) and `componentTest`
// (src/componentTest). The two extra ones test the main classes, inherit the unit suite's
// dependencies and see its classes.
testing {
    suites {
        listOf("integrationTest", "componentTest").forEach { name ->
            register<JvmTestSuite>(name) {
                dependencies {
                    implementation(project())
                }
                sources {
                    java.setSrcDirs(listOf("src/$name/java"))
                    resources.setSrcDirs(listOf("src/$name/resources"))
                }
            }
        }
    }
}

listOf("integrationTest", "componentTest").forEach { name ->
    configurations.named("${name}Implementation") {
        extendsFrom(configurations.testImplementation.get())
    }
    configurations.named("${name}RuntimeOnly") {
        extendsFrom(configurations.testRuntimeOnly.get())
    }
    dependencies.add("${name}Implementation", sourceSets.test.get().output)
    tasks.named<Test>(name) {
        description = "Runs the $name suite."
        group = "verification"
        useJUnitPlatform()
        shouldRunAfter(tasks.named("test"))
    }
}

tasks.named("check") {
    dependsOn("integrationTest", "componentTest")
}

// Contract tests read their documents from the file system (OpenApiContract, AsyncApiContracts and
// the files their relative $refs reach), not from the classpath, so the documents are declared as
// inputs of every test task: a contract change runs the tests again instead of reusing an
// up-to-date or cached result. Relative path sensitivity keeps the tasks relocatable.
val sharedApiContracts = layout.settingsDirectory.dir("platform/api-contracts")
val messageSchemas = layout.settingsDirectory.dir("platform/contracts/src/main/resources/schemas")
val ownOpenApiContracts = layout.projectDirectory.dir("src/main/openapi")
val ownAsyncApiContracts = layout.projectDirectory.dir("src/main/asyncapi")

tasks.withType<Test>().configureEach {
    inputs.files(ownOpenApiContracts)
        .withPropertyName("openApiContracts")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(ownAsyncApiContracts)
        .withPropertyName("asyncApiContracts")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(sharedApiContracts)
        .withPropertyName("sharedApiContracts")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(messageSchemas)
        .withPropertyName("messageSchemas")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    useJUnitPlatform()
    maxParallelForks = 1
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
