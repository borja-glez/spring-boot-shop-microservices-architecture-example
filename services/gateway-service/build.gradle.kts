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
val repositoryRoot: Directory = rootProject.layout.projectDirectory
val contractsStaging = layout.buildDirectory.dir("generated/contracts")

val collectContracts by tasks.registering(Sync::class) {
    from(
        repositoryRoot.asFileTree.matching {
            include(
                "services/*/src/main/openapi/**",
                "services/*/src/main/asyncapi/**",
                "platform/api-contracts/**",
                "platform/service-support/src/main/openapi/**",
                "platform/contracts/src/main/resources/schemas/**",
            )
            // Keep Gradle's own output out of the inputs.
            exclude("**/build/**")
        }
    )
    into(contractsStaging.map { it.dir("api/docs/contracts") })

    val contractsRoot = contractsStaging.map { it.dir("api/docs/contracts") }
    doLast {
        val root = contractsRoot.get().asFile.toPath()
        val entries =
            root.toFile()
                .walkTopDown()
                .filter { it.isFile && (it.extension == "yaml" || it.extension == "yml") }
                .mapNotNull { file ->
                    val kind =
                        file.useLines { lines ->
                            lines
                                .firstOrNull { it.startsWith("openapi:") || it.startsWith("asyncapi:") }
                                ?.substringBefore(':')
                        }
                    kind?.let { it to root.relativize(file.toPath()).joinToString("/") }
                }
                .sortedBy { it.second }
                .toList()
        val json =
            entries.joinToString(",\n", "[\n", "\n]\n") { (kind, path) ->
                "  {\"kind\":\"$kind\",\"path\":\"$path\"}"
            }
        root.resolve("index.json").toFile().writeText(if (entries.isEmpty()) "[]\n" else json)
    }
}

tasks.processResources {
    from(files(contractsStaging).builtBy(collectContracts)) { into("static") }
}
