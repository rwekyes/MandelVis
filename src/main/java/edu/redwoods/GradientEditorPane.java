package edu.redwoods;

import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class GradientEditorPane extends VBox {

    private final GradientModel model;
    private final GradientPreview preview;

    public GradientEditorPane(GradientModel model) {

        this.model = model;
        this.preview = new GradientPreview(model);

        setSpacing(10);
        setPadding(new Insets(10));
        setPrefWidth(300);

        preview.setHeight(40);

        Label title = new Label("Gradient Editor");

        ListView<GradientStop> listView = new ListView<>(model.getStops());

        listView.setMaxWidth(Double.MAX_VALUE);
        ScrollPane sp = new ScrollPane(listView);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);

        listView.setCellFactory(lv -> new ListCell<>() {
            private final HBox box = new HBox(10);
            private final Rectangle swatch = new Rectangle(10, 10);
            private final Slider positionSlider = new Slider(0, 1, 0);
            private final ColorPicker picker = new ColorPicker();
            private final Button deleteBtn = new Button("X");
            private GradientStop currentStop = null;

            {
                box.getChildren().addAll(swatch, positionSlider, picker, deleteBtn); // Add the stuff

                positionSlider.setPrefWidth(80); // Set the widths on the sliders to prevent them spilling
                positionSlider.setMaxWidth(80);

                // Only draw and sort on mouse release
                positionSlider.setOnMouseReleased(e -> {
                    model.sortStops();
                    preview.draw();
                });
            }

            @Override
            protected void updateItem(GradientStop stop, boolean empty) {
                super.updateItem(stop, empty);

                // Always unbind before doing anything else
                if (currentStop != null) {
                    swatch.fillProperty().unbind();
                    picker.valueProperty().unbindBidirectional(currentStop.colorProperty());
                    positionSlider.valueProperty().unbindBidirectional(currentStop.positionProperty());
                }
                currentStop = stop;

                if (empty || stop == null) {
                    setGraphic(null);
                    return;
                }

                // Fresh bindings to this stop
                swatch.fillProperty().bind(stop.colorProperty());
                picker.valueProperty().bindBidirectional(stop.colorProperty());
                positionSlider.valueProperty().bindBidirectional(stop.positionProperty());

                deleteBtn.setOnAction(e -> model.removeStop(stop));

                setGraphic(box);
            }
        });

        Button addStopButton = new Button("Add Stop");
        addStopButton.setOnAction(e -> {
            model.addStop(0.5, Color.BLACK);
            listView.setItems(model.getStops());
        });



        listView.setOnDragDetected (e -> {
            int index = listView.getSelectionModel().getSelectedIndex();
            if (index < 0) return;

            Dragboard db = listView.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(String.valueOf(index));
            db.setContent(content);
        });

        getChildren().addAll(title, preview, sp, addStopButton);
    }
}
