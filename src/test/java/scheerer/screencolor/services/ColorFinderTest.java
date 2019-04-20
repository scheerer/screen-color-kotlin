package scheerer.screencolor.services;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest
@Slf4j
public class ColorFinderTest {

    private ScreenShotService screenShotService;

    @Before
    public void setup() throws Exception {
        screenShotService = new ScreenShotService();
    }

    @Test
    public void testTimings() {
        int imageSamples = 250;

        Map<String, Double> averageTimings = screenShotService.screenShots(100)
                .take(imageSamples)
                .map(screenShotImage -> Tuples.of(
                    captureTiming(ColorFinder.getAverageRgb, screenShotImage),
                    captureTiming(ColorFinder.getSquaredAverageRgb, screenShotImage),
                    captureTiming(ColorFinder.getAverageHue, screenShotImage),
                    captureTiming(ColorFinder.getMostDominant, screenShotImage)
                ))
                .collectList()
        .map(timings -> {
            double avgRgbTotal =0;
            double squaredAverageRgbTotal = 0;
            double averageHueTotal = 0;
            double mostDominantTotal = 0;

            for(Tuple4 tuple : timings) {
                avgRgbTotal += Long.parseLong(tuple.getT1().toString());
                squaredAverageRgbTotal += Long.parseLong(tuple.getT2().toString());
                averageHueTotal += Long.parseLong(tuple.getT3().toString());
                mostDominantTotal += Long.parseLong(tuple.getT4().toString());
            }

            Map<String, Double> result = new HashMap<>();
            result.put("averageRgb", avgRgbTotal / timings.size());
            result.put("squaredAverageRgb", squaredAverageRgbTotal / timings.size());
            result.put("averageHue", averageHueTotal / timings.size());
            result.put("mostDominant", mostDominantTotal / timings.size());

            return result;
        })
        .blockOptional()
        .orElse(null);

        log.info("Averages: {}", averageTimings);
    }

    private long captureTiming(ColorAlgorithm algorithm, BufferedImage image) {
        long startTime = System.currentTimeMillis();
        algorithm.apply(image);
        return System.currentTimeMillis() - startTime;
    }
}
