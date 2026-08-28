package capsule

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Filesystem-bound: invokes FFmpeg process for video cropping")
open class CapsuleDistribTask : DefaultTask() {

    @get:OutputDirectory
    val outputDir: DirectoryProperty = project.objects.directoryProperty()

    @get:Internal
    internal var capsuleExtension: CapsuleExtension
        get() = _capsuleExtension ?: project.extensions.getByType(CapsuleExtension::class.java).also { _capsuleExtension = it }
        set(value) { _capsuleExtension = value }

    private var _capsuleExtension: CapsuleExtension? = null

    init {
        outputDir.convention(project.layout.buildDirectory.dir("capsule/distrib"))
    }

    @TaskAction
    fun execute() {
        val capDir = project.layout.buildDirectory.dir(
            capsuleExtension.outputDir.get()
        ).get().asFile

        val videos = capDir.listFiles { f -> f.name.endsWith(".webm") }?.toList()
            ?: emptyList()

        if (videos.isEmpty()) {
            logger.warn("No capsule videos found in {}. Run 'generateCapsuleVideo' first.", capDir.absolutePath)
            return
        }

        val distDir = outputDir.get().asFile
        distDir.mkdirs()

        val ffmpeg = capsuleExtension.ffmpegExecutablePath.get()
        val targetWidth = capsuleExtension.distribOutputWidth.get()
        val targetHeight = capsuleExtension.distribOutputHeight.get()

        if (!isFfmpegAvailable(ffmpeg)) {
            logger.warn("FFmpeg not available at '{}' — copying videos as-is to distrib", ffmpeg)
            for (video in videos) {
                val dest = distDir.resolve(video.name)
                video.copyTo(dest, overwrite = true)
                logger.lifecycle("  COPY → {}", dest.absolutePath)
            }
            return
        }

        for (video in videos) {
            val outputFile = distDir.resolve(video.name)

            if (!isValidWebM(video)) {
                logger.lifecycle("DISTRIB → {} (placeholder, copy as-is)", video.name)
                video.copyTo(outputFile, overwrite = true)
                logger.lifecycle("  COPY → {}", outputFile.absolutePath)
                continue
            }

            logger.lifecycle("DISTRIB → {} (crop {}x{})", video.name, targetWidth, targetHeight)

            try {
                cropVideo(video, outputFile, ffmpeg, targetWidth, targetHeight)
                logger.lifecycle("  OK → {}", outputFile.absolutePath)
            } catch (e: Exception) {
                logger.error("  FAILED {}: {}", video.name, e.message)
                throw e
            }
        }
    }

    private fun cropVideo(
        input: File,
        output: File,
        ffmpeg: String,
        targetWidth: Int,
        targetHeight: Int
    ) {
        val result = ProcessRunner.run(
            listOf(
                ffmpeg, "-y",
                "-i", input.absolutePath,
                "-vf", "scale=$targetWidth:$targetHeight:force_original_aspect_ratio=increase,crop=$targetWidth:$targetHeight",
                "-c:a", "copy",
                output.absolutePath,
            )
        )
        if (!result.isSuccess) {
            throw RuntimeException("FFmpeg exited with code ${result.exitCode}: ${result.tail()}")
        }
    }

    private fun isFfmpegAvailable(ffmpegPath: String): Boolean =
        ProcessRunner.probe(ffmpegPath, "-version")

    private val webmSignature = byteArrayOf(0x1a.toByte(), 0x45.toByte(), 0xdf.toByte(), 0xa3.toByte())

    private fun isValidWebM(file: File): Boolean {
        if (file.length() < 4) return false
        return try {
            val header = ByteArray(4)
            file.inputStream().use { it.read(header) }
            header.contentEquals(webmSignature)
        } catch (e: Exception) {
            false
        }
    }
}
