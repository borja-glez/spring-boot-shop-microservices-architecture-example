import info.solidsoft.gradle.pitest.PitestPluginExtension

// Opt-in PIT mutation gate over the unit suite (`test`). Modules set the packages to mutate:
//   shopMutation { targetPackages.set(listOf("com.borjaglez.shop.<module>.*")) }
plugins {
    java
    id("info.solidsoft.pitest")
}

val libs = the<VersionCatalogsExtension>().named("libs")

interface ShopMutationExtension {
    val targetPackages: ListProperty<String>
    val threshold: Property<Int>
    val threads: Property<Int>
}

val shopMutation = extensions.create<ShopMutationExtension>("shopMutation")
shopMutation.threshold.convention(90)
shopMutation.threads.convention(2)

configure<PitestPluginExtension> {
    pitestVersion.set(libs.findVersion("pitest").get().requiredVersion)
    junit5PluginVersion.set(libs.findVersion("pitest-junit5").get().requiredVersion)
    targetClasses.set(shopMutation.targetPackages)
    mutationThreshold.set(shopMutation.threshold)
    testSourceSets.set(listOf(the<SourceSetContainer>()["test"]))
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    threads.set(shopMutation.threads)
}

tasks.named("check") {
    dependsOn("pitest")
}
