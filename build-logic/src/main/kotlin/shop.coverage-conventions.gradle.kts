// Opt-in 100% coverage gate (line and branch). The execution data of all three suites is merged.
plugins {
    id("shop.java-base-conventions")
    jacoco
}

val libs = the<VersionCatalogsExtension>().named("libs")

jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

val suites = listOf("test", "integrationTest", "componentTest")

// Code generated from the OpenAPI contracts (shop.openapi-conventions) is not measured: a class is
// left out only when the generator wrote its source, so a hand-written class in a generated
// package is still measured.
val generatedCode = GeneratedOpenApiClasses(layout.buildDirectory.dir("generated/openapi").get().asFile)

// JaCoCo skips execution files that do not exist, so an empty suite needs no special case.
val suiteExecutionData = files(
    suites.map { name ->
        tasks.named<Test>(name).map { it.extensions.getByType<JacocoTaskExtension>().destinationFile!! }
    }
)

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
            // Checked method by method, which leaves out the methods Hibernate's bytecode enhancement
            // writes into the entities (shop.hibernate-enhancement-conventions): they are named
            // $$_hibernate_*, have no source line and are run by Hibernate, not by the service. The
            // pattern spells each $ as ?, because the Ant task behind the check reads $$ as $.
            element = "METHOD"
            excludes = listOf("*.??_hibernate_*")
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
