package scheerer.screencolor

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.Banner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.config.CorsRegistry
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.config.WebFluxConfigurer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.awt.*
import java.awt.image.BufferedImage
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt


inline fun <reified T> logger(): Logger {
	return LoggerFactory.getLogger(T::class.java)
}

@Configuration
class SharedFluxConfig {

	@Bean
	fun executorService(): ExecutorService {
		return Executors.newVirtualThreadPerTaskExecutor()
	}

	@Bean
	fun screenColorFlux(screenShotService: ScreenShotService): Flux<ColorEvent> {
		return screenShotService.screenShots(60)
			.map { ColorEvent(it) }
			.share()
	}
}

@RestController
class WebRouter (private val screenColorFlux: Flux<ColorEvent>) {

	@GetMapping("/screen-colors")
	fun screenColors(): Flux<ServerSentEvent<ColorEvent>> {
		return screenColorFlux.map { ServerSentEvent.builder(it).build() }
	}
}

@Service
class ScreenShotService @Throws(AWTException::class)
constructor(private val executorService: ExecutorService) {
	private val awtRobot: Robot = Robot()
	private val screenRect = Rectangle(Toolkit.getDefaultToolkit().screenSize)

	fun screenShots(fps: Int): Flux<BufferedImage> {
		return Flux.interval(Duration.ofMillis((1000 / fps).toLong()))
			.onBackpressureLatest() // Only keep the latest value if we can't keep up
			.flatMap({ captureScreen() }, 1) // Limit concurrency to 1
			.limitRate(1) // Ensure no more than 1 per interval
			.subscribeOn(Schedulers.fromExecutorService(executorService)) // Use a scheduler that can handle blocking operations
	}

	fun captureScreen(): Mono<BufferedImage> {
		return Mono.fromCallable {
			try {
				val startTime = System.currentTimeMillis()
				val img = awtRobot.createScreenCapture(screenRect)
				val captureLatencyMs = System.currentTimeMillis() - startTime
				logger<Logger>().info("Screen capture latency: {}", captureLatencyMs)

				img
			} catch (e: AWTException) {
				println("Failed to capture screen: ${e.message}")
				null
			}
		}
	}
}

data class Color(val red: Int, val green: Int, val blue: Int) {
	val hexTriplet: String = String.format("#%02x%02x%02x", red, green, blue)
}

class ColorEvent(image: BufferedImage) {
	private val algorithmResults: Map<String, Color> = ColorFinder.getColors(image)
	val color: Color =
		algorithmResults["squaredAvgRgb"] ?: error("Couldn't find the algorithm result!") // probably should have an enum I guess
}

@Configuration
@EnableWebFlux
class WebConfig : WebFluxConfigurer {
	override fun addCorsMappings(registry: CorsRegistry) {
		registry.addMapping("/**")
				.allowedOrigins("*")
				.allowedMethods("GET")

	}
}

@SpringBootApplication
class ScreenColorApplication

fun main(args: Array<String>) {
	System.setProperty("java.awt.headless", "false")
	runApplication<ScreenColorApplication>(*args) {
		setBannerMode(Banner.Mode.CONSOLE)
	}
}


object ColorFinder {
	private const val PIXEL_DENSITY = 4

	private fun getSquaredAverageRgb(image: BufferedImage): Color {
		var sumr: Long = 0
		var sumg: Long = 0
		var sumb: Long = 0
		var pixelsScanned = 0
		var x = 0
		while (x < image.width) {
			var y = 0
			while (y < image.height) {
				val pixel = Color(image.getRGB(x, y))
				sumr += (pixel.red * pixel.red).toLong()
				sumg += (pixel.green * pixel.green).toLong()
				sumb += (pixel.blue * pixel.blue).toLong()

				pixelsScanned++
				y += PIXEL_DENSITY
			}
			x += PIXEL_DENSITY
		}

		val redAvg = sqrt((sumr / pixelsScanned).toDouble()).toInt()
		val greenAvg = sqrt((sumg / pixelsScanned).toDouble()).toInt()
		val blueAvg = sqrt((sumb / pixelsScanned).toDouble()).toInt()

		return Color(redAvg, greenAvg, blueAvg)
	}

	fun getColors(image: BufferedImage): Map<String, Color> {
		return mapOf("squaredAvgRgb" to getSquaredAverageRgb(image))
	}
}