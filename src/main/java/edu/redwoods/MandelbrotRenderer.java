package edu.redwoods;

import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MandelbrotRenderer {

    private List<GradientStop> gradient = new ArrayList<>();
    private GradientModel gradientModel;

    private int maxIterations = 500;

    public MandelbrotRenderer(GradientModel gradientModel) {

        this.gradientModel = gradientModel;

    }

    public WritableImage render(int width, int height, Viewport viewport) {

        WritableImage image = new WritableImage(width, height);

        double[][] iterBuffer = new double[height][width];
        int[] histogram = new int[maxIterations];

        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        int tileSize = 64;

        // -------------------------
        // PASS 1: Compute iterations + histogram
        // -------------------------
        List<Future<?>> futures = new ArrayList<>();

        for (int ty = 0; ty < height; ty += tileSize) {
            for (int tx = 0; tx < width; tx += tileSize) {

                int startX = tx;
                int endX = Math.min(tx + tileSize, width);
                int startY = ty;
                int endY = Math.min(ty + tileSize, height);

                futures.add(executor.submit(new RenderTask(
                        startX, endX, startY, endY,
                        width, height,
                        viewport,
                        iterBuffer,
                        histogram
                )));
            }
        }

        // Wait for all threads
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // -------------------------
        // Build cumulative histogram
        // -------------------------
        int total = width * height;
        double[] cumulative = new double[maxIterations];

        double sum = 0;
        for (int i = 0; i < maxIterations; i++) {
            sum += histogram[i];
            cumulative[i] = sum / total;
        }

        // -------------------------
        // PASS 2: Color pixels
        // -------------------------
        PixelWriter pw = image.getPixelWriter();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                double iter = iterBuffer[y][x];

                if (iter >= maxIterations) {
                    pw.setColor(x, y, Color.BLACK);
                    continue;
                }

                int base = (int) iter;
                // Clamp to safe range
                if (base >= maxIterations - 1) {
                    base = maxIterations - 2;
                }

                double frac = iter - base;

                double t1 = cumulative[base];
                double t2 = cumulative[Math.min(base + 1, maxIterations - 1)];

                double t = t1 + (t2 - t1) * frac;

                Color color = getGradientColor(t);
                pw.setColor(x, y, color);
            }
        }

        executor.shutdown();

        return image;
    }

    public WritableImage highResRender(int width, int height, Viewport viewport) {

        int newWidth = width * 4;

        int newHeight = height * 4;

        WritableImage image = new WritableImage(newWidth, newHeight);
        PixelWriter pw = image.getPixelWriter();

        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {

                double cx = viewport.mapX(x, newWidth);
                double cy = viewport.mapY(y, newHeight);

                double iter = mandelbrot(cx, cy);

                Color color = getColor(iter);
                pw.setColor(x, y, color);
            }
        }

        return image;
    }

    private double mandelbrot(double cx, double cy) {

        double zx = 0;
        double zy = 0;
        int iter = 0;

        while (zx * zx + zy * zy <= 4 && iter < maxIterations) {
            double temp = zx * zx - zy * zy + cx;
            zy = 2 * zx * zy + cy;
            zx = temp;
            iter++;
        }

        if (iter == maxIterations) return iter;

        double modulus = Math.sqrt(zx * zx + zy * zy);
        double smooth = iter + 1 - Math.log(Math.log(modulus)) / Math.log(2);

        return smooth;
    }

    private Color getGradientColor(double t) {

        var stops = gradientModel.getStops();

        if (stops.isEmpty()) return Color.BLACK;

        if (t <= stops.get(0).getPosition()) {
            return stops.get(0).getColor();
        }

        if (t >= stops.get(stops.size() - 1).getPosition()) {
            return stops.get(stops.size() - 1).getColor();
        }

        for (int i = 0; i < stops.size() - 1; i++) {
            GradientStop a = stops.get(i);
            GradientStop b = stops.get(i + 1);

            if (t >= a.getPosition() && t <= b.getPosition()) {
                double localT = (t - a.getPosition()) /
                        (b.getPosition() - a.getPosition());

                return a.getColor().interpolate(b.getColor(), localT);
            }
        }

        return stops.get(stops.size() - 1).getColor();
    }

    // Old getColor
    private Color getColor(double iter) {

        if (iter >= maxIterations) {
            return Color.BLACK;
        }

        // double t = iter / maxIterations;
        // Fix to control blocky look - smooths the gradient
        double t = iter * 0.02;
        t = t - Math.floor(t);
        t = Math.pow(t, 0.8);

        var stops = gradientModel.getStops();

        if (stops.isEmpty()) {
            return Color.BLACK;
        }

        // Clamp t to the range of the first and last stop
        if (t <= stops.get(0).getPosition()) {
            return stops.get(0).getColor();
        }
        if (t >= stops.get(stops.size() - 1).getPosition()) {
            return stops.get(stops.size() - 1).getColor();
        }

        for (int i = 0; i < stops.size() - 1; i++) {
            GradientStop a = stops.get(i);
            GradientStop b = stops.get(i + 1);

            if (t >= a.getPosition() && t <= b.getPosition()) {
                double localT = (t - a.getPosition()) / (b.getPosition() - a.getPosition());
                return a.getColor().interpolate(b.getColor(), localT);
            }
        }

        // Fallback (should not be reached after clamping above)
        return stops.get(stops.size() - 1).getColor();
    }

    public void addGradientStop(double position, Color color) {
        gradient.add(new GradientStop(position, color));
        gradient.sort(Comparator.comparingDouble(gs -> gs.getPosition()));
    }

    public void setGradientModel(GradientModel gradientModel) {
        this.gradientModel = gradientModel;
    }

    public void setMaxIterations(int newMaxIterations) {
        this.maxIterations = newMaxIterations;
    }

    //Inner class to implement multi-threading
    class RenderTask implements Runnable {

        private final int startX, endX, startY, endY;
        private final int width, height;
        private final Viewport viewport;
        private final double[][] iterBuffer;
        private final int[] histogram;

        public RenderTask(int startX, int endX, int startY, int endY,
                          int width, int height,
                          Viewport viewport,
                          double[][] iterBuffer,
                          int[] histogram) {

            this.startX = startX;
            this.endX = endX;
            this.startY = startY;
            this.endY = endY;
            this.width = width;
            this.height = height;
            this.viewport = viewport;
            this.iterBuffer = iterBuffer;
            this.histogram = histogram;
        }

        @Override
        public void run() {

            // Thread-local histogram to avoid contention
            int[] localHist = new int[maxIterations];

            for (int y = startY; y < endY; y++) {
                for (int x = startX; x < endX; x++) {

                    double cx = viewport.mapX(x, width);
                    double cy = viewport.mapY(y, height);

                    double iter = mandelbrot(cx, cy);

                    iterBuffer[y][x] = iter;

                    int index = (int) iter;
                    if (index >= 0 && index < maxIterations) {
                        localHist[index]++;
                    }
                }
            }

            // Merge local histogram into global
            synchronized (histogram) {
                for (int i = 0; i < maxIterations; i++) {
                    histogram[i] += localHist[i];
                }
            }
        }
    }
}
