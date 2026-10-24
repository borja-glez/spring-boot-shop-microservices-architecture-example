plugins {
    id("shop.boot-service-conventions")
}

description = "Single public entry point: routes /api/** to the services"

dependencies {
    implementation(libs.spring.cloud.gateway.server.webmvc)
    implementation(libs.spring.boot.starter.validation)
    runtimeOnly(libs.webjars.swagger.ui)

    testImplementation(libs.spring.boot.starter.restclient)
}

// Native build: AOT processing reads class metadata with ASM, ignoring the Java 24+ entries of
// multi-release jars. With the Class-File API reader Spring uses on Java 24 and later, the hints
// processor of Spring Cloud Gateway 5.0.3 registers compiler-generated classes and fails
// (spring-cloud/spring-cloud-gateway#4275, fixed in 5.0.4).
tasks.withType<org.springframework.boot.gradle.tasks.aot.ProcessAot>().configureEach {
    jvmArgs("-Djdk.util.jar.enableMultiRelease=false")
}

// The contracts page serves every OpenAPI/AsyncAPI document of the repository under
// /api/docs/contracts/, keeping the repository layout so relative $refs resolve over HTTP.
// One file tree per source root keeps the other directories of the repository out of the inputs.
val contractsStaging = layout.buildDirectory.dir("generated/contracts")
val swaggerUiVersion = libs.versions.swagger.ui.get()
val asyncApiWebComponentVersion = libs.versions.asyncapi.web.component.get()

val collectContracts by tasks.registering(Sync::class) {
    val repositoryRoot = rootProject.layout.projectDirectory
    from(
        repositoryRoot.dir("services").asFileTree.matching {
            include("*/src/main/openapi/**", "*/src/main/asyncapi/**")
        }
    ) {
        into("services")
    }
    from(
        repositoryRoot.dir("platform").asFileTree.matching {
            include(
                "api-contracts/**",
                "*/src/main/openapi/**",
                "contracts/src/main/resources/schemas/**",
            )
        }
    ) {
        into("platform")
    }
    into(contractsStaging.map { it.dir("api/docs/contracts") })

    val contractsRoot = contractsStaging.map { it.dir("api/docs/contracts") }
    doLast {
        val root = contractsRoot.get().asFile
        // Only documents with operations (a non-empty top-level paths or channels mapping) are
        // listed; component libraries are still served so that the $refs of the listed documents
        // resolve. SnakeYAML is on the build classpath through the OpenAPI generator plugin.
        val yaml = org.yaml.snakeyaml.Yaml(org.yaml.snakeyaml.constructor.SafeConstructor(org.yaml.snakeyaml.LoaderOptions()))
        fun hasEntries(document: Map<*, *>, key: String) = (document[key] as? Map<*, *>).orEmpty().isNotEmpty()
        val entries =
            root.walkTopDown()
                .filter { it.isFile && (it.extension == "yaml" || it.extension == "yml") }
                .mapNotNull { file ->
                    val document = file.reader().use { yaml.load<Any?>(it) } as? Map<*, *> ?: emptyMap<Any, Any>()
                    val kind =
                        when {
                            "openapi" in document && hasEntries(document, "paths") -> "openapi"
                            "asyncapi" in document && hasEntries(document, "channels") -> "asyncapi"
                            else -> null
                        }
                    kind?.let { mapOf("kind" to it, "path" to file.relativeTo(root).invariantSeparatorsPath) }
                }
                .sortedBy { it["path"] }
                .toList()
        root.resolve("index.json").writeText(groovy.json.JsonOutput.toJson(entries) + "\n")
    }
}

tasks.processResources {
    from(files(contractsStaging).builtBy(collectContracts)) { into("static") }
    // The page takes the Swagger UI and AsyncAPI versions from the version catalog.
    val pageVersions =
        mapOf("swaggerUiVersion" to swaggerUiVersion, "asyncapiWebComponentVersion" to asyncApiWebComponentVersion)
    inputs.properties(pageVersions)
    filesMatching("static/api/docs/index.html") {
        filter<org.apache.tools.ant.filters.ReplaceTokens>("tokens" to pageVersions)
    }
}
