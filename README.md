# MandelVis

A fractal visualizer app made for exploring the [Mandelbrot Set](https://en.wikipedia.org/wiki/Mandelbrot_set).
This is a student project focused on learning JavaFX basics and good coding practices.

![Screenshot of MandelVis showing a deep zoom into the Mandelbrot set](resources/ScreenShot.png)

## Features

- Drag to draw a selection rectangle and zoom into any region
- Scroll wheel zoom centered on the cursor
- Custom gradient editor with live preview
- Click the gradient bar to add color stops, drag to reorder
- Reset to the default view with one click
- Export a high-resolution PNG (4× upsampled)

## Prerequisites

- Java 25+
- Maven 3.x (the `mvn` command must be on your PATH)

## Build & Run

```bash
mvn javafx:run
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for how to report bugs, suggest features, and open pull requests.

## License

MIT — see [LICENSE](LICENSE).
