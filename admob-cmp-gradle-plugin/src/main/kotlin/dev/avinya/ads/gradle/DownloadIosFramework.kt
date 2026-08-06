package dev.avinya.ads.gradle

import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Downloads external binary archive")
public abstract class DownloadIosFramework : DefaultTask() {
    @get:Input
    public abstract val version: Property<String>

    @get:Input
    public abstract val baseUrl: Property<String>

    // Supply-chain integrity: the UMP endpoint is unversioned and neither archive was
    // checksummed, so the same commit could build against different headers. Fail closed.
    @get:Input
    public abstract val expectedSha256: Property<String>

    /**
     * The extracted `<name>.xcframework` directory — the task's real product.
     *
     * Declaring only the marker as an `@OutputFile` tracked that one file and nothing else, so
     * deleting an architecture slice or truncating a binary left the marker intact, the task
     * UP-TO-DATE, and the damage to surface downstream in cinterop or the native link instead.
     * An `@OutputDirectory` fingerprints the tree, so any damage inside it re-runs extraction.
     *
     * Deliberately the per-framework directory, NOT the shared parent both download tasks
     * extract into: declaring the parent would make the two tasks overlap on outputs.
     */
    @get:OutputDirectory
    public abstract val frameworkDir: DirectoryProperty

    /**
     * Records the catalog version the tree was extracted from, so a version bump invalidates it.
     * Lives inside [frameworkDir] and is therefore already covered by that output; `@Internal`
     * keeps it from being declared twice.
     */
    @get:Internal
    public abstract val markerFile: RegularFileProperty

    @TaskAction
    fun download() {
        val mf = markerFile.get().asFile
        val fwDir = frameworkDir.get().asFile
        // The marker records the version, but it is NOT evidence the extraction is intact — a
        // deleted slice leaves it untouched. Gradle re-runs this task when the output tree
        // changes; short-circuiting on the marker alone would skip the very repair it was
        // re-run to perform. Both conditions must hold.
        if (mf.exists() && mf.readText().trim() == version.get() && expectedSlice(fwDir).isDirectory) return
        val frameworksBase = fwDir.parentFile
        fwDir.deleteRecursively()
        fwDir.mkdirs()
        val baseName = fwDir.name
        // Note: the UMP zip URL is not version-pinned by Google; the marker records
        // the catalog version that triggered the download.
        val zipPath = when {
            baseName.startsWith("GoogleMobileAds") -> "googlemobileadssdkios-${version.get()}.zip"
            baseName.startsWith("UserMessagingPlatform") -> "googleusermessagingplatform.zip"
            else -> error("Unknown framework: $baseName")
        }
        val zipUrl = URI("${baseUrl.get()}/$zipPath").toURL()
        logger.lifecycle("Downloading from ${zipUrl}...")

        // Read the whole archive into memory first so it can be checksummed BEFORE any of
        // its bytes are extracted — the checksum gate must run on every real download, not
        // just at configuration time, and must never let unverified bytes reach disk.
        val archiveBytes = zipUrl.openStream().use { it.readBytes() }
        val expectedSha = expectedSha256.get()
        val actualSha = MessageDigest.getInstance("SHA-256")
            .digest(archiveBytes)
            .joinToString("") { "%02x".format(it) }
        check(actualSha == expectedSha) {
            "$baseName iOS header archive checksum mismatch.\n  expected: $expectedSha\n  actual:   $actualSha\n" +
                "Refusing to generate bindings from an unverified archive."
        }

        val basePath = frameworksBase.canonicalFile.toPath()
        ByteArrayInputStream(archiveBytes).use { input ->
            ZipInputStream(input).use { zis ->
                val firstEntry = zis.nextEntry ?: throw GradleException("Empty zip from ${zipUrl}")
                val prefix = firstEntry.name.substringBefore('/') + "/"
                fun processEntry(entry: java.util.zip.ZipEntry, base: File) {
                    val rel = entry.name.removePrefix(prefix)
                    val target = File(base, rel)
                    if (!target.canonicalFile.toPath().startsWith(basePath)) {
                        throw GradleException("Zip entry escapes extraction dir: ${entry.name}")
                    }
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile.mkdirs()
                        target.outputStream().use { it.write(zis.readAllBytes()) }
                    }
                }
                processEntry(firstEntry, frameworksBase)
                zis.closeEntry()
                var entry = zis.nextEntry
                while (entry != null) {
                    processEntry(entry, frameworksBase)
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        if (!expectedSlice(fwDir).exists()) {
            throw GradleException("Extraction did not produce ${expectedSlice(fwDir)} — zip layout changed?")
        }
        mf.writeText(version.get())
        logger.lifecycle("Extracted to $fwDir")
    }

    /** The slice whose presence stands in for "the extraction produced a usable framework". */
    private fun expectedSlice(fwDir: File): File = File(fwDir, "ios-arm64")
}
