package edu.redwoods;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.paint.Color;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.List;

public class MainApp extends Application{

    private MandelbrotRenderer renderer;
    private Viewport viewport = new Viewport();

    private Canvas canvas;

    private List<GradientStop> stops;

    private double startX;
    private double startY;
    private double currentX;
    private double currentY;
    private boolean dragging = false;
    private StackPane canvasHolder;
    private WritableImage lastRenderedImage;

    @Override
    public void start(Stage stage) {

        GradientModel gradientModel = new GradientModel();

        gradientModel.addStop(0.0, Color.BLACK);
        gradientModel.addStop(0.1, Color.BLUE);
        gradientModel.addStop(0.2, Color.PURPLE);
        gradientModel.addStop(0.3, Color.MEDIUMPURPLE);
        gradientModel.addStop(0.4, Color.RED);
        gradientModel.addStop(0.5, Color.ORANGERED);
        gradientModel.addStop(0.6, Color.ORANGE);
        gradientModel.addStop(0.7, Color.GREEN);
        gradientModel.addStop(0.8, Color.LIME);
        gradientModel.addStop(0.9, Color.YELLOWGREEN);
        gradientModel.addStop(1.0, Color.BLACK);

        gradientModel.getStops().addListener(
                (javafx.collections.ListChangeListener<GradientStop>) c -> render()
        );

        renderer = new MandelbrotRenderer(gradientModel);

        canvas = new Canvas(1000, 750);

        lastRenderedImage = new WritableImage(1000, 750);

        BorderPane root = new BorderPane();

        canvasHolder = new StackPane(canvas);

        StackPane.setAlignment(canvas, Pos.CENTER);

        root.setCenter(canvasHolder);

        Button saveButton = new Button("Save Image");
        saveButton.setOnAction(e -> saveImage());

        Button resetButton = new Button("Reset Image");
        resetButton.setOnAction(e -> resetImage());

        renderer.setGradientModel(gradientModel);

        GradientEditorPane editorPane = new GradientEditorPane(gradientModel);

        Button toggleEditorButton = new Button("Hide Editor");
        toggleEditorButton.setOnAction(e -> {
            boolean isShowing = root.getRight() != null;
            if (isShowing) {
                TranslateTransition slide = new TranslateTransition(Duration.millis(200), editorPane);
                slide.setFromX(0);
                slide.setToX(editorPane.getWidth());
                slide.setOnFinished(ev -> {
                    root.setRight(null);
                    editorPane.setTranslateX(0);
                    Platform.runLater(() -> {
                        resizeCanvas();
                        render();
                    });
                });
                slide.play();
                toggleEditorButton.setText("Show Editor");
            } else {
                root.setRight(editorPane);
                TranslateTransition slide = new TranslateTransition(Duration.millis(200), editorPane);
                slide.setFromX(editorPane.getWidth());
                slide.setToX(0);
                slide.setOnFinished(ev -> {
                    Platform.runLater(() -> {
                        resizeCanvas();
                        render();
                    });
                });
                slide.play();
                toggleEditorButton.setText("Hide Editor");
            }
        });

        root.setRight(editorPane);

        HBox topBar = new HBox(saveButton, resetButton, toggleEditorButton);

        root.setTop(topBar);

        Scene scene = new Scene(root);
        ((Region) scene.getRoot()).minWidthProperty().bind(editorPane.widthProperty().add(300)); // Sets minimum width and prevents the gradient pane dissappearing


        stage.setScene(scene);
        root.setStyle("-fx-background-color: black;");
        stage.setTitle("Mandelbrot Visualizer");
        stage.show();



        ChangeListener<Number> resizeListener = (obs, oldVal, newVal) -> {
            resizeCanvas();
            render();
        };

        canvasHolder.widthProperty().addListener(resizeListener);
        canvasHolder.heightProperty().addListener(resizeListener);

        setupMouseZoom();

        render();
        Platform.runLater(() -> {
            resizeCanvas();
            render();
        });
    }

    private void resizeCanvas() {
        double availableWidth = canvasHolder.getWidth();
        double availableHeight = canvasHolder.getHeight();
        double aspectRatio = viewport.getRangeX() / viewport.getRangeY();
        double newWidth, newHeight;

        double containerRatio = availableWidth / availableHeight;

        if (containerRatio > aspectRatio) {
            // Container is wider → limit by height
            newHeight = availableHeight;
            newWidth = newHeight * aspectRatio;
        } else {
            // Container is taller → limit by width
            newWidth = availableWidth;
            newHeight = newWidth / aspectRatio;
        }

        canvas.setWidth(newWidth);
        canvas.setHeight(newHeight);

        double centerX = (viewport.getMinX() + viewport.getMaxX()) / 2.0;
        double centerY = (viewport.getMinY() + viewport.getMaxY()) / 2.0;
        double halfRangeX = viewport.getRangeX() / 2.0;
        double halfRangeY = (newHeight / newWidth) * halfRangeX;

    }

    private void render() {
        int width = (int) canvas.getWidth();
        int height = (int) canvas.getHeight();

        // This dynamically scales the MaxIterations based on zoom level
        int newMaxIterations = (int)(50 * Math.log10(1.0 / viewport.getRangeX()));
        newMaxIterations = Math.max(newMaxIterations, 100);
        renderer.setMaxIterations(newMaxIterations);

        Task<WritableImage> task = new Task<>() {
            @Override
            protected WritableImage call() {
                return renderer.render(width, height, viewport);
            }

            @Override
            protected void succeeded(){
                WritableImage image = getValue();
                GraphicsContext gc = canvas.getGraphicsContext2D();
                gc.drawImage(image, 0, 0);
                lastRenderedImage = image;
                if (dragging) {
                    drawSelectionRectangle(gc);
                }
            }

            @Override
            protected void failed() {
                Throwable ex = getException();
                ex.printStackTrace();
            }
        };

        new Thread(task).start();
    }

    private void drawSelectionRectangle(GraphicsContext gc) {

        double dx = currentX - startX;
        double dy = currentY - startY;

        double aspect = canvas.getWidth() / canvas.getHeight();

        if (Math.abs(dx / dy) > aspect) {
            dy = Math.signum(dy) * Math.abs(dx) / aspect;
        } else {
            dx = Math.signum(dx) * Math.abs(dy) * aspect;
        }

        double minX = Math.min(startX, startX + dx);
        double minY = Math.min(startY, startY + dy);
        double width = Math.abs(dx);
        double height = Math.abs(dy);

        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2);
        gc.strokeRect(minX, minY, width, height);

        gc.setFill(Color.color(1,1,1,0.2));
        gc.fillRect(minX, minY, width, height);


    }

    private void setupMouseZoom() {

        canvas.setOnMousePressed(event -> {
            startX = event.getX();
            startY = event.getY();
            dragging = true;
        });

        canvas.setOnMouseDragged(e -> {
            currentX = e.getX();
            currentY = e.getY();
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.drawImage(lastRenderedImage, 0, 0);  // repaint the fractal
            drawSelectionRectangle(gc);             // draw rectangle on top
        });

        canvas.setOnMouseReleased(e -> {

            dragging = false;

            double dx = currentX - startX;
            double dy = currentY - startY;

            if (Math.abs(dx) < 5 || Math.abs(dy) < 5) return;

            double aspect = canvas.getWidth() / canvas.getHeight();

            if (Math.abs(dx / dy) > aspect) {
                dy = Math.signum(dy) * Math.abs(dx) / aspect;
            } else {
                dx = Math.signum(dx) * Math.abs(dy) * aspect;
            }

            double minX = Math.min(startX, startX + dx);
            double maxX = Math.max(startX, startX + dx);
            double minY = Math.min(startY, startY + dy);
            double maxY = Math.max(startY, startY + dy);

            double newMinX = viewport.mapX((int) minX, (int) canvas.getWidth());
            double newMaxX = viewport.mapX((int) maxX, (int) canvas.getWidth());
            double newMinY = viewport.mapY((int) minY, (int) canvas.getHeight());
            double newMaxY = viewport.mapY((int) maxY, (int) canvas.getHeight());

            viewport.zoom(newMinX, newMaxX, newMinY, newMaxY);

            render();
        });

        canvas.setOnScroll(event -> {

            double zoomFactor = 0.9; // smaller = faster zoom

            if (event.getDeltaY() <0) {
                zoomFactor = 1.1; // scroll down = zoom out
            }

            double mouseX = event.getX();
            double mouseY = event.getY();

            double width = canvas.getWidth();
            double height = canvas.getHeight();

            double centerX = viewport.mapX((int) mouseX, (int) width);
            double centerY = viewport.mapY((int) mouseY, (int) height);

            double newMinX = centerX + (viewport.getMinX() - centerX) * zoomFactor;
            double newMaxX = centerX + (viewport.getMaxX() - centerX) * zoomFactor;
            double newMinY = centerY + (viewport.getMinY() - centerY) * zoomFactor;
            double newMaxY = centerY + (viewport.getMaxY() - centerY) * zoomFactor;

            viewport.zoom(newMinX, newMaxX, newMinY, newMaxY);

            render();
        });
    }

    private void saveImage(){
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG files", "*.png"));
        File file = fileChooser.showSaveDialog(null);

        if (file != null) {
            try {
                var image = renderer.highResRender(
                        (int) canvas.getWidth(),
                        (int) canvas.getHeight(),
                        viewport
                );
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setHeaderText("Saved");
                alert.setContentText(file.getAbsolutePath());
                alert.showAndWait();
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setHeaderText("Failed to Save");
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
            }
        }

    }

    private void resetImage() {
        viewport.zoom(-2.5, 1.0, -1.5, 1.5);
        render();
    }

    public static void main(String[] args) {
        launch();
    }
}
