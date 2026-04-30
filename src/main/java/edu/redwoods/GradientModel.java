package edu.redwoods;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.paint.Color;

import java.util.Comparator;
import java.util.List;

public class GradientModel {

    private ObservableList<GradientStop> stops =
            FXCollections.observableArrayList();

    public ObservableList<GradientStop> getStops() {
        return stops;
    }

    public void sortStops() {
        FXCollections.sort(stops, Comparator.comparingDouble(GradientStop::getPosition));
    }

    public void replaceStops(List<GradientStop> gradient) {

        for(GradientStop stop : stops){
            stops.remove(stop);
        };

        for(int i = 0; i < gradient.size(); i++) {
            stops.add(i,gradient.get(i));
        };
    }

    public void addStop(double p, Color c) {
        stops.add(new GradientStop(p, c));
        sortStops();
    }

    public void removeStop(GradientStop stop) {
        stops.remove(stop);
    }

}
