package edu.redwoods;

import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MandelbrotRenderer {

    private GradientModel gradientModel;
    private int maxIterations = 500;
    private int numCycles     = 3;

    // -------------------------------------------------------------------------
    // GLITCH_SENTINEL
    //
    // A special value written into iterBuffer to mark a pixel as "glitched" —
    // meaning the perturbation approximation broke down before the pixel escaped.
    // We scan for this value after the perturbation pass and recompute those
    // pixels using full BigDecimal arithmetic.
    //
    // We use -1.0 because real iteration counts are always >= 0.
    // -------------------------------------------------------------------------
    private static final double GLITCH_SENTINEL = -1.0;

    // -------------------------------------------------------------------------
    // GLITCH_THRESHOLD
    //
    // During the perturbation pass, each iteration we check whether the error
    // term ε has grown too large relative to the reference orbit value X.
    //
    // The perturbation equation assumes ε ≪ X.  If that stops being true,
    // the double arithmetic accumulates error and the result is wrong.
    //
    // We flag a glitch when:
    //   |ε|²  >  GLITCH_THRESHOLD × |X|²
    //
    // GLITCH_THRESHOLD = 1e-6 means we allow ε to be at most ~0.001 × |X|
    // before we give up and mark the pixel for BigDecimal recomputation.
    // Raising this value catches more glitches but is more conservative;
    // lowering it misses more glitches but is faster.
    // -------------------------------------------------------------------------
    private static final double GLITCH_THRESHOLD = 1e-6;

    public MandelbrotRenderer(GradientModel gradientModel) {
        this.gradientModel = gradientModel;
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    public WritableImage render(int width, int height, Viewport viewport) {
        return renderInternal(width, height, viewport);
    }

    public WritableImage highResRender(int width, int height, Viewport viewport) {
        // Render at 4× resolution for export; same viewport, same math.
        return renderInternal(width * 4, height * 4, viewport);
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

    // =========================================================================
    // CORE RENDERING PIPELINE
    // =========================================================================

    // -------------------------------------------------------------------------
    // renderInternal  —  three-pass pipeline
    //
    // PASS 1: compute the reference orbit (one BigDecimal calculation)
    // PASS 2: perturbation pass — cheap double math for every pixel
    // PASS 3: glitch correction — BigDecimal fallback for flagged pixels
    // PASS 4: coloring — same as before (unchanged)
    // -------------------------------------------------------------------------
    private WritableImage renderInternal(int width, int height, Viewport viewport) {

        WritableImage image    = new WritableImage(width, height);
        double[][] iterBuffer  = new double[height][width];

        // ------------------------------------------------------------------
        // PASS 1 — Reference orbit
        //
        // Compute the orbit of the viewport CENTER at high precision.
        // This is the one expensive call; every other pixel piggybacks on it.
        // ------------------------------------------------------------------
        ReferenceOrbit refOrbit = computeReferenceOrbit(viewport, maxIterations);

        // ------------------------------------------------------------------
        // PASS 2 — Perturbation (all pixels, multithreaded)
        //
        // Each tile runs a PerturbationTask that iterates the equation:
        //   ε_{n+1} = 2·X_n·ε_n  +  ε_n²  +  δ
        // using double arithmetic.  Pixels whose ε grows too large are
        // written as GLITCH_SENTINEL instead of a real iteration count.
        // ------------------------------------------------------------------
        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        int tileSize = 64;

        List<Future<?>> pass2 = new ArrayList<>();
        for (int ty = 0; ty < height; ty += tileSize) {
            for (int tx = 0; tx < width; tx += tileSize) {
                pass2.add(executor.submit(new PerturbationTask(
                        tx, Math.min(tx + tileSize, width),
                        ty, Math.min(ty + tileSize, height),
                        width, height,
                        viewport, iterBuffer, refOrbit
                )));
            }
        }
        waitForAll(pass2);

        // ------------------------------------------------------------------
        // PASS 3 — Glitch correction (BigDecimal fallback, multithreaded)
        //
        // Scan iterBuffer for GLITCH_SENTINEL values.  For each one, compute
        // the pixel's actual BigDecimal coordinate and run a full high-
        // precision Mandelbrot iteration.
        //
        // This is slower per pixel than the perturbation pass, but glitched
        // pixels are usually a small fraction of the total, so the overall
        // render time stays reasonable.
        // ------------------------------------------------------------------
        List<Future<?>> pass3 = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (iterBuffer[y][x] == GLITCH_SENTINEL) {
                    final int fx = x, fy = y;
                    pass3.add(executor.submit(() -> {

                        // The pixel's complex coordinate:
                        //   C = bigCenter + offset
                        // where offset is a small double (the pixel's position
                        // relative to the center of the view).
                        double offsetX = viewport.getOffsetX(fx, width);
                        double offsetY = viewport.getOffsetY(fy, height);

                        // Determine how many BigDecimal digits we need.
                        // We need enough to distinguish neighboring pixels at
                        // this zoom depth, plus 15 spare digits for safety.
                        int digits = precisionForViewport(viewport);
                        MathContext mc = new MathContext(digits);

                        BigDecimal cx = viewport.getBigCenterX()
                                .add(new BigDecimal(offsetX), mc);
                        BigDecimal cy = viewport.getBigCenterY()
                                .add(new BigDecimal(offsetY), mc);

                        iterBuffer[fy][fx] = mandelbrotBigDecimal(cx, cy, maxIterations, mc);
                    }));
                }
            }
        }
        waitForAll(pass3);

        executor.shutdown();

        // ------------------------------------------------------------------
        // PASS 4 — Coloring (unchanged from original)
        // ------------------------------------------------------------------
        PixelWriter pw   = image.getPixelWriter();
        double logMax    = Math.log1p(maxIterations);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double iter = iterBuffer[y][x];
                if (iter >= maxIterations) {
                    pw.setColor(x, y, Color.BLACK);
                    continue;
                }
                double tRaw = Math.log1p(iter) / logMax;
                double t    = (Math.pow(tRaw, 4.0) * numCycles) % 1.0;
                pw.setColor(x, y, getGradientColor(t));
            }
        }

        return image;
    }

    // =========================================================================
    // REFERENCE ORBIT COMPUTATION  (BigDecimal)
    // =========================================================================

    // -------------------------------------------------------------------------
    // computeReferenceOrbit
    //
    // Runs the standard Mandelbrot iteration
    //   Z_{n+1} = Z_n²  +  C₀
    // for C₀ = the center of the viewport, using BigDecimal arithmetic.
    //
    // We store each X_n value as a double (after converting from BigDecimal)
    // because the perturbation equation only needs them as double multipliers.
    // The precision gain comes from computing C₀ accurately, not from keeping
    // the full orbit in BigDecimal.
    //
    // The loop stops either when the orbit escapes (|Z| > 2) or when we hit
    // maxIterations.  The `length` field of the returned ReferenceOrbit tells
    // callers how many steps were actually computed.
    // -------------------------------------------------------------------------
    private ReferenceOrbit computeReferenceOrbit(Viewport viewport, int maxIter) {

        int digits  = precisionForViewport(viewport);
        MathContext mc = new MathContext(digits);

        BigDecimal TWO  = BigDecimal.valueOf(2);
        BigDecimal cx0  = viewport.getBigCenterX();
        BigDecimal cy0  = viewport.getBigCenterY();

        BigDecimal zx = BigDecimal.ZERO;
        BigDecimal zy = BigDecimal.ZERO;

        double[] re = new double[maxIter + 1];
        double[] im = new double[maxIter + 1];
        int length  = 0;

        for (int n = 0; n < maxIter; n++) {

            // Store current X_n before advancing.
            re[n] = zx.doubleValue();
            im[n] = zy.doubleValue();
            length = n + 1;

            // Z_{n+1} = Z_n² + C₀
            //   real part:  zx² - zy² + cx0
            //   imag part:  2·zx·zy   + cy0
            BigDecimal newZx = zx.multiply(zx, mc)
                    .subtract(zy.multiply(zy, mc), mc)
                    .add(cx0, mc);
            BigDecimal newZy = TWO.multiply(zx, mc)
                    .multiply(zy, mc)
                    .add(cy0, mc);
            zx = newZx;
            zy = newZy;

            // Escape check — use doubleValue() for the magnitude test since
            // we only need to know if |Z| has crossed 2.0.
            double mag2 = zx.doubleValue() * zx.doubleValue()
                        + zy.doubleValue() * zy.doubleValue();
            if (mag2 > 4.0) break;
        }

        return new ReferenceOrbit(re, im, length);
    }

    // =========================================================================
    // BIGDECIMAL FALLBACK  (glitch correction)
    // =========================================================================

    // -------------------------------------------------------------------------
    // mandelbrotBigDecimal
    //
    // A full, from-scratch Mandelbrot iteration using BigDecimal arithmetic.
    // Used only for pixels that the perturbation pass flagged as glitched.
    //
    // It is identical in logic to the original `mandelbrot(double, double)` method
    // that was here before, but uses BigDecimal throughout so it stays accurate
    // at any zoom depth (as long as the MathContext has enough digits).
    //
    // Returns a smooth (non-integer) iteration count for nice coloring,
    // same formula as the original.
    // -------------------------------------------------------------------------
    private double mandelbrotBigDecimal(BigDecimal cx, BigDecimal cy,
                                        int maxIter, MathContext mc) {
        BigDecimal TWO = BigDecimal.valueOf(2);
        BigDecimal zx  = BigDecimal.ZERO;
        BigDecimal zy  = BigDecimal.ZERO;

        for (int iter = 0; iter < maxIter; iter++) {
            BigDecimal newZx = zx.multiply(zx, mc)
                    .subtract(zy.multiply(zy, mc), mc)
                    .add(cx, mc);
            BigDecimal newZy = TWO.multiply(zx, mc)
                    .multiply(zy, mc)
                    .add(cy, mc);
            zx = newZx;
            zy = newZy;

            double zxD   = zx.doubleValue();
            double zyD   = zy.doubleValue();
            double mag2  = zxD * zxD + zyD * zyD;

            if (mag2 > 4.0) {
                double modulus = Math.sqrt(mag2);
                return iter + 1 - Math.log(Math.log(modulus)) / Math.log(2);
            }
        }

        return maxIter; // point is in the set
    }

    // =========================================================================
    // PERTURBATION TASK  (inner class — runs on thread-pool threads)
    // =========================================================================

    // -------------------------------------------------------------------------
    // PerturbationTask
    //
    // Replaces the old RenderTask.  Instead of computing each pixel's full
    // Mandelbrot orbit from scratch, it tracks only the DEVIATION ε from the
    // pre-computed reference orbit.
    //
    // The perturbation equation:
    //   ε_{n+1}  =  2·X_n·ε_n  +  ε_n²  +  δ
    //
    // where:
    //   X_n  = reference orbit at step n  (looked up from refOrbit arrays)
    //   ε_n  = this pixel's deviation from the reference at step n  (double)
    //   δ    = this pixel's fixed offset from the reference center  (double)
    //
    // The actual orbit of the pixel is Z_n = X_n + ε_n, so the escape check is
    //   |X_n + ε_n|² > 4
    //
    // Glitch detection: if |ε|² grows larger than GLITCH_THRESHOLD × |X|²,
    // the approximation "ε ≪ X" is no longer valid.  We write GLITCH_SENTINEL
    // and let Pass 3 recompute this pixel with BigDecimal.
    // -------------------------------------------------------------------------
    class PerturbationTask implements Runnable {

        private final int startX, endX, startY, endY;
        private final int width, height;
        private final Viewport viewport;
        private final double[][] iterBuffer;
        private final ReferenceOrbit refOrbit;

        PerturbationTask(int startX, int endX, int startY, int endY,
                         int width, int height,
                         Viewport viewport,
                         double[][] iterBuffer,
                         ReferenceOrbit refOrbit) {
            this.startX    = startX;
            this.endX      = endX;
            this.startY    = startY;
            this.endY      = endY;
            this.width     = width;
            this.height    = height;
            this.viewport  = viewport;
            this.iterBuffer = iterBuffer;
            this.refOrbit  = refOrbit;
        }

        @Override
        public void run() {
            for (int y = startY; y < endY; y++) {
                for (int x = startX; x < endX; x++) {
                    iterBuffer[y][x] = perturbedIteration(x, y);
                }
            }
        }

        private double perturbedIteration(int px, int py) {

            // δ: this pixel's offset from the reference center in the complex plane.
            // This is a small double even at extreme zoom (see Viewport.getOffsetX).
            double dRe = viewport.getOffsetX(px, width);
            double dIm = viewport.getOffsetY(py, height);

            // ε starts at zero: at n=0 the pixel deviation equals its offset,
            // which the first iteration of the equation below will introduce via δ.
            double eRe = 0.0;
            double eIm = 0.0;

            boolean escaped  = false;
            boolean glitched = false;

            for (int n = 0; n < refOrbit.length; n++) {

                // Look up X_n from the pre-computed reference orbit.
                double xRe = refOrbit.re[n];
                double xIm = refOrbit.im[n];

                // ----------------------------------------------------------
                // Perturbation equation (complex arithmetic, expanded):
                //   ε_{n+1} = 2·X_n·ε_n  +  ε_n²  +  δ
                //
                // 2·X_n·ε_n  (complex multiply then ×2):
                //   real: 2·(xRe·eRe  -  xIm·eIm)
                //   imag: 2·(xRe·eIm  +  xIm·eRe)
                //
                // ε_n²  (complex square):
                //   real: eRe·eRe  -  eIm·eIm
                //   imag: 2·eRe·eIm
                //
                // Then add δ (real: dRe, imag: dIm).
                // ----------------------------------------------------------
                double newERe = 2.0 * (xRe * eRe - xIm * eIm)
                              + (eRe * eRe - eIm * eIm)
                              + dRe;
                double newEIm = 2.0 * (xRe * eIm + xIm * eRe)
                              + 2.0 * eRe * eIm
                              + dIm;
                eRe = newERe;
                eIm = newEIm;

                // Reconstruct actual Z_n = X_n + ε_n for the escape check
                // and for smooth-coloring when the point escapes.
                double zRe   = xRe + eRe;
                double zIm   = xIm + eIm;
                double zMag2 = zRe * zRe + zIm * zIm;

                if (zMag2 > 4.0) {
                    // Smooth coloring: same logarithmic formula as the original.
                    double modulus = Math.sqrt(zMag2);
                    iterBuffer[py][px] = n + 1 - Math.log(Math.log(modulus)) / Math.log(2);
                    escaped = true;
                    break;
                }

                // ----------------------------------------------------------
                // Glitch detection
                //
                // When |ε|² > GLITCH_THRESHOLD × |X|² the error term is no
                // longer small relative to the reference orbit.  The double
                // arithmetic is accumulating too much error to trust.
                //
                // We skip the check when |X|² is very small (near zero) to
                // avoid false positives at the very start of the orbit, when
                // both X and ε are legitimately near zero.
                // ----------------------------------------------------------
                double eMag2 = eRe * eRe + eIm * eIm;
                double xMag2 = xRe * xRe + xIm * xIm;

                if (xMag2 > 1e-20 && eMag2 > GLITCH_THRESHOLD * xMag2) {
                    iterBuffer[py][px] = GLITCH_SENTINEL;
                    glitched = true;
                    break;
                }
            }

            // If we exhausted the reference orbit without escaping or glitching:
            if (!escaped && !glitched) {
                if (refOrbit.length < maxIterations) {
                    // The reference orbit escaped before maxIterations, but this
                    // pixel has not.  That mismatch means we need a new reference
                    // — mark it as a glitch for BigDecimal recomputation.
                    iterBuffer[py][px] = GLITCH_SENTINEL;
                } else {
                    // Reference ran to maxIterations, pixel ran with it: in the set.
                    iterBuffer[py][px] = maxIterations;
                }
            }

            return iterBuffer[py][px];
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    // -------------------------------------------------------------------------
    // precisionForViewport
    //
    // Returns the number of BigDecimal digits needed to render at this zoom level.
    //
    // Rule of thumb: we need one digit per decade of zoom depth, plus 15 spare
    // digits so the iteration arithmetic doesn't eat into our precision budget.
    // The minimum of 30 keeps overhead low at shallow zoom.
    // -------------------------------------------------------------------------
    private static int precisionForViewport(Viewport viewport) {
        double rangeX = viewport.getRangeX();
        if (rangeX <= 0) return 60;
        int depthDigits = (int) Math.ceil(-Math.log10(rangeX));
        return Math.max(30, depthDigits + 15);
    }

    // -------------------------------------------------------------------------
    // waitForAll  —  blocks until every submitted task completes.
    // -------------------------------------------------------------------------
    private static void waitForAll(List<Future<?>> futures) {
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // =========================================================================
    // GRADIENT / COLORING  (unchanged from original)
    // =========================================================================

    private Color getGradientColor(double t) {
        var stops = gradientModel.getStops();

        if (stops.isEmpty()) return Color.BLACK;
        if (stops.size() == 1) return stops.get(0).getColor();

        GradientStop first = stops.get(0);
        GradientStop last  = stops.get(stops.size() - 1);

        // ---------------------------------------------------------------------
        // Wrap-around segment: interpolate from the LAST stop back to the FIRST.
        //
        // Without this, the gradient clamps at both ends, so when the coloring
        // formula cycles t back to 0 there is a hard jump between whatever color
        // is at position 1.0 and whatever color is at position 0.0.
        //
        // With wrapping, that transition is a smooth blend just like any other
        // adjacent pair of stops.  The wrap segment spans the gap between
        // last.position and (1.0 + first.position) — i.e. the "empty" space on
        // both sides of the stop list treated as a single bridging segment.
        // ---------------------------------------------------------------------
        double segLen = (1.0 - last.getPosition()) + first.getPosition();

        if (t >= last.getPosition()) {
            if (segLen <= 0) return last.getColor();
            double localT = (t - last.getPosition()) / segLen;
            return last.getColor().interpolate(first.getColor(), localT);
        }

        if (t <= first.getPosition()) {
            if (segLen <= 0) return first.getColor();
            // t is on the "far side" of the wrap — measure from last.position,
            // looping through 1.0 to reach t.
            double localT = (t + 1.0 - last.getPosition()) / segLen;
            return last.getColor().interpolate(first.getColor(), localT);
        }

        // Standard case: find the segment that contains t.
        for (int i = 0; i < stops.size() - 1; i++) {
            GradientStop a = stops.get(i);
            GradientStop b = stops.get(i + 1);
            if (t >= a.getPosition() && t <= b.getPosition()) {
                double localT = (t - a.getPosition())
                              / (b.getPosition() - a.getPosition());
                return a.getColor().interpolate(b.getColor(), localT);
            }
        }

        return last.getColor();
    }
}
