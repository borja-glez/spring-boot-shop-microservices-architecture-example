// Opt-in 100% coverage gate (line and branch). The execution data of all three suites is merged.
plugins {
    java
    jacoco
}

val libs = the<VersionCatalogsExtension>().named("libs")

jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

val suites = listOf("test", "integrationTest", "componentTest")

// Code generated from the OpenAPI contracts (rest/<version>/api and rest/<version>/model) is not measured.
val generatedCode = listOf("**/rest/*/api/**", "**/rest/*/model/**")

// An empty suite writes no execution file, so only the existing ones are merged.
val suiteExecutionData = files(
    suites.map { name ->
        tasks.named<Test>(name).map { it.extensions.getByType<JacocoTaskExtension>().destinationFile!! }
    }
).filter { it.exists() }

val mainOutput = the<SourceSetContainer>()["main"]
val measuredClasses = files(mainOutput.output.classesDirs).asFileTree.matching { exclude(generatedCode) }

val jacocoMergedReport = tasks.register<JacocoReport>("jacocoMergedReport") {
    group = "verification"
    description = "Coverage report merging the unit, integration and component suites."
    dependsOn(suites.map { tasks.named(it) })
    executionData.setFrom(suiteExecutionData)
    classDirectories.setFrom(measuredClasses)
    sourceDirectories.setFrom(mainOutput.allSource.sourceDirectories)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

val jacocoMergedVerification = tasks.register<JacocoCoverageVerification>("jacocoMergedVerification") {
    group = "verification"
    description = "Fails unless line and branch coverage are 100%."
    dependsOn(suites.map { tasks.named(it) })
    executionData.setFrom(suiteExecutionData)
    classDirectories.setFrom(measuredClasses)
    sourceDirectories.setFrom(mainOutput.allSource.sourceDirectories)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "1.0".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(jacocoMergedReport, jacocoMergedVerification)
}
