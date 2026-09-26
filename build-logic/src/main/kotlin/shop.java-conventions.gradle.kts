// Java modules on the Spring Boot 4 BOM: platform libraries and Boot 4 services.
plugins {
    id("shop.java-base-conventions")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    testImplementation(platform(libs.findLibrary("spring-boot-dependencies").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testImplementation(libs.findLibrary("assertj-core").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}
