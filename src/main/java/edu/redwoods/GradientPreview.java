package edu.redwoods;

import javafx.collections.ListChangeListener;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class GradientPreview extends Canvas {

    private final GradientModel model;

    public GradientPreview(GradientModel model) {
        this.model = model;

        widthProperty().addListener(e -> draw());
        heightProperty().addListener(e -> draw());
        model.getStops().addListener((ListChangeListener<? super GradientStop>) c -> draw());

        draw();

        setOnMouseClicked(e -> {
            double p = e.getX() / getWidth();
            model.addStop(p, Color.WHITE);
        });
    }

    public void draw() {

        GraphicsContext gc = getGraphicsContext2D();

        int w = (int)getWidth();
        int h = (int)getHeight();

        for (int x = 0; x < w; x++) {
            double t = x / (double) w;
            Color c = interpolate(t);

            gc.setStroke(c);
            gc.strokeLine(x, 0, x, h);
        }

        drawStopMarkers(gc);
    }

    private Color interpolate(double t) {

        var stops = model.getStops();
        if (stops.isEmpty()) return Color.BLACK;

        for (int i = 0; i < stops.size() - 1; i++) {
            GradientStop a = stops.get(i);
            GradientStop b = stops.get(i + 1);

            if (t >= a.getPosition() && t <= b.getPosition()) {
                double localT =
                        (t - a.getPosition()) /
                                (b.getPosition() - a.getPosition());
                return a.getColor().interpolate(b.getColor(), localT);
            }
        }

        return stops.get(stops.size() - 1).getColor();
    }

    public void drawStopMarkers(GraphicsContext gc) {
        for (GradientStop stop : model.getStops()) {
            double x = stop.getPosition() * getWidth();

            gc.setFill(stop.getColor());
            gc.fillOval(x - 5, getHeight() - 15, 10, 10);

            gc.setStroke(Color.BLACK);
            gc.strokeOval(x - 5, getHeight() -15, 10, 10);
        }
    }


}
