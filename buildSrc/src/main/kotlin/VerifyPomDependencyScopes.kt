import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

public abstract class VerifyPomDependencyScopes : DefaultTask() {

    @get:InputFile
    public abstract val pomFile: RegularFileProperty

    @get:Input
    public abstract val groupId: Property<String>

    @get:Input
    public abstract val expectedArtifactIds: SetProperty<String>

    @TaskAction
    fun verify() {
        val file = pomFile.get().asFile
        check(file.exists()) {
            "POM file does not exist: ${file.absolutePath}"
        }

        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)

        val targetGroupId = groupId.get()
        val expected = expectedArtifactIds.get()
        val dependencies = document.getElementsByTagName("dependency")

        val foundArtifacts = mutableMapOf<String, String>()

        for (i in 0 until dependencies.length) {
            val dependency = dependencies.item(i) as? Element ?: continue
            val depGroupId = dependency.directChild("groupId")?.textContent
            val depArtifactId = dependency.directChild("artifactId")?.textContent
            val scope = dependency.directChild("scope")?.textContent ?: ""

            if (depGroupId == targetGroupId && depArtifactId != null) {
                if (depArtifactId in foundArtifacts) {
                    throw GradleException(
                        "Duplicated dependency entry found in $file for $targetGroupId:$depArtifactId"
                    )
                }
                foundArtifacts[depArtifactId] = scope
            }
        }

        for (expectedArtifactId in expected) {
            val scope = foundArtifacts[expectedArtifactId]
            if (scope == null) {
                throw GradleException(
                    "Missing expected dependency $targetGroupId:$expectedArtifactId in $file"
                )
            }
            if (scope != "compile") {
                throw GradleException(
                    "Expected dependency $targetGroupId:$expectedArtifactId in $file must have scope 'compile' but was '$scope'"
                )
            }
        }
    }

    private fun Element.directChild(tagName: String): Element? =
        (0 until childNodes.length)
            .asSequence()
            .map(childNodes::item)
            .filterIsInstance<Element>()
            .firstOrNull { it.tagName == tagName }
}
