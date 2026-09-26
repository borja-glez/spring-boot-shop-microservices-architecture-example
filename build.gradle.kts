plugins {
    base
}

description = "Marketplace microservices example for spring-boot-cqrs and spring-boot-specification-repository"

allprojects {
    group = property("group") as String
    version = property("version") as String
}

// Every project under services/ is a deployable Spring Boot service.
val services = subprojects.filter { it.parent?.path == ":services" }.map { it.path }

tasks.register("buildImages") {
    group = "distribution"
    description = "Builds the OCI image of every service (JVM, or GraalVM native with -Pnative)."
    dependsOn(services.map { "$it:bootBuildImage" })
}
