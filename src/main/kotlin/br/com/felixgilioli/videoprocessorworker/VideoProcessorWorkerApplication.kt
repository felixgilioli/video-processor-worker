package br.com.felixgilioli.videoprocessorworker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class VideoProcessorWorkerApplication

fun main(args: Array<String>) {
	runApplication<VideoProcessorWorkerApplication>(*args)
}
