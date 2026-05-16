package edu.redwoods;

import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MandelbrotRenderer {

    private GradientModel gradientModel;

    private int maxIterations = 500;
    private int numCycles = 3;

    public MandelbrotRenderer(GradientModel gradientModel) {

        this.gradientModel = gradientModel;

    }

    public WritableImage render(int width, int height, Viewport viewport) {

        WritableImage image = new WritableImage(width, height);

        double[][] iterBuffer = new double[height][width];

        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        int tileSize = 64;

        // -------------------------
        // PASS 1: Compute iterations
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
                        iterBuffer
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
        // PASS 2: Color pixels
        // -------------------------
        PixelWriter pw = image.getPixelWriter();

        double logMax = Math.log1p(maxIterations);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                double iter = iterBuffer[y][x];

                if (iter >= maxIterations) {
                    pw.setColor(x, y, Color.BLACK);
                    continue;
                }
                // Power scales it closer to the edge cases, % 1.0 can be scaled as well
                double tRaw = Math.log1p(iter) / logMax;
                double t = (Math.pow(tRaw, 4.0) * numCycles) % 1.0;

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

        double[][] iterBuffer = new double[newHeight][newWidth];

        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        int tileSize = 64;

        // -------------------------
        // PASS 1: Compute iterations
        // -------------------------
        List<Future<?>> futures = new ArrayList<>();

        for (int ty = 0; ty < newHeight; ty += tileSize) {
            for (int tx = 0; tx < newWidth; tx += tileSize) {

                int startX = tx;
                int endX = Math.min(tx + tileSize, newWidth);
                int startY = ty;
                int endY = Math.min(ty + tileSize, newHeight);

                futures.add(executor.submit(new RenderTask(
                        startX, endX, startY, endY,
                        newWidth, newHeight,
                        viewport,
                        iterBuffer
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
        // PASS 2: Color pixels
        // -------------------------
        PixelWriter pw = image.getPixelWriter();

        double logMax = Math.log1p(maxIterations);

        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {

                double iter = iterBuffer[y][x];

                if (iter >= maxIterations) {
                    pw.setColor(x, y, Color.BLACK);
                    continue;
                }

                double tRaw = Math.log1p(iter) / logMax;
                double t = (Math.pow(tRaw, 4.0) * numCycles) % 1.0;

                Color color = getGradientColor(t);
                pw.setColor(x, y, color);
            }
        }

        executor.shutdown();

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
    public void setGradientModel(GradientModel gradientModel) {
        this.gradientModel = gradientModel;
    }

    public void setMaxIterations(int newMaxIterations) {
        this.maxIterations = newMaxIterations;
    }

    public void setNumCycles(int numCycles) {
        this.numCycles = numCycles;
    }

    //Inner class to implement multi-threading
    class RenderTask implements Runnable {

        private final int startX, endX, startY, endY;
        private final int width, height;
        private final Viewport viewport;
        private final double[][] iterBuffer;

        public RenderTask(int startX, int endX, int startY, int endY,
                          int width, int height,
                          Viewport viewport,
                          double[][] iterBuffer) {

            this.startX = startX;
            this.endX = endX;
            this.startY = startY;
            this.endY = endY;
            this.width = width;
            this.height = height;
            this.viewport = viewport;
            this.iterBuffer = iterBuffer;
        }

        @Override
        public void run() {

            for (int y = startY; y < endY; y++) {
                for (int x = startX; x < endX; x++) {

                    double cx = viewport.mapX(x, width);
                    double cy = viewport.mapY(y, height);

                    iterBuffer[y][x] = mandelbrot(cx, cy);
                }
            }
        }
    }
}
