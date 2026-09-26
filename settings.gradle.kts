pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
    }
}

rootProject.name = "spring-boot-shop-microservices-architecture-example"

// Optionally build against local checkouts of the libraries (to try unreleased library changes):
// with -Pshop.localLibs=true they are substituted for their Maven coordinates.
val localLibs = providers.gradleProperty("shop.localLibs").map(String::toBoolean).getOrElse(false)
if (localLibs) {
    includeBuild(providers.gradleProperty("shop.cqrsPath").getOrElse("../spring-boot-cqrs"))
    includeBuild(
        providers.gradleProperty("shop.specrepoPath")
            .getOrElse("../spring-boot-specification-repository")
    )
}

include(
    ":platform:contracts",
    ":platform:es-kit",
    ":platform:service-support",
    ":platform:test-support",
    ":services:catalog-service",
    ":services:orders-service",
    ":services:inventory-service",
    ":services:payments-service",
    ":services:notifications-service",
    ":services:reporting-service",
    ":services:gateway-service",
    ":system-tests",
)
