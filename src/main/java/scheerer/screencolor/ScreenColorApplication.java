package scheerer.screencolor;

import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Flux;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.logging.Level;

import static org.springframework.web.reactive.function.BodyInserters.fromServerSentEvents;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
@RestController
@Slf4j
public class ScreenColorApplication {

    static {
        System.setProperty("java.awt.headless", "false");
    }

    public static void main(String[] args) {
        SpringApplication.run(ScreenColorApplication.class, args);
    }

    @Bean
    RouterFunction routes() {
        return route(GET("/screen-color"), request -> {
            int interval = Integer.parseInt(request.queryParam("interval").orElse("1000"));
            return ServerResponse.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(fromServerSentEvents(Flux.from(screenColors(interval)).map(ScreenColorApplication::toSse)));
        });
    }

    private Flux<ColorEvent> screenColors(int intervalMillis) {
        try {
            Robot robot = new Robot();
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            return Flux.interval(Duration.ofMillis(intervalMillis))
                    .map(aLong -> {
                        BufferedImage screenShot = robot.createScreenCapture(new Rectangle(screenSize));
                        int pixelDensity = 10;

                        long sumr = 0, sumg = 0, sumb = 0;
                        int pixelsScanned = 0;
                        for (int x = 0; x < screenShot.getWidth(); x += pixelDensity) {
                            for (int y = 0; y < screenShot.getHeight(); y += pixelDensity) {
                                Color pixel = new Color(screenShot.getRGB(x, y));
                                sumr += pixel.getRed();
                                sumg += pixel.getGreen();
                                sumb += pixel.getBlue();

                                pixelsScanned++;
                            }
                        }

                        long redAvg = sumr / pixelsScanned;
                        long greenAvg = sumg / pixelsScanned;
                        long blueAvg = sumb / pixelsScanned;
                        return new ColorEvent(redAvg, greenAvg, blueAvg);
                    })
                    .log(log.getName(), Level.INFO);
        } catch (AWTException e) {
            return Flux.never();
        }
    }

    private static ServerSentEvent<ColorEvent> toSse(ColorEvent data) {
        return ServerSentEvent.builder(data).build();
    }

    @Data
    @ToString
    public static class ColorEvent {
        ZonedDateTime time = ZonedDateTime.now();
        long red, green, blue;
        String hexTriplet;

        ColorEvent(long red, long green, long blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.hexTriplet = String.format("#%02x%02x%02x", red, green, blue);
        }
    }
}
