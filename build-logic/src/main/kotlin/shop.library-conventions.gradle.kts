// Plain library modules (platform/*). They share the Spring Boot and specification-repository BOMs
// with the services.
plugins {
    `java-library`
    id("shop.java-conventions")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    api(platform(libs.findLibrary("spring-boot-dependencies").get()))
    api(platform(libs.findLibrary("specrepo-bom").get()))
    annotationProcessor(platform(libs.findLibrary("spring-boot-dependencies").get()))
}
