package br.com.felixgilioli.videoprocessorworker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class VideoProcessorWorkerApplication

fun main(args: Array<String>) {
	runApplication<VideoProcessorWorkerApplication>(*args)
}
