plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.graalvm.native.gradle.plugin)
    implementation(libs.hibernate.gradle.plugin)
    implementation(libs.lombok.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
}
