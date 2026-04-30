package edu.redwoods;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.paint.Color;


public class GradientStop {

    private final DoubleProperty position =
            new SimpleDoubleProperty();

    private final ObjectProperty<Color> color =
            new SimpleObjectProperty<>();

    public GradientStop(double position, Color color) {
        this.position.set(position);
        this.color.set(color);
    }

    public double getPosition() { return position.get(); }

    public void setPosition(double v) { position.set(v); }

    public DoubleProperty positionProperty() { return position; }

    public Color getColor() { return color.get(); }

    public void setColor(Color c) { color.set(c); }

    public ObjectProperty<Color> colorProperty() { return color; }

}
