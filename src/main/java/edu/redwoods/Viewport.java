package edu.redwoods;

import java.math.BigDecimal;
import java.math.MathContext;

public class Viewport {

    // -------------------------------------------------------------------------
    // WHY BigDecimal for the center?
    //
    // A Java `double` has about 15-16 significant decimal digits.  The initial
    // Mandelbrot view is roughly 3.5 units wide.  Each zoom step shrinks that
    // range.  Once the range drops below ~1e-14, two adjacent pixels map to the
    // exact same `double` coordinate, so they always get the same color — that
    // is the pixelation bug.
    //
    // The fix: store the CENTER of the view as a BigDecimal (arbitrary precision)
    // and keep only the RANGE (width/height of the window) as a `double`.
    //
    // The range is a relative measurement: it just gets smaller.  "1e-20" is
    // representable as a double with full relative precision even though an
    // absolute coordinate at that scale loses all precision.
    //
    // Each pixel's complex-plane coordinate is then computed as:
    //   coord = bigCenter + offset_from_center
    // where `offset_from_center` is at most range/2 — a small double that is
    // accurate relative to itself, even if it is tiny in absolute terms.
    // -------------------------------------------------------------------------

    // High-precision center of the viewport in the complex plane.
    private BigDecimal bigCenterX;
    private BigDecimal bigCenterY;

    // Width and height of the visible window in complex-plane units.
    // These shrink as you zoom in but remain representable and meaningful as doubles.
    private double rangeX;
    private double rangeY;

    // Derived double bounds kept for UI / coloring code that still needs them.
    // At extreme zoom these lose precision, but the renderer no longer uses them
    // directly for per-pixel math — it uses bigCenter + offset instead.
    private double minX, maxX, minY, maxY;

    // -------------------------------------------------------------------------
    // Constructor — sets the classic overview of the Mandelbrot set.
    // -------------------------------------------------------------------------
    public Viewport() {
        bigCenterX = new BigDecimal("-0.75");
        bigCenterY = new BigDecimal("0.0");
        rangeX     = 3.5;
        rangeY     = 3.0;
        updateDoubleBounds();
    }

    // -------------------------------------------------------------------------
    // updateDoubleBounds
    //
    // Recomputes minX/maxX/minY/maxY from the BigDecimal center and the range.
    // Called after every zoom so that UI code (aspect-ratio logic, selection
    // rectangles) always has fresh double bounds to read.
    // -------------------------------------------------------------------------
    private void updateDoubleBounds() {
        double cx = bigCenterX.doubleValue();
        double cy = bigCenterY.doubleValue();
        minX = cx - rangeX / 2.0;
        maxX = cx + rangeX / 2.0;
        minY = cy - rangeY / 2.0;
        maxY = cy + rangeY / 2.0;
    }

    // -------------------------------------------------------------------------
    // zoomAroundPoint  —  used by the scroll-wheel zoom.
    //
    // mouseComplexX/Y: the complex-plane coordinate the mouse is hovering over.
    //   (Computed in double by mapX/mapY — fine, because it is only used to
    //    compute a DELTA from the current center, not an absolute coordinate.)
    // factor: < 1.0 zooms in, > 1.0 zooms out.
    //
    // After a zoom-in, the viewport center moves toward the mouse position.
    // The delta is:
    //   delta = (mouse - oldCenter) * (1 - factor)
    //
    // Because delta is a relative offset within the current view (which is a
    // small double), adding it to bigCenter as a BigDecimal keeps the center
    // precise even after thousands of scroll events.
    // -------------------------------------------------------------------------
    public void zoomAroundPoint(double mouseComplexX, double mouseComplexY, double factor) {
        double oldCX = bigCenterX.doubleValue();
        double oldCY = bigCenterY.doubleValue();

        // How much the center shifts, expressed as a double offset.
        // At any practical zoom depth this offset is small enough to be accurate.
        double deltaX = (mouseComplexX - oldCX) * (1.0 - factor);
        double deltaY = (mouseComplexY - oldCY) * (1.0 - factor);

        // Add the delta to the BigDecimal center.
        // MathContext.DECIMAL128 gives 34 significant decimal digits — enough
        // for zoom depths down to roughly 1e-30.
        bigCenterX = bigCenterX.add(new BigDecimal(deltaX), MathContext.DECIMAL128);
        bigCenterY = bigCenterY.add(new BigDecimal(deltaY), MathContext.DECIMAL128);

        rangeX *= factor;
        rangeY *= factor;
        updateDoubleBounds();
    }

    // -------------------------------------------------------------------------
    // zoomToSelection  —  used by the click-and-drag box zoom.
    //
    // The four parameters are the complex-plane bounds of the drawn rectangle
    // (still computed in double from mapX/mapY, which is fine for the delta).
    // The new center is the midpoint of the rectangle; new range is its size.
    // -------------------------------------------------------------------------
    public void zoomToSelection(double selMinX, double selMaxX,
                                double selMinY, double selMaxY) {
        double oldCX = bigCenterX.doubleValue();
        double oldCY = bigCenterY.doubleValue();

        // Midpoint of the selection, then its offset from the current center.
        double newCX  = (selMinX + selMaxX) / 2.0;
        double newCY  = (selMinY + selMaxY) / 2.0;
        double deltaX = newCX - oldCX;
        double deltaY = newCY - oldCY;

        bigCenterX = bigCenterX.add(new BigDecimal(deltaX), MathContext.DECIMAL128);
        bigCenterY = bigCenterY.add(new BigDecimal(deltaY), MathContext.DECIMAL128);

        rangeX = selMaxX - selMinX;
        rangeY = selMaxY - selMinY;
        updateDoubleBounds();
    }

    // -------------------------------------------------------------------------
    // reset  —  called by the Reset button in MainApp.
    // Re-seeds bigCenter with exact decimal strings so there is no double
    // rounding error in the initial coordinates.
    // -------------------------------------------------------------------------
    public void reset() {
        bigCenterX = new BigDecimal("-0.75");
        bigCenterY = new BigDecimal("0.0");
        rangeX     = 3.5;
        rangeY     = 3.0;
        updateDoubleBounds();
    }

    // -------------------------------------------------------------------------
    // Legacy zoom(double,double,double,double)
    //
    // Still used by reset via the old call-site pattern if callers have not been
    // updated yet.  At shallow zoom this is fine; at deep zoom, use the methods
    // above instead.
    // -------------------------------------------------------------------------
    public void zoom(double newMinX, double newMaxX, double newMinY, double newMaxY) {
        double newCX = (newMinX + newMaxX) / 2.0;
        double newCY = (newMinY + newMaxY) / 2.0;
        bigCenterX = new BigDecimal(newCX);
        bigCenterY = new BigDecimal(newCY);
        rangeX = newMaxX - newMinX;
        rangeY = newMaxY - newMinY;
        updateDoubleBounds();
    }

    // -------------------------------------------------------------------------
    // mapX / mapY
    //
    // Maps a pixel coordinate to a double complex-plane coordinate.
    // Still works correctly for display purposes and for computing deltas.
    // The renderer does NOT call these for per-pixel math; it calls getOffsetX/Y.
    // -------------------------------------------------------------------------
    public double mapX(int pixelX, int width) {
        return minX + (pixelX / (double) width) * rangeX;
    }

    public double mapY(int pixelY, int height) {
        return minY + (pixelY / (double) height) * rangeY;
    }

    // -------------------------------------------------------------------------
    // getOffsetX / getOffsetY
    //
    // Returns a pixel's position relative to the CENTER of the viewport, in
    // complex-plane units.  This is what perturbation theory calls "delta" (δ).
    //
    // Key insight: even at extreme zoom, this offset is a small number whose
    // RELATIVE precision is fine in double.  Example: at rangeX = 1e-20 and
    // width = 1000, a pixel one step from center has offset ≈ 1e-23.  That value
    // has ~15 significant digits in double, which is all we need for the delta.
    //
    // +0.5 centers the sample point inside each pixel square.
    // -------------------------------------------------------------------------
    public double getOffsetX(int pixelX, int width) {
        return ((pixelX + 0.5) / width - 0.5) * rangeX;
    }

    public double getOffsetY(int pixelY, int height) {
        return ((pixelY + 0.5) / height - 0.5) * rangeY;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------
    public BigDecimal getBigCenterX() { return bigCenterX; }
    public BigDecimal getBigCenterY() { return bigCenterY; }

    public double getMinX()    { return minX;   }
    public double getMaxX()    { return maxX;   }
    public double getMinY()    { return minY;   }
    public double getMaxY()    { return maxY;   }
    public double getRangeX()  { return rangeX; }
    public double getRangeY()  { return rangeY; }
}
