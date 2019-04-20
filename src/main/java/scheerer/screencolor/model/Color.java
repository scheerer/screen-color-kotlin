package scheerer.screencolor.model;

import lombok.Data;

@Data
public class Color {
    private int red, green, blue;
    private String hexTriplet;

    public Color(int red, int green, int blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.hexTriplet = String.format("#%02x%02x%02x", red, green, blue);
    }
}