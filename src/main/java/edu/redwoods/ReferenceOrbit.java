package edu.redwoods;

// =============================================================================
// ReferenceOrbit
//
// Holds the pre-computed orbit of the one "reference point" chosen for a render
// — the center of the current viewport.
//
// WHY does this exist?
//   Perturbation theory lets us compute every pixel cheaply by tracking only its
//   small deviation (ε) from this reference orbit.  But first we need the
//   reference orbit itself, computed once using BigDecimal so it is accurate even
//   at extreme zoom depths.
//
// WHAT is stored?
//   re[n] and im[n] are the real and imaginary parts of X_n — the reference
//   orbit's value at iteration step n.  They are stored as doubles because the
//   perturbation equation uses them as multipliers in double arithmetic:
//
//     ε_{n+1} = 2·X_n·ε_n  +  ε_n²  +  δ
//
//   The precision gain comes from the BigDecimal computation that produced these
//   values, not from storing them in a higher-precision type.
//
// WHAT does `length` mean?
//   The number of iterations actually stored.  If the reference center escapes
//   the Mandelbrot set before hitting maxIterations, we stop early and length
//   reflects that.  Pixels whose perturbation iteration runs past `length` without
//   escaping are flagged as glitches (they need a different reference point or a
//   full BigDecimal recompute).
// =============================================================================
public class ReferenceOrbit {

    // X_n values, real and imaginary parts, indexed by iteration number n.
    public final double[] re;
    public final double[] im;

    // How many entries are valid in re[] and im[].
    public final int length;

    public ReferenceOrbit(double[] re, double[] im, int length) {
        this.re     = re;
        this.im     = im;
        this.length = length;
    }
}
