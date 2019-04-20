package scheerer.screencolor.services;

import scheerer.screencolor.model.Color;

import java.awt.image.BufferedImage;

@FunctionalInterface
public interface ColorAlgorithm {
    Color apply(BufferedImage image);
}
