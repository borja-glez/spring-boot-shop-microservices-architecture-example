import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * One image build at a time. The build runs projects in parallel, and several Paketo builds at
 * once (native ones above all) exhaust Docker Desktop: the daemon drops the connection ("pipe has
 * ended") and every image fails.
 */
abstract class ImageBuilds : BuildService<BuildServiceParameters.None>

fun Project.imageBuilds(): Provider<ImageBuilds> =
    gradle.sharedServices.registerIfAbsent("imageBuilds", ImageBuilds::class.java) {
        maxParallelUsages.set(1)
    }
