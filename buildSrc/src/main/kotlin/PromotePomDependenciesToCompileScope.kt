import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.w3c.dom.Element
import java.io.Serializable

public class PromotePomDependenciesToCompileScope(
    private val groupId: String,
    private val artifactIds: Set<String>
) : Action<XmlProvider>, Serializable {

    override fun execute(xml: XmlProvider) {
        val root = xml.asElement()
        val dependencies = root.getElementsByTagName("dependency")
        for (i in 0 until dependencies.length) {
            val dependency = dependencies.item(i) as? Element ?: continue
            val depGroupId = dependency.directChild("groupId")?.textContent
            val depArtifactId = dependency.directChild("artifactId")?.textContent
            val scopeElement = dependency.directChild("scope")

            if (depGroupId == groupId && depArtifactId in artifactIds && scopeElement?.textContent == "runtime") {
                scopeElement.textContent = "compile"
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
