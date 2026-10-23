import java.io.File

import org.gradle.api.file.FileTreeElement
import org.gradle.api.specs.Spec

/**
 * Matches the class files compiled from the sources that shop.openapi-conventions generates: a
 * class, or a class nested in it, whose source file the generator wrote under
 * `<generatedRoot>/<version>/src/main/java`. A hand-written class in a generated package does not
 * match.
 */
class GeneratedOpenApiClasses(private val generatedRoot: File) : Spec<FileTreeElement> {

    override fun isSatisfiedBy(element: FileTreeElement): Boolean {
        if (element.isDirectory || !element.name.endsWith(".class")) {
            return false
        }
        val source = element.relativePath.pathString.removeSuffix(".class").substringBefore('$') + ".java"
        return generatedRoot.listFiles().orEmpty().any { File(it, "src/main/java/$source").isFile }
    }
}
