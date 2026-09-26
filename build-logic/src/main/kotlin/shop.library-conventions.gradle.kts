// Plain library modules (platform/*). They share the Spring Boot BOM with the services.
plugins {
    `java-library`
    id("shop.java-conventions")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    api(platform(libs.findLibrary("spring-boot-dependencies").get()))
    annotationProcessor(platform(libs.findLibrary("spring-boot-dependencies").get()))
}
