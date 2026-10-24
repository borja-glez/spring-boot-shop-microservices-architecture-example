import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

// Contract-first REST adapters: generates the Spring MVC server interfaces and the DTOs of the
// module's OpenAPI documents, one per major version of its API.
//
//   shopOpenApi {
//       basePackage.set("com.borjaglez.shop.<service>.infrastructure.in")
//       specs {
//           register("v1") { file.set(layout.projectDirectory.file("src/main/openapi/<service>-api.v1.yaml")) }
//       }
//   }
//
// Each document is named after its major version (v1, v2...) and gets a task openApiGenerate<Version>
// that writes the interfaces to <basePackage>.rest.<version>.api and the DTOs to
// <basePackage>.rest.<version>.model, under build/generated/openapi/<version>, a source directory of
// the document's source set (`main` unless `sourceSet` names another one). Each DTO is named after its
// schema with the suffix `Dto` (ProductCard -> ProductCardDto), so it never clashes with the views and
// messages of the core; the JSON and the TypeScript types of the frontend keep the schema names. The
// generated code is not committed, formatted or measured by the coverage gate. It compiles against Spring
// MVC and Bean Validation (the webmvc or web starter and the validation starter). Boot 3 modules
// (shop.boot3-service-conventions) get Boot 3 / Jackson 2 code.
//
// The task regenerates when the document, a file in its folder or a file in
// platform/api-contracts/openapi changes. A document that `$ref`s files elsewhere lists them in
// `extraSchemaLocations`, or Gradle does not see their changes.
//
// Enum parameters (query, path, header) bind by Java constant name, Spring's default: the values
// of an enum used as a parameter must be its constant names (UPPER_SNAKE_CASE).
plugins {
    java
    id("com.diffplug.spotless")
}

interface ShopOpenApiSpec : Named {
    /** The OpenAPI document. */
    val file: RegularFileProperty

    /** Source set that compiles the generated code. */
    val sourceSet: Property<String>
}

interface ShopOpenApiExtension {
    /** Package that holds the REST adapter, such as com.borjaglez.shop.catalog.infrastructure.in. */
    val basePackage: Property<String>

    /** Spring Boot generation the code is generated for: 4, or 3. */
    val springBoot: Property<Int>

    /** One document per major version, named after it (v1, v2...). */
    val specs: NamedDomainObjectContainer<ShopOpenApiSpec>

    /** Files outside the documents' folders and platform/api-contracts/openapi that they `$ref`. */
    val extraSchemaLocations: ConfigurableFileCollection
}

val shopOpenApi = extensions.create<ShopOpenApiExtension>("shopOpenApi")
shopOpenApi.springBoot.convention(4)
pluginManager.withPlugin("shop.boot3-service-conventions") {
    shopOpenApi.springBoot.convention(3)
}
shopOpenApi.specs.configureEach {
    sourceSet.convention(SourceSet.MAIN_SOURCE_SET_NAME)
}

// Options of the `spring` generator, https://openapi-generator.tech/docs/generators/spring
fun generatorOptions(springBoot: Int): Map<String, String> {
    val common = mapOf(
        // Interfaces only: the controllers implement them and map DTOs to commands and queries.
        "interfaceOnly" to "true",
        // No default bodies: a controller that misses an operation does not compile.
        "skipDefaultInterface" to "true",
        // One interface per tag (ProductsApi, OrdersApi...) rather than per first path segment.
        "useTags" to "true",
        "useJakartaEe" to "true",
        "useBeanValidation" to "true",
        // Spring MVC validates the parameters itself; no class-level @Validated proxy.
        "useSpringBuiltInValidation" to "true",
        // Optional fields are plain nullable fields: no jackson-databind-nullable.
        "openApiNullable" to "false",
        // The contracts are served as files: no springdoc and no Swagger annotations.
        "documentationProvider" to "none",
        "annotationLibrary" to "none",
        // Null handling follows the application's JSON mapper, which writes null properties: a
        // property that can be null must say `nullable: true`, or the contract tests reject it.
        "generateJsonIncludeAnnotations" to "false",
        "generateJsonSetterNullsAnnotations" to "false",
        // Same output for the same document, so the task is cacheable.
        "hideGenerationTimestamp" to "true",
        "sourceFolder" to "src/main/java",
    )
    val generation = when (springBoot) {
        4 -> mapOf("useSpringBoot4" to "true", "useJackson3" to "true", "useJspecify" to "true")
        3 -> mapOf("useSpringBoot3" to "true")
        else -> throw GradleException("shopOpenApi.springBoot must be 3 or 4, not $springBoot")
    }
    return common + generation
}

val sourceSets = the<SourceSetContainer>()
val noSources = files()
val sharedContracts = layout.settingsDirectory.dir("platform/api-contracts/openapi")
val projectPath = path
val basePackage = shopOpenApi.basePackage.orElse(
    provider { throw GradleException("Set shopOpenApi.basePackage in $projectPath") }
)

val majorVersion = Regex("v[0-9]+")

shopOpenApi.specs.all {
    val spec = this
    val version = spec.name
    if (!majorVersion.matches(version)) {
        throw GradleException(
            "shopOpenApi.specs in $projectPath: '$version' is not a major version name such as v1 or v2"
        )
    }
    val outputDir = layout.buildDirectory.dir("generated/openapi/$version")
    val generate = tasks.register<GenerateTask>("openApiGenerate${version.replaceFirstChar(Char::titlecase)}") {
        group = "openapi"
        description = "Generates the server interfaces and DTOs of the $version OpenAPI contract."
        generatorName.set("spring")
        inputSpec.set(spec.file)
        // Regenerate when a file the document references changes (see the header).
        schemaLocations.from(
            spec.file.map { it.asFile.parentFile },
            sharedContracts,
            shopOpenApi.extraSchemaLocations,
        )
        this.outputDir.set(outputDir)
        // A forked JVM: the generator classes do not pile up in the daemon across builds.
        workerIsolation.set("process")
        apiPackage.set(basePackage.map { "$it.rest.$version.api" })
        modelPackage.set(basePackage.map { "$it.rest.$version.model" })
        modelNameSuffix.set("Dto")
        configOptions.set(shopOpenApi.springBoot.map(::generatorOptions))
        // Interfaces and models only: no README, pom, ApiUtil or enum converter configuration (hence
        // the binding of enum parameters by constant name).
        globalProperties.set(mapOf("apis" to "", "models" to ""))
        generateApiTests.set(false)
        generateModelTests.set(false)
        generateApiDocumentation.set(false)
        generateModelDocumentation.set(false)
        // A schema removed from the contract must not leave its class behind.
        cleanupOutput.set(true)
    }
    val generatedSources = files(outputDir.map { it.dir("src/main/java") }).builtBy(generate)
    sourceSets.configureEach {
        val target = name
        java.srcDir(spec.sourceSet.map { if (it == target) generatedSources else noSources })
        tasks.named(compileJavaTaskName) {
            dependsOn(spec.sourceSet.map { if (it == target) listOf(generate) else emptyList() })
        }
    }
}

// The hand-written sources only: an explicit target keeps the generated directories out, and
// spotlessCheck/spotlessApply from running the generation to list the source sets.
spotless {
    java {
        target("src/*/java/**/*.java")
    }
}
