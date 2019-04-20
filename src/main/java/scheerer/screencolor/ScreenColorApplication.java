package scheerer.screencolor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import scheerer.screencolor.model.ColorEvent;
import scheerer.screencolor.services.ScreenShotService;

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
    RouterFunction routes(ScreenShotService screenShotService) {
        return route(GET("/screen-colors"), request -> {
            int interval = Integer.parseInt(request.queryParam("interval").orElse("1000"));
            return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(fromServerSentEvents(Flux.from(screenColors(screenShotService, interval)).map(ColorEvent::toSse)));

        });
    }

    private Flux<ColorEvent> screenColors(ScreenShotService screenShotService, int interval) {
        return Flux.from(screenShotService.screenShots(interval))
                .map(ColorEvent::new)
                .log(log.getName(), Level.INFO);

    }
}
