package scheerer.screencolor.services;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.TermCriteria;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import scheerer.screencolor.model.Color;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

@Slf4j
public class ColorFinder {

    public static final int PIXEL_DENSITY = 4;

    public static Map<String, Color> getColors(BufferedImage image) {
        return Stream.of(
                getColor("avgRgb", ColorFinder.getAverageRgb, image),
                getColor("squaredAvgRgb", ColorFinder.getSquaredAverageRgb, image),
                getColor("averageHue", ColorFinder.getAverageHue, image)
//                getColor("mostDominant", ColorFinder.getMostDominant, image)
            )
            .map(CompletableFuture::join)
            .collect(toMap(Tuple2::getT1, Tuple2::getT2));
    }

    private static CompletableFuture<Tuple2<String, Color>> getColor(String name, ColorAlgorithm algorithm, BufferedImage image) {
        return CompletableFuture.supplyAsync(() -> Tuples.of(name, algorithm.apply(image)));
    }

    public static ColorAlgorithm getAverageRgb = image -> {
        long sumr = 0, sumg = 0, sumb = 0;
        int pixelsScanned = 0;
        for (int x = 0; x < image.getWidth(); x += PIXEL_DENSITY) {
            for (int y = 0; y < image.getHeight(); y += PIXEL_DENSITY) {
                java.awt.Color pixel = new java.awt.Color(image.getRGB(x, y));
                sumr += pixel.getRed();
                sumg += pixel.getGreen();
                sumb += pixel.getBlue();

                pixelsScanned++;
            }
        }

        int redAvg = ((Long) (sumr / pixelsScanned)).intValue();
        int greenAvg = ((Long) (sumg / pixelsScanned)).intValue();
        int blueAvg = ((Long) (sumb / pixelsScanned)).intValue();

        return new Color(redAvg, greenAvg, blueAvg);
    };

    public static ColorAlgorithm getSquaredAverageRgb = image -> {
        long sumr = 0, sumg = 0, sumb = 0;
        int pixelsScanned = 0;
        for (int x = 0; x < image.getWidth(); x += PIXEL_DENSITY) {
            for (int y = 0; y < image.getHeight(); y += PIXEL_DENSITY) {
                java.awt.Color pixel = new java.awt.Color(image.getRGB(x, y));
                sumr += pixel.getRed() * pixel.getRed();
                sumg += pixel.getGreen() * pixel.getGreen();
                sumb += pixel.getBlue() * pixel.getBlue();

                pixelsScanned++;
            }
        }

        int redAvg = ((Double) Math.sqrt(sumr / pixelsScanned)).intValue();
        int greenAvg = ((Double) Math.sqrt(sumg / pixelsScanned)).intValue();
        int blueAvg = ((Double) Math.sqrt(sumb / pixelsScanned)).intValue();

        return new Color(redAvg, greenAvg, blueAvg);
    };


    public static ColorAlgorithm getAverageHue = image -> {
        // These will be used to store the sum of the angles
        double X = 0.0;
        double Y = 0.0;

        int pixelsScanned = 0;
        for (int x = 0; x < image.getWidth(); x += PIXEL_DENSITY) {
            for (int y = 0; y < image.getHeight(); y += PIXEL_DENSITY) {
                java.awt.Color pixel = new java.awt.Color(image.getRGB(x, y));

                float[] hsb = java.awt.Color.RGBtoHSB(pixel.getRed(), pixel.getGreen(), pixel.getBlue(), null);

                X += Math.cos(hsb[0] / 180 * Math.PI);
                Y += Math.sin(hsb[0] / 180 * Math.PI);

                pixelsScanned++;
            }
        }

        // Now average the X and Y values
        X /= pixelsScanned;
        Y /= pixelsScanned;


        // Get atan2 of those
        double averageHue = Math.atan2(Y, X) * 180 / Math.PI;
        int rgb = java.awt.Color.HSBtoRGB(((Double) averageHue).floatValue(), 1.0f, 0.5f);
        java.awt.Color averageColor = new java.awt.Color(rgb);
        return new Color(averageColor.getRed(), averageColor.getGreen(), averageColor.getBlue());
    };


    public static ColorAlgorithm getMostDominant = image -> {
        Mat m = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC4);

        int[] matPixels = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();

        ByteBuffer bb = ByteBuffer.allocate(matPixels.length * 4);
        IntBuffer ib = bb.asIntBuffer();
        ib.put(matPixels);

        byte[] bvals = bb.array();

        m.put(0,0, bvals);
        Map<Color, Float> colorWeights = cluster(m, 4);
        log.info("ColorWeights: {}", colorWeights);

        return colorWeights.keySet().iterator().next();
    };

    private static Map<Color, Float> cluster(Mat image, int k) {
        Mat samples = image.reshape(1, image.cols() * image.rows());
        Mat samples32f = new Mat();
        samples.convertTo(samples32f, CvType.CV_32F, 1.0 / 255.0);

        Mat labels = new Mat();
        TermCriteria criteria = new TermCriteria(TermCriteria.COUNT, 100, 1);
        Mat centers = new Mat();
        Core.kmeans(samples32f, k, labels, criteria, 1, Core.KMEANS_PP_CENTERS, centers);
        return getColorWeights(image, labels, centers);
    }

    private static Map<Color, Float> getColorWeights(Mat image, Mat labels, Mat colors) {
        Map<Integer, Float> colorTotals = new HashMap<>();

        int index = 0;
        for (int y = 0; y < image.rows(); y += PIXEL_DENSITY) {
            for (int x = 0; x < image.cols(); x += PIXEL_DENSITY) {
                int label = (int)labels.get(index, 0)[0];

                double r = colors.get(label, 1)[0];
                double g = colors.get(label, 2)[0];
                double b = colors.get(label, 3)[0];

                java.awt.Color color = new java.awt.Color((float)r, (float)g, (float)b);
                int c = color.getRGB();
                if (!colorTotals.containsKey(c)) {
                    colorTotals.put(c, 0f);
                }
                colorTotals.put(c, colorTotals.get(c) + 1);

                index++;
            }
        }

        int imageSize = image.rows() * image.cols();

        List<Map.Entry<Integer, Float>> list = new ArrayList<>(colorTotals.entrySet());
        list.sort((eOne, eTwo) -> eTwo.getValue().compareTo(eOne.getValue()));


        LinkedHashMap<Color, Float> colorWeights = new LinkedHashMap<>();
        for (Map.Entry<Integer, Float> entry : list) {
            java.awt.Color color = new java.awt.Color(entry.getKey());
            colorWeights.put(new Color(color.getRed(), color.getGreen(), color.getBlue()), entry.getValue() / imageSize);
        }

        return colorWeights;
    }
}
