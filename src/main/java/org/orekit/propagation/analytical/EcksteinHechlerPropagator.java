/* Copyright 2002-2014 CS Systèmes d'Information
 * Licensed to CS Systèmes d'Information (CS) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * CS licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orekit.propagation.analytical;

import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.MathUtils;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitMessages;
import org.orekit.errors.PropagationException;
import org.orekit.forces.gravity.potential.UnnormalizedSphericalHarmonicsProvider;
import org.orekit.forces.gravity.potential.UnnormalizedSphericalHarmonicsProvider.UnnormalizedSphericalHarmonics;
import org.orekit.orbits.CircularOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.OrbitType;
import org.orekit.orbits.PositionAngle;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

/** This class propagates a {@link org.orekit.propagation.SpacecraftState}
 *  using the analytical Eckstein-Hechler model.
 * <p>The Eckstein-Hechler model is suited for near circular orbits
 * (e < 0.1, with poor accuracy between 0.005 and 0.1) and inclination
 * neither equatorial (direct or retrograde) nor critical (direct or
 * retrograde).</p>
 * @see Orbit
 * @author Guylaine Prat
 */
public class EcksteinHechlerPropagator extends AbstractAnalyticalPropagator {

    /** Mean parameters at the initial date. */
    private CircularOrbit mean;

    /** Current mass. */
    private double mass;

    // CHECKSTYLE: stop JavadocVariable check

    // preprocessed values
    private double q;
    private double ql;
    private double g2;
    private double g3;
    private double g4;
    private double g5;
    private double g6;
    private double cosI1;
    private double sinI1;
    private double sinI2;
    private double sinI4;
    private double sinI6;

    // CHECKSTYLE: resume JavadocVariable check

    /** Reference radius of the central body attraction model (m). */
    private double referenceRadius;

    /** Central attraction coefficient (m<sup>3</sup>/s<sup>2</sup>). */
    private double mu;

    /** Un-normalized zonal coefficient (about -1.08e-3 for Earth). */
    private double c20;

    /** Un-normalized zonal coefficient (about +2.53e-6 for Earth). */
    private double c30;

    /** Un-normalized zonal coefficient (about +1.62e-6 for Earth). */
    private double c40;

    /** Un-normalized zonal coefficient (about +2.28e-7 for Earth). */
    private double c50;

    /** Un-normalized zonal coefficient (about -5.41e-7 for Earth). */
    private double c60;

    /** Build a propagator from orbit and potential provider.
     * <p>Mass and attitude provider are set to unspecified non-null arbitrary values.</p>
     * @param initialOrbit initial orbit
     * @param provider for un-normalized zonal coefficients
     * @exception OrekitException if the zonal coefficients cannot be retrieved
     * @exception PropagationException if the mean parameters cannot be computed
     */
    public EcksteinHechlerPropagator(final Orbit initialOrbit,
                                     final UnnormalizedSphericalHarmonicsProvider provider)
        throws PropagationException , OrekitException {
        this(initialOrbit, DEFAULT_LAW, DEFAULT_MASS, provider,
                provider.onDate(initialOrbit.getDate()));
    }

    /**
     * Private helper constructor.
     * @param initialOrbit initial orbit
     * @param attitude attitude provider
     * @param mass spacecraft mass
     * @param provider for un-normalized zonal coefficients
     * @param harmonics {@code provider.onDate(initialOrbit.getDate())}
     * @exception OrekitException if the zonal coefficients cannot be retrieved
     */
    public EcksteinHechlerPropagator(final Orbit initialOrbit,
                                     final AttitudeProvider attitude,
                                     final double mass,
                                     final UnnormalizedSphericalHarmonicsProvider provider,
                                     final UnnormalizedSphericalHarmonics harmonics)
        throws OrekitException
    {
        this(initialOrbit, attitude, mass, provider.getAe(), provider.getMu(),
                harmonics.getUnnormalizedCnm(2, 0),
                harmonics.getUnnormalizedCnm(3, 0),
                harmonics.getUnnormalizedCnm(4, 0),
                harmonics.getUnnormalizedCnm(5, 0),
                harmonics.getUnnormalizedCnm(6, 0));
    }

    /** Build a propagator from orbit and potential.
     * <p>Mass and attitude provider are set to unspecified non-null arbitrary values.</p>
     * <p>The C<sub>n,0</sub> coefficients are the denormalized zonal coefficients, they
     * are related to both the normalized coefficients
     * <span style="text-decoration: overline">C</span><sub>n,0</sub>
     *  and the J<sub>n</sub> one as follows:</p>
     * <pre>
     *   C<sub>n,0</sub> = [(2-&delta;<sub>0,m</sub>)(2n+1)(n-m)!/(n+m)!]<sup>&frac12;</sup><span style="text-decoration: overline">C</span><sub>n,0</sub>
     *   C<sub>n,0</sub> = -J<sub>n</sub>
     * </pre>
     * @param initialOrbit initial orbit
     * @param referenceRadius reference radius of the Earth for the potential model (m)
     * @param mu central attraction coefficient (m<sup>3</sup>/s<sup>2</sup>)
     * @param c20 un-normalized zonal coefficient (about -1.08e-3 for Earth)
     * @param c30 un-normalized zonal coefficient (about +2.53e-6 for Earth)
     * @param c40 un-normalized zonal coefficient (about +1.62e-6 for Earth)
     * @param c50 un-normalized zonal coefficient (about +2.28e-7 for Earth)
     * @param c60 un-normalized zonal coefficient (about -5.41e-7 for Earth)
     * @exception PropagationException if the mean parameters cannot be computed
     * @see org.orekit.utils.Constants
     */
    public EcksteinHechlerPropagator(final Orbit initialOrbit,
                                     final double referenceRadius, final double mu,
                                     final double c20, final double c30, final double c40,
                                     final double c50, final double c60)
        throws PropagationException {
        this(initialOrbit, DEFAULT_LAW, DEFAULT_MASS, referenceRadius, mu, c20, c30, c40, c50, c60);
    }

    /** Build a propagator from orbit, mass and potential provider.
     * <p>Attitude law is set to an unspecified non-null arbitrary value.</p>
     * @param initialOrbit initial orbit
     * @param mass spacecraft mass
     * @param provider for un-normalized zonal coefficients
     * @exception OrekitException if the zonal coefficients cannot be retrieved
     * @exception PropagationException if the mean parameters cannot be computed
     */
    public EcksteinHechlerPropagator(final Orbit initialOrbit, final double mass,
                                     final UnnormalizedSphericalHarmonicsProvider provider)
        throws PropagationException , OrekitException {
        this(initialOrbit, DEFAULT_LAW, mass, provider, provider.onDate(initialOrbit.getDate()));
    }

    /** Build a propagator from orbit, mass and potential.
     * <p>Attitude law is set to an unspecified non-null arbitrary value.</p>
     * <p>The C<sub>n,0</sub> coefficients are the denormalized zonal coefficients, they
     * are related to both the normalized coefficients
     * <span style="text-decoration: overline">C</span><sub>n,0</sub>
     *  and the J<sub>n</sub> one as follows:</p>
     * <pre>
     *   C<sub>n,0</sub> = [(2-&delta;<sub>0,m</sub>)(2n+1)(n-m)!/(n+m)!]<sup>&frac12;</sup><span style="text-decoration: overline">C</span><sub>n,0</sub>
     *   C<sub>n,0</sub> = -J<sub>n</sub>
     * </pre>
     * @param initialOrbit initial orbit
     * @param mass spacecraft mass
     * @param referenceRadius reference radius of the Earth for the potential model (m)
     * @param mu central attraction coefficient (m<sup>3</sup>/s<sup>2</sup>)
     * @param c20 un-normalized zonal coefficient (about -1.08e-3 for Earth)
     * @param c30 un-normalized zonal coefficient (about +2.53e-6 for Earth)
     * @param c40 un-normalized zonal coefficient (about +1.62e-6 for Earth)
     * @param c50 un-normalized zonal coefficient (about +2.28e-7 for Earth)
     * @param c60 un-normalized zonal coefficient (about -5.41e-7 for Earth)
     * @exception PropagationException if the mean parameters cannot be computed
     */
    public EcksteinHechlerPropagator(final Orbit initialOrbit, final double mass,
                                     final double referenceRadius, final double mu,
                                     final double c20, final double c30, final double c40,
                                     final double c50, final double c60)
        throws PropagationException {
        this(initialOrbit, DEFAULT_LAW, mass, referenceRadius, mu, c20, c30, c40, c50, c60);
    }

    /** Build a propagator from orbit, attitude provider and potential provider.
     * <p>Mass is set to an unspecified non-null arbitrary value.</p>
     * @param initialOrbit initial orbit
     * @param attitudeProv attitude provider
     * @param provider for un-normalized zonal coefficients
     * @exception OrekitException if the zonal coefficients cannot be retrieved
     * @exception PropagationException if the mean parameters cannot be computed
     */
    public EcksteinHechlerPropagator(final Orbit initialOrbit,
                                     final AttitudeProvider attitudeProv,
                                     final UnnormalizedSphericalHarmonicsProvider provider)
        throws PropagationException , OrekitException {
        this(initialOrbit, attitudeProv, DEFAULT_MASS, provider,
                provider.onDate(initialOrbit.getDate()));
    }

    /** Build a propagator from orbit, attitude provider and potential.
     * <p>Mass is set to an unspecified non-null arbitrary value.</p>
     * <p>The C<sub>n,0</sub> coefficients are the denormalized zonal coefficients, they
     * are related to both the normalized coefficients
     * <span style="text-decoration: overline">C</span><sub>n,0</sub>
     *  and the J<sub>n</sub> one as follows:</p>
     * <pre>
     *   C<sub>n,0</sub> = [(2-&delta;<sub>0,m</sub>)(2n+1)(n-m)!/(n+m)!]<sup>&frac12;</sup><span style="text-decoration: overline">C</span><sub>n,0</sub>
     *   C<sub>n,0</sub> = -J<sub>n</sub>
     * </pre>
     * @param initialOrbit initial orbit
     * @param attitudeProv attitude provider
     * @param referenceRadius reference radius of the Earth for the potential model (m)
     * @param mu central attraction coefficient (m<sup>3</sup>/s<sup>2</sup>)
     * @param c20 un-normalized zonal coefficient (about -1.08e-3 for Earth)
     * @param c30 un-normalized zonal coefficient (about +2.53e-6 for Earth)
     * @param c40 un-normalized zonal coefficient (about +1.62e-6 for Earth)
     * @param c50 un-normalized zonal coefficient (about +2.28e-7 for Earth)
     * @param c60 un-normalized zonal coefficient (about -5.41e-7 for Earth)
     * @exception PropagationException if the mean parameters cannot be computed
     */
    public EcksteinHechlerPropagator(final Orbit initialOrbit,
                                     final AttitudeProvider attitudeProv,
                                     final double referenceRadius, final double mu,
                                     final double c20, final double c30, final double c40,
                                     final double c50, final double c60)
        throws PropagationException {
        this(initialOrbit, attitudeProv, DEFAULT_MASS, referenceRadius, mu, c20, c30, c40, c50, c60);
    }

    /** Build a propagator from orbit, attitude provider, mass and potential provider.
     * @param initialOrbit initial orbit
     * @param attitudeProv attitude provider
     * @param mass spacecraft mass
     * @param provider for un-normalized zonal coefficients
     * @exception OrekitException if the zonal coefficients cannot be retrieved
     * @exception PropagationException if the mean parameters cannot be computed
     */
    public EcksteinHechlerPropagator(final Orbit initialOrbit,
                                     final AttitudeProvider attitudeProv,
                                     final double mass,
                                     final UnnormalizedSphericalHarmonicsProvider provider)
        throws PropagationException , OrekitException {
        this(initialOrbit, attitudeProv, mass, provider,
                provider.onDate(initialOrbit.getDate()));
    }

    /** Build a propagator from orbit, attitude provider, mass and potential.
     * <p>The C<sub>n,0</sub> coefficients are the denormalized zonal coefficients, they
     * are related to both the normalized coefficients
     * <span style="text-decoration: overline">C</span><sub>n,0</sub>
     *  and the J<sub>n</sub> one as follows:</p>
     * <pre>
     *   C<sub>n,0</sub> = [(2-&delta;<sub>0,m</sub>)(2n+1)(n-m)!/(n+m)!]<sup>&frac12;</sup><span style="text-decoration: overline">C</span><sub>n,0</sub>
     *   C<sub>n,0</sub> = -J<sub>n</sub>
     * </pre>
     * @param initialOrbit initial orbit
     * @param attitudeProv attitude provider
     * @param mass spacecraft mass
     * @param referenceRadius reference radius of the Earth for the potential model (m)
     * @param mu central attraction coefficient (m<sup>3</sup>/s<sup>2</sup>)
     * @param c20 un-normalized zonal coefficient (about -1.08e-3 for Earth)
     * @param c30 un-normalized zonal coefficient (about +2.53e-6 for Earth)
     * @param c40 un-normalized zonal coefficient (about +1.62e-6 for Earth)
     * @param c50 un-normalized zonal coefficient (about +2.28e-7 for Earth)
     * @param c60 un-normalized zonal coefficient (about -5.41e-7 for Earth)
     * @exception PropagationException if the mean parameters cannot be computed
     */
    public EcksteinHechlerPropagator(final Orbit initialOrbit,
                                     final AttitudeProvider attitudeProv,
                                     final double mass,
                                     final double referenceRadius, final double mu,
                                     final double c20, final double c30, final double c40,
                                     final double c50, final double c60)
        throws PropagationException {

        super(attitudeProv);
        this.mass = mass;

        try {

            // store model coefficients
            this.referenceRadius = referenceRadius;
            this.mu  = mu;
            this.c20 = c20;
            this.c30 = c30;
            this.c40 = c40;
            this.c50 = c50;
            this.c60 = c60;

            // compute mean parameters
            // transform into circular adapted parameters used by the Eckstein-Hechler model
            resetInitialState(new SpacecraftState(initialOrbit,
                                                  attitudeProv.getAttitude(initialOrbit,
                                                                           initialOrbit.getDate(),
                                                                           initialOrbit.getFrame()),
                                                  mass));

        } catch (OrekitException oe) {
            throw new PropagationException(oe);
        }
    }

    /** {@inheritDoc} */
    public void resetInitialState(final SpacecraftState state)
        throws PropagationException {
        super.resetInitialState(state);
        this.mass = state.getMass();
        computeMeanParameters((CircularOrbit) OrbitType.CIRCULAR.convertType(state.getOrbit()));
    }

    /** Compute mean parameters according to the Eckstein-Hechler analytical model.
     * @param osculating osculating orbit
     * @exception PropagationException if orbit goes outside of supported range
     * (trajectory inside the Brillouin sphere, too eccentric, equatorial, critical
     * inclination) or if convergence cannot be reached
     */
    private void computeMeanParameters(final CircularOrbit osculating)
        throws PropagationException {

        // sanity check
        if (osculating.getA() < referenceRadius) {
            throw new PropagationException(OrekitMessages.TRAJECTORY_INSIDE_BRILLOUIN_SPHERE,
                                           osculating.getA());
        }

        // rough initialization of the mean parameters
        mean = new CircularOrbit(osculating);

        // threshold for each parameter
        final double epsilon         = 1.0e-13;
        final double thresholdA      = epsilon * (1 + FastMath.abs(mean.getA()));
        final double thresholdE      = epsilon * (1 + mean.getE());
        final double thresholdAngles = epsilon * FastMath.PI;

        int i = 0;
        while (i++ < 100) {

            // preliminary processing
            q = referenceRadius / mean.getA();
            ql = q * q;
            g2 = c20 * ql;
            ql *= q;
            g3 = c30 * ql;
            ql *= q;
            g4 = c40 * ql;
            ql *= q;
            g5 = c50 * ql;
            ql *= q;
            g6 = c60 * ql;

            cosI1 = FastMath.cos(mean.getI());
            sinI1 = FastMath.sin(mean.getI());
            sinI2 = sinI1 * sinI1;
            sinI4 = sinI2 * sinI2;
            sinI6 = sinI2 * sinI4;

            if (sinI2 < 1.0e-10) {
                throw new PropagationException(OrekitMessages.ALMOST_EQUATORIAL_ORBIT,
                                               FastMath.toDegrees(mean.getI()));
            }

            if (FastMath.abs(sinI2 - 4.0 / 5.0) < 1.0e-3) {
                throw new PropagationException(OrekitMessages.ALMOST_CRITICALLY_INCLINED_ORBIT,
                                               FastMath.toDegrees(mean.getI()));
            }

            if (mean.getE() > 0.1) {
                // if 0.005 < e < 0.1 no error is triggered, but accuracy is poor
                throw new PropagationException(OrekitMessages.TOO_LARGE_ECCENTRICITY_FOR_PROPAGATION_MODEL,
                                               mean.getE());
            }

            // recompute the osculating parameters from the current mean parameters
            final CircularOrbit rebuilt = (CircularOrbit) propagateOrbit(mean.getDate());

            // adapted parameters residuals
            final double deltaA      = osculating.getA()  - rebuilt.getA();
            final double deltaEx     = osculating.getCircularEx() - rebuilt.getCircularEx();
            final double deltaEy     = osculating.getCircularEy() - rebuilt.getCircularEy();
            final double deltaI      = osculating.getI()  - rebuilt.getI();
            final double deltaRAAN   = MathUtils.normalizeAngle(osculating.getRightAscensionOfAscendingNode() -
                                                                rebuilt.getRightAscensionOfAscendingNode(),
                                                                0.0);
            final double deltaAlphaM = MathUtils.normalizeAngle(osculating.getAlphaM() - rebuilt.getAlphaM(), 0.0);

            // update mean parameters
            mean = new CircularOrbit(mean.getA()          + deltaA,
                                     mean.getCircularEx() + deltaEx,
                                     mean.getCircularEy() + deltaEy,
                                     mean.getI()          + deltaI,
                                     mean.getRightAscensionOfAscendingNode() + deltaRAAN,
                                     mean.getAlphaM()     + deltaAlphaM,
                                     PositionAngle.MEAN,
                                     mean.getFrame(),
                                     mean.getDate(), mean.getMu());

            // check convergence
            if ((FastMath.abs(deltaA)      < thresholdA) &&
                (FastMath.abs(deltaEx)     < thresholdE) &&
                (FastMath.abs(deltaEy)     < thresholdE) &&
                (FastMath.abs(deltaI)      < thresholdAngles) &&
                (FastMath.abs(deltaRAAN)   < thresholdAngles) &&
                (FastMath.abs(deltaAlphaM) < thresholdAngles)) {
                return;
            }

        }

        throw new PropagationException(OrekitMessages.UNABLE_TO_COMPUTE_ECKSTEIN_HECHLER_MEAN_PARAMETERS, i);

    }

    /** {@inheritDoc} */
    public Orbit propagateOrbit(final AbsoluteDate date)
        throws PropagationException {

        // keplerian evolution
        final double xnot = date.durationFrom(mean.getDate()) * FastMath.sqrt(mu / mean.getA()) / mean.getA();

        // secular effects

        // eccentricity
        final double rdpom = -0.75 * g2 * (4.0 - 5.0 * sinI2);
        final double rdpomp = 7.5 * g4 * (1.0 - 31.0 / 8.0 * sinI2 + 49.0 / 16.0 * sinI4) -
                              13.125 * g6 * (1.0 - 8.0 * sinI2 + 129.0 / 8.0 * sinI4 - 297.0 / 32.0 * sinI6);
        final double x = (rdpom + rdpomp) * xnot;
        final double cx = FastMath.cos(x);
        final double sx = FastMath.sin(x);
        q = 3.0 / (32.0 * rdpom);
        final double eps1 =
            q * g4 * sinI2 * (30.0 - 35.0 * sinI2) -
            175.0 * q * g6 * sinI2 * (1.0 - 3.0 * sinI2 + 2.0625 * sinI4);
        q = 3.0 * sinI1 / (8.0 * rdpom);
        final double eps2 =
            q * g3 * (4.0 - 5.0 * sinI2) - q * g5 * (10.0 - 35.0 * sinI2 + 26.25 * sinI4);
        final double exm = mean.getCircularEx() * cx - (1.0 - eps1) * mean.getCircularEy() * sx + eps2 * sx;
        final double eym = (1.0 + eps1) * mean.getCircularEx() * sx + (mean.getCircularEy() - eps2) * cx + eps2;

        // inclination
        final double xim = mean.getI();

        // right ascension of ascending node
        q = 1.50 * g2 - 2.25 * g2 * g2 * (2.5 - 19.0 / 6.0 * sinI2) +
            0.9375 * g4 * (7.0 * sinI2 - 4.0) +
            3.28125 * g6 * (2.0 - 9.0 * sinI2 + 8.25 * sinI4);
        final double omm =
            MathUtils.normalizeAngle(mean.getRightAscensionOfAscendingNode() + q * cosI1 * xnot, FastMath.PI);

        // latitude argument
        final double rdl = 1.0 - 1.50 * g2 * (3.0 - 4.0 * sinI2);
        q = rdl +
            2.25 * g2 * g2 * (9.0 - 263.0 / 12.0 * sinI2 + 341.0 / 24.0 * sinI4) +
            15.0 / 16.0 * g4 * (8.0 - 31.0 * sinI2 + 24.5 * sinI4) +
            105.0 / 32.0 * g6 * (-10.0 / 3.0 + 25.0 * sinI2 - 48.75 * sinI4 + 27.5 * sinI6);
        final double xlm = MathUtils.normalizeAngle(mean.getAlphaM() + q * xnot, FastMath.PI);

        // periodical terms
        final double cl1 = FastMath.cos(xlm);
        final double sl1 = FastMath.sin(xlm);
        final double cl2 = cl1 * cl1 - sl1 * sl1;
        final double sl2 = cl1 * sl1 + sl1 * cl1;
        final double cl3 = cl2 * cl1 - sl2 * sl1;
        final double sl3 = cl2 * sl1 + sl2 * cl1;
        final double cl4 = cl3 * cl1 - sl3 * sl1;
        final double sl4 = cl3 * sl1 + sl3 * cl1;
        final double cl5 = cl4 * cl1 - sl4 * sl1;
        final double sl5 = cl4 * sl1 + sl4 * cl1;
        final double cl6 = cl5 * cl1 - sl5 * sl1;

        final double qq = -1.5 * g2 / rdl;
        final double qh = 0.375 * (eym - eps2) / rdpom;
        ql = 0.375 * exm / (sinI1 * rdpom);

        // semi major axis
        double f = (2.0 - 3.5 * sinI2) * exm * cl1 +
                   (2.0 - 2.5 * sinI2) * eym * sl1 +
                   sinI2 * cl2 +
                   3.5 * sinI2 * (exm * cl3 + eym * sl3);
        double rda = qq * f;

        q = 0.75 * g2 * g2 * sinI2;
        f = 7.0 * (2.0 - 3.0 * sinI2) * cl2 + sinI2 * cl4;
        rda += q * f;

        q = -0.75 * g3 * sinI1;
        f = (4.0 - 5.0 * sinI2) * sl1 + 5.0 / 3.0 * sinI2 * sl3;
        rda += q * f;

        q = 0.25 * g4 * sinI2;
        f = (15.0 - 17.5 * sinI2) * cl2 + 4.375 * sinI2 * cl4;
        rda += q * f;

        q = 3.75 * g5 * sinI1;
        f = (2.625 * sinI4 - 3.5 * sinI2 + 1.0) * sl1 +
            7.0 / 6.0 * sinI2 * (1.0 - 1.125 * sinI2) * sl3 +
            21.0 / 80.0 * sinI4 * sl5;
        rda += q * f;

        q = 105.0 / 16.0 * g6 * sinI2;
        f = (3.0 * sinI2 - 1.0 - 33.0 / 16.0 * sinI4) * cl2 +
            0.75 * (1.1 * sinI4 - sinI2) * cl4 -
            11.0 / 80.0 * sinI4 * cl6;
        rda += q * f;

        // eccentricity
        f = (1.0 - 1.25 * sinI2) * cl1 +
            0.5 * (3.0 - 5.0 * sinI2) * exm * cl2 +
            (2.0 - 1.5 * sinI2) * eym * sl2 +
            7.0 / 12.0 * sinI2 * cl3 +
            17.0 / 8.0 * sinI2 * (exm * cl4 + eym * sl4);
        final double rdex = qq * f;

        f = (1.0 - 1.75 * sinI2) * sl1 +
            (1.0 - 3.0 * sinI2) * exm * sl2 +
            (2.0 * sinI2 - 1.5) * eym * cl2 +
            7.0 / 12.0 * sinI2 * sl3 +
            17.0 / 8.0 * sinI2 * (exm * sl4 - eym * cl4);
        final double rdey = qq * f;

        // ascending node
        q = -qq * cosI1;
        f = 3.5 * exm * sl1 -
            2.5 * eym * cl1 -
            0.5 * sl2 +
            7.0 / 6.0 * (eym * cl3 - exm * sl3);
        double rdom = q * f;

        f = g3 * cosI1 * (4.0 - 15.0 * sinI2);
        rdom += ql * f;

        f = 2.5 * g5 * cosI1 * (4.0 - 42.0 * sinI2 + 52.5 * sinI4);
        rdom -= ql * f;

        // inclination
        q = 0.5 * qq * sinI1 * cosI1;
        f = eym * sl1 - exm * cl1 + cl2 + 7.0 / 3.0 * (exm * cl3 + eym * sl3);
        double rdxi = q * f;

        f = g3 * cosI1 * (4.0 - 5.0 * sinI2);
        rdxi -= qh * f;

        f = 2.5 * g5 * cosI1 * (4.0 - 14.0 * sinI2 + 10.5 * sinI4);
        rdxi += qh * f;

        // latitude argument
        f = (7.0 - 77.0 / 8.0 * sinI2) * exm * sl1 +
            (55.0 / 8.0 * sinI2 - 7.50) * eym * cl1 +
            (1.25 * sinI2 - 0.5) * sl2 +
            (77.0 / 24.0 * sinI2 - 7.0 / 6.0) * (exm * sl3 - eym * cl3);
        double rdxl = qq * f;

        f = g3 * (53.0 * sinI2 - 4.0 - 57.5 * sinI4);
        rdxl += ql * f;

        f = 2.5 * g5 * (4.0 - 96.0 * sinI2 + 269.5 * sinI4 - 183.75 * sinI6);
        rdxl += ql * f;

        // osculating parameters
        return new CircularOrbit(mean.getA() * (1.0 + rda), exm + rdex, eym + rdey,
                                 xim + rdxi, MathUtils.normalizeAngle(omm + rdom, FastMath.PI),
                                 MathUtils.normalizeAngle(xlm + rdxl, FastMath.PI),
                                 PositionAngle.MEAN,
                                 mean.getFrame(), date, mean.getMu());

    }

    /** {@inheritDoc} */
    protected double getMass(final AbsoluteDate date) {
        return mass;
    }

}
