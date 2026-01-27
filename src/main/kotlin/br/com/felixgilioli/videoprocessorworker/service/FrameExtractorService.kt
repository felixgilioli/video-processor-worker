package br.com.felixgilioli.videoprocessorworker.service

import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.Java2DFrameConverter
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

@Service
class FrameExtractorService {

    fun extractFramesAndZip(videoBytes: ByteArray, videoId: UUID): ByteArray {
        val tempDir = Files.createTempDirectory("frames-$videoId")
        val videoFile = tempDir.resolve("video.mp4")
        val framesDir = tempDir.resolve("frames")
        Files.createDirectories(framesDir)

        try {
            Files.write(videoFile, videoBytes)
            extractFrames(videoFile, framesDir)
            return createZip(framesDir)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun extractFrames(videoFile: Path, framesDir: Path) {
        FFmpegFrameGrabber(videoFile.toFile()).use { grabber ->
            grabber.start()

            val durationSeconds = grabber.lengthInTime / 1_000_000.0
            val durationMinutes = (durationSeconds / 60).toInt().coerceAtLeast(1)
            val intervalSeconds = durationSeconds / durationMinutes

            for (i in 0 until durationMinutes) {
                val timestamp = (i * intervalSeconds * 1_000_000).toLong()
                grabber.setTimestamp(timestamp)
                val frame = grabber.grabImage() ?: continue
                saveFrame(frame, framesDir.resolve("frame_${String.format("%04d", i)}.jpg"))
            }
        }
    }

    private fun saveFrame(frame: Frame, output: Path) {
        Java2DFrameConverter().use { converter ->
            val image = converter.convert(frame)
            ImageIO.write(image, "jpg", output.toFile())
        }
    }

    private fun createZip(framesDir: Path): ByteArray {
        val outputStream = ByteArrayOutputStream()
        ZipOutputStream(outputStream).use { zip ->
            Files.list(framesDir).forEach { frame ->
                zip.putNextEntry(ZipEntry(frame.fileName.toString()))
                zip.write(Files.readAllBytes(frame))
                zip.closeEntry()
            }
        }
        return outputStream.toByteArray()
    }
}