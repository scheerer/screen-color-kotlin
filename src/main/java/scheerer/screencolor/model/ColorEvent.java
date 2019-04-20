package scheerer.screencolor.model;

import lombok.Data;
import lombok.ToString;
import org.springframework.http.codec.ServerSentEvent;
import scheerer.screencolor.services.ColorFinder;

import java.awt.image.BufferedImage;
import java.time.ZonedDateTime;
import java.util.Map;

@Data
@ToString
public class ColorEvent {
    private ZonedDateTime time = ZonedDateTime.now();
    private Map<String, Color> algorithmResults;
    private Color color;

    public ColorEvent(BufferedImage image) {
        this.algorithmResults = ColorFinder.getColors(image);
        this.color = algorithmResults.get("squaredAvgRgb"); // probably should have an enum I guess
    }

    public ServerSentEvent<ColorEvent> toSse() {
        return ServerSentEvent.builder(this).build();
    }
}
