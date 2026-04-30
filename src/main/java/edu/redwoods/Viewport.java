package edu.redwoods;

public class Viewport {

    private double minX = -2.5;
    private double maxX = 1.0;
    private double minY = -1.5;
    private double maxY = 1.5;

    public double mapX(int pixelX, int width) {
        return minX + (pixelX / (double) width) * (maxX - minX);
    }

    public double mapY(int pixelY, int height) {
        return minY + (pixelY / (double) height) * (maxY - minY);
    }

    public void zoom(double newMinX, double newMaxX, double newMinY, double newMaxY) {
        minX = newMinX;
        maxX = newMaxX;
        minY = newMinY;
        maxY = newMaxY;
    }

    public double getMinX() { return minX; }
    public double getMinY() { return minY; }
    public double getMaxX() { return maxX; }
    public double getMaxY() { return maxY; }
    public double getRangeX() { return maxX - minX; }
    public double getRangeY() { return maxY - minY; }

}
