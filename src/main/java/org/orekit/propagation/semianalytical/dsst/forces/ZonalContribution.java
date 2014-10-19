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
package org.orekit.propagation.semianalytical.dsst.forces;

import java.util.TreeMap;

import org.apache.commons.math3.util.FastMath;
import org.orekit.errors.OrekitException;
import org.orekit.forces.gravity.potential.UnnormalizedSphericalHarmonicsProvider;
import org.orekit.forces.gravity.potential.UnnormalizedSphericalHarmonicsProvider.UnnormalizedSphericalHarmonics;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.semianalytical.dsst.utilities.AuxiliaryElements;
import org.orekit.propagation.semianalytical.dsst.utilities.CoefficientsFactory;
import org.orekit.propagation.semianalytical.dsst.utilities.CoefficientsFactory.NSKey;
import org.orekit.propagation.semianalytical.dsst.utilities.UpperBounds;
import org.orekit.propagation.semianalytical.dsst.utilities.hansen.HansenZonalLinear;
import org.orekit.time.AbsoluteDate;

/** Zonal contribution to the {@link DSSTCentralBody central body gravitational perturbation}.
 *
 *   @author Romain Di Costanzo
 *   @author Pascal Parraud
 */
class ZonalContribution implements DSSTForceModel {

    /** Truncation tolerance. */
    private static final double TRUNCATION_TOLERANCE = 1e-4;

    /** Provider for spherical harmonics. */
    private final UnnormalizedSphericalHarmonicsProvider provider;

    /** Maximal degree to consider for harmonics potential. */
    private final int maxDegree;

    /** Maximal degree to consider for harmonics potential. */
    private final int maxOrder;

    /** Factorial. */
    private final double[] fact;

    /** Coefficient used to define the mean disturbing function V<sub>ns</sub> coefficient. */
    private final TreeMap<NSKey, Double> Vns;

    /** Highest power of the eccentricity to be used in series expansion. */
    private int maxEccPow;

    // Equinoctial elements (according to DSST notation)
    /** a. */
    private double a;
    /** ex. */
    private double k;
    /** ey. */
    private double h;
    /** hx. */
    private double q;
    /** hy. */
    private double p;
    /** Retrograde factor. */
    private int    I;

    /** Eccentricity. */
    private double ecc;

    /** Direction cosine &alpha. */
    private double alpha;
    /** Direction cosine &beta. */
    private double beta;
    /** Direction cosine &gamma. */
    private double gamma;

    // Common factors for potential computation
    /** &Chi; = 1 / sqrt(1 - e<sup>2</sup>) = 1 / B. */
    private double X;
    /** &Chi;<sup>2</sup>. */
    private double XX;
    /** &Chi;<sup>3</sup>. */
    private double XXX;
    /** 1 / (A * B) .*/
    private double ooAB;
    /** B / A .*/
    private double BoA;
    /** B / A(1 + B) .*/
    private double BoABpo;
    /** -C / (2 * A * B) .*/
    private double mCo2AB;
    /** -2 * a / A .*/
    private double m2aoA;
    /** &mu; / a .*/
    private double muoa;

    /** An array that contains the objects needed to build the Hansen coefficients. <br/>
     * The index is s*/
    private HansenZonalLinear[] hansenObjects;

    /** Simple constructor.
     * @param provider provider for spherical harmonics
     */
    public ZonalContribution(final UnnormalizedSphericalHarmonicsProvider provider) {

        this.provider  = provider;
        this.maxDegree = provider.getMaxDegree();
        this.maxOrder  = provider.getMaxOrder();

        // Vns coefficients
        this.Vns = CoefficientsFactory.computeVns(maxDegree + 1);

        // Factorials computation
        final int maxFact = 2 * maxDegree + 1;
        this.fact = new double[maxFact];
        fact[0] = 1.;
        for (int i = 1; i < maxFact; i++) {
            fact[i] = i * fact[i - 1];
        }

        // Initialize default values
        this.maxEccPow = (maxDegree == 2) ? 0 : Integer.MIN_VALUE;

    }

    /** Get the spherical harmonics provider.
     *  @return the spherical harmonics provider
     */
    public UnnormalizedSphericalHarmonicsProvider getProvider() {
        return provider;
    }

    /** Computes the highest power of the eccentricity to appear in the truncated
     *  analytical power series expansion.
     *  <p>
     *  This method computes the upper value for the central body potential and
     *  determines the maximal power for the eccentricity producing potential
     *  terms bigger than a defined tolerance.
     *  </p>
     *  @param aux auxiliary elements related to the current orbit
     *  @throws OrekitException if some specific error occurs
     */
    public void initialize(final AuxiliaryElements aux)
        throws OrekitException {

        if (maxDegree == 2) {
            maxEccPow = 0;
        } else {
            // Initializes specific parameters.
            initializeStep(aux);
            final UnnormalizedSphericalHarmonics harmonics = provider.onDate(aux.getDate());

            // Utilities for truncation
            final double ax2or = 2. * a / provider.getAe();
            double xmuran = provider.getMu() / a;
            // Set a lower bound for eccentricity
            final double eo2  = FastMath.max(0.0025, ecc / 2.);
            final double x2o2 = XX / 2.;
            final double[] eccPwr = new double[maxDegree + 1];
            final double[] chiPwr = new double[maxDegree + 1];
            final double[] hafPwr = new double[maxDegree + 1];
            eccPwr[0] = 1.;
            chiPwr[0] = X;
            hafPwr[0] = 1.;
            for (int i = 1; i <= maxDegree; i++) {
                eccPwr[i] = eccPwr[i - 1] * eo2;
                chiPwr[i] = chiPwr[i - 1] * x2o2;
                hafPwr[i] = hafPwr[i - 1] * 0.5;
                xmuran  /= ax2or;
            }

            // Set highest power of e and degree of current spherical harmonic.
            maxEccPow = 0;
            int n = maxDegree;
            // Loop over n
            do {
                // Set order of current spherical harmonic.
                int m = 0;
                // Loop over m
                do {
                    // Compute magnitude of current spherical harmonic coefficient.
                    final double cnm = harmonics.getUnnormalizedCnm(n, m);
                    final double snm = harmonics.getUnnormalizedSnm(n, m);
                    final double csnm = FastMath.hypot(cnm, snm);
                    if (csnm == 0.) break;
                    // Set magnitude of last spherical harmonic term.
                    double lastTerm = 0.;
                    // Set current power of e and related indices.
                    int nsld2 = (n - maxEccPow - 1) / 2;
                    int l = n - 2 * nsld2;
                    // Loop over l
                    double term = 0.;
                    do {
                        // Compute magnitude of current spherical harmonic term.
                        if (m < l) {
                            term = csnm * xmuran *
                                    (fact[n - l] / (fact[n - m])) *
                                    (fact[n + l] / (fact[nsld2] * fact[nsld2 + l])) *
                                    eccPwr[l] * UpperBounds.getDnl(XX, chiPwr[l], n, l) *
                                    (UpperBounds.getRnml(gamma, n, l, m, 1, I) + UpperBounds.getRnml(gamma, n, l, m, -1, I));
                        } else {
                            term = csnm * xmuran *
                                    (fact[n + m] / (fact[nsld2] * fact[nsld2 + l])) *
                                    eccPwr[l] * hafPwr[m - l] * UpperBounds.getDnl(XX, chiPwr[l], n, l) *
                                    (UpperBounds.getRnml(gamma, n, m, l, 1, I) + UpperBounds.getRnml(gamma, n, m, l, -1, I));
                        }
                        // Is the current spherical harmonic term bigger than the truncation tolerance ?
                        if (term >= TRUNCATION_TOLERANCE) {
                            maxEccPow = l;
                        } else {
                            // Is the current term smaller than the last term ?
                            if (term < lastTerm) {
                                break;
                            }
                        }
                        // Proceed to next power of e.
                        lastTerm = term;
                        l += 2;
                        nsld2--;
                    } while (l < n);
                    // Is the current spherical harmonic term bigger than the truncation tolerance ?
                    if (term >= TRUNCATION_TOLERANCE) {
                        maxEccPow = FastMath.min(maxDegree - 2, maxEccPow);
                        //Initialise the HansenCoefficient generator
                        this.hansenObjects = new HansenZonalLinear[maxEccPow + 1];

                        for (int s = 0; s <= maxEccPow; s++) {
                            this.hansenObjects[s] = new HansenZonalLinear(maxDegree, s);
                        }
                        return;
                    }
                    // Proceed to next order.
                    m++;
                } while (m <= FastMath.min(n, maxOrder));
                // Procced to next degree.
                xmuran *= ax2or;
                n--;
            } while (n > maxEccPow + 2);

            maxEccPow = FastMath.min(maxDegree - 2, maxEccPow);
        }

        //Initialise the HansenCoefficient generator
        this.hansenObjects = new HansenZonalLinear[maxEccPow + 1];

        for (int s = 0; s <= maxEccPow; s++) {
            this.hansenObjects[s] = new HansenZonalLinear(maxDegree, s);
        }

    }

    /** {@inheritDoc} */
    public void initializeStep(final AuxiliaryElements aux) throws OrekitException {

        // Equinoctial elements
        a = aux.getSma();
        k = aux.getK();
        h = aux.getH();
        q = aux.getQ();
        p = aux.getP();

        // Retrograde factor
        I = aux.getRetrogradeFactor();

        // Eccentricity
        ecc = aux.getEcc();

        // Direction cosines
        alpha = aux.getAlpha();
        beta  = aux.getBeta();
        gamma = aux.getGamma();

        // Equinoctial coefficients
        final double AA = aux.getA();
        final double BB = aux.getB();
        final double CC = aux.getC();

        // &Chi; = 1 / B
        X = 1. / BB;
        XX = X * X;
        XXX = X * XX;
        // 1 / AB
        ooAB = 1. / (AA * BB);
        // B / A
        BoA = BB / AA;
        // -C / 2AB
        mCo2AB = -CC * ooAB / 2.;
        // B / A(1 + B)
        BoABpo = BoA / (1. + BB);
        // -2 * a / A
        m2aoA = -2 * a / AA;
        // &mu; / a
        muoa = provider.getMu() / a;

    }

    /** {@inheritDoc} */
    public double[] getMeanElementRate(final SpacecraftState spacecraftState) throws OrekitException {

        // Compute potential derivative
        final double[] dU  = computeUDerivatives(spacecraftState.getDate());
        final double dUda  = dU[0];
        final double dUdk  = dU[1];
        final double dUdh  = dU[2];
        final double dUdAl = dU[3];
        final double dUdBe = dU[4];
        final double dUdGa = dU[5];

        // Compute cross derivatives [Eq. 2.2-(8)]
        // U(alpha,gamma) = alpha * dU/dgamma - gamma * dU/dalpha
        final double UAlphaGamma   = alpha * dUdGa - gamma * dUdAl;
        // U(beta,gamma) = beta * dU/dgamma - gamma * dU/dbeta
        final double UBetaGamma    =  beta * dUdGa - gamma * dUdBe;
        // Common factor
        final double pUAGmIqUBGoAB = (p * UAlphaGamma - I * q * UBetaGamma) * ooAB;

        // Compute mean elements rates [Eq. 3.1-(1)]
        final double da =  0.;
        final double dh =  BoA * dUdk + k * pUAGmIqUBGoAB;
        final double dk = -BoA * dUdh - h * pUAGmIqUBGoAB;
        final double dp =  mCo2AB * UBetaGamma;
        final double dq =  mCo2AB * UAlphaGamma * I;
        final double dM =  m2aoA * dUda + BoABpo * (h * dUdh + k * dUdk) + pUAGmIqUBGoAB;

        return new double[] {da, dk, dh, dq, dp, dM};
    }

    /** {@inheritDoc} */
    public double[] getShortPeriodicVariations(final AbsoluteDate date,
                                               final double[] meanElements)
        throws OrekitException {
        // TODO: not implemented yet, Short Periodic Variations are set to null
        return new double[] {0., 0., 0., 0., 0., 0.};
    }

    /** {@inheritDoc} */
    public EventDetector[] getEventsDetectors() {
        return null;
    }

    /** Compute the derivatives of the gravitational potential U [Eq. 3.1-(6)].
     *  <p>
     *  The result is the array
     *  [dU/da, dU/dk, dU/dh, dU/d&alpha;, dU/d&beta;, dU/d&gamma;]
     *  </p>
     *  @param date current date
     *  @return potential derivatives
     *  @throws OrekitException if an error occurs in hansen computation
     */
    private double[] computeUDerivatives(final AbsoluteDate date) throws OrekitException {

        final UnnormalizedSphericalHarmonics harmonics = provider.onDate(date);

        // Gs and Hs coefficients
        final double[][] GsHs = CoefficientsFactory.computeGsHs(k, h, alpha, beta, maxEccPow);
        // Qns coefficients
        final double[][] Qns  = CoefficientsFactory.computeQns(gamma, maxDegree, maxEccPow);
        // r / a up to power degree
        final double roa = provider.getAe() / a;

        final double[] roaPow = new double[maxDegree + 1];
        roaPow[0] = 1.;
        for (int i = 1; i <= maxDegree; i++) {
            roaPow[i] = roa * roaPow[i - 1];
        }

        // Potential derivatives
        double dUda  = 0.;
        double dUdk  = 0.;
        double dUdh  = 0.;
        double dUdAl = 0.;
        double dUdBe = 0.;
        double dUdGa = 0.;

        for (int s = 0; s <= maxEccPow; s++) {
            //Initialise the Hansen roots
            this.hansenObjects[s].computeInitValues(X);

            // Get the current Gs coefficient
            final double gs = GsHs[0][s];

            // Compute Gs partial derivatives from 3.1-(9)
            double dGsdh  = 0.;
            double dGsdk  = 0.;
            double dGsdAl = 0.;
            double dGsdBe = 0.;
            if (s > 0) {
                // First get the G(s-1) and the H(s-1) coefficients
                final double sxgsm1 = s * GsHs[0][s - 1];
                final double sxhsm1 = s * GsHs[1][s - 1];
                // Then compute derivatives
                dGsdh  = beta  * sxgsm1 - alpha * sxhsm1;
                dGsdk  = alpha * sxgsm1 + beta  * sxhsm1;
                dGsdAl = k * sxgsm1 - h * sxhsm1;
                dGsdBe = h * sxgsm1 + k * sxhsm1;
            }

            // Kronecker symbol (2 - delta(0,s))
            final double d0s = (s == 0) ? 1 : 2;

            for (int n = s + 2; n <= maxDegree; n++) {
                // (n - s) must be even
                if ((n - s) % 2 == 0) {

                    //Extract data from previous computation :
                    final double kns   = this.hansenObjects[s].getValue(-n - 1, X);
                    final double dkns  = this.hansenObjects[s].getDerivative(-n - 1, X);

                    final double vns   = Vns.get(new NSKey(n, s));
                    final double coef0 = d0s * roaPow[n] * vns * -harmonics.getUnnormalizedCnm(n, 0);
                    final double coef1 = coef0 * Qns[n][s];
                    final double coef2 = coef1 * kns;
                    // dQns/dGamma = Q(n, s + 1) from Equation 3.1-(8)
                    final double dqns  = Qns[n][s + 1];

                    // Compute dU / da :
                    dUda += coef2 * (n + 1) * gs;
                    // Compute dU / dEx
                    dUdk += coef1 * (kns * dGsdk + k * XXX * gs * dkns);
                    // Compute dU / dEy
                    dUdh += coef1 * (kns * dGsdh + h * XXX * gs * dkns);
                    // Compute dU / dAlpha
                    dUdAl += coef2 * dGsdAl;
                    // Compute dU / dBeta
                    dUdBe += coef2 * dGsdBe;
                    // Compute dU / dGamma
                    dUdGa += coef0 * kns * dqns * gs;
                }
            }
        }

        return new double[] {
            dUda  *  muoa / a,
            dUdk  * -muoa,
            dUdh  * -muoa,
            dUdAl * -muoa,
            dUdBe * -muoa,
            dUdGa * -muoa
        };
    }
}
