package scheerer.screencolor.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Duration;

@Service
@Slf4j
public class ScreenShotService {

    private Robot awtRobot;
    private Dimension screenSize;

    public ScreenShotService() throws AWTException {
        awtRobot = new Robot();
        screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    }

    public Flux<BufferedImage> screenShots(int intervalMillis) {
        return Flux.interval(Duration.ofMillis(intervalMillis))
                .map(aLong -> capture())
                .share();
    }

    private BufferedImage capture() {
        return awtRobot.createScreenCapture(new Rectangle(screenSize));
    }
}
