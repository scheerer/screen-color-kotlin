package scheerer.screencolor

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.Banner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.config.CorsRegistry
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.config.WebFluxConfigurer
import reactor.core.publisher.Flux
import java.awt.*
import java.awt.image.BufferedImage
import java.time.Duration
import java.util.logging.Level
import kotlin.math.sqrt

inline fun <reified T> logger(): Logger {
	return LoggerFactory.getLogger(T::class.java)
}

@RestController
class WebRouter (val screenShotService: ScreenShotService) {
	@GetMapping("/screen-colors")
	fun screenColors(@RequestParam(name = "interval", defaultValue = "1000") interval: Int): Flux<ServerSentEvent<ColorEvent>> {
		return screenShotService.screenShots(interval)
				.map { ColorEvent(it) }
				.map { ServerSentEvent.builder(it).build() }
	}
}

@Service
class ScreenShotService @Throws(AWTException::class)
constructor() {
	private val awtRobot: Robot = Robot()
	private val screenSize: Dimension = Toolkit.getDefaultToolkit().screenSize

	fun screenShots(intervalMillis: Int): Flux<BufferedImage> {
		return Flux.interval(Duration.ofMillis(intervalMillis.toLong()))
				.map { awtRobot.createScreenCapture(Rectangle(screenSize)) }
				.share()
	}
}

data class Color(val red: Int, val green: Int, val blue: Int) {
	val hexTriplet: String = String.format("#%02x%02x%02x", red, green, blue)
}

class ColorEvent(image: BufferedImage) {
	val algorithmResults: Map<String, Color> = ColorFinder.getColors(image)
	val color: Color

	init {
		this.color = algorithmResults["squaredAvgRgb"] ?: error("Couldn't find the algorithm result!") // probably should have an enum I guess
	}
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