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

import org.orekit.errors.OrekitException;
import org.orekit.forces.gravity.potential.UnnormalizedSphericalHarmonicsProvider;
import org.orekit.frames.Frame;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.semianalytical.dsst.utilities.AuxiliaryElements;
import org.orekit.time.AbsoluteDate;

/** Central body gravitational contribution to the
 *  {@link org.orekit.propagation.semianalytical.dsst.DSSTPropagator DSSTPropagator}.
 *  <p>
 *  Central body gravitational contribution is made of:
 *  <ol>
 *  <li>zonal harmonics contribution</li>
 *  <li>tesseral harmonics contribution</li>
 *  </ol>
 *  </p>
 *
 *   @author Pascal Parraud
 */
public class DSSTCentralBody implements DSSTForceModel {

    /** Zonal harmonics contribution. */
    private final ZonalContribution    zonal;

    /** Tesseral harmonics contribution. */
    private final TesseralContribution tesseral;

    /** DSST Central body constructor.
     * @param centralBodyFrame rotating body frame
     * @param centralBodyRotationRate central body rotation rate (rad/s)
     * @param provider provider for spherical harmonics
     */
    public DSSTCentralBody(final Frame centralBodyFrame,
                           final double centralBodyRotationRate,
                           final UnnormalizedSphericalHarmonicsProvider provider) {

        // Zonal harmonics contribution
        this.zonal = new ZonalContribution(provider);

        // Tesseral harmonics contribution (only if order > 0)
        this.tesseral = (provider.getMaxOrder() == 0) ?
                        null : new TesseralContribution(centralBodyFrame,
                                                        centralBodyRotationRate,
                                                        provider);

    }

    /** {@inheritDoc} */
    public void initialize(final AuxiliaryElements aux)
        throws OrekitException {

        // Initialize zonal contribution
        zonal.initialize(aux);

        // Initialize tesseral contribution if needed
        if (tesseral != null) {
            tesseral.initialize(aux);
        }
    }

    /** {@inheritDoc} */
    public void initializeStep(final AuxiliaryElements aux)
        throws OrekitException {

        // Initialize zonal contribution
        zonal.initializeStep(aux);

        // Initialize tesseral contribution if needed
        if (tesseral != null) {
            tesseral.initializeStep(aux);
        }
    }

    /** {@inheritDoc} */
    public double[] getMeanElementRate(final SpacecraftState spacecraftState)
        throws OrekitException {

        // Get zonal harmonics contribution to mean elements
        final double[] meanElementRate = zonal.getMeanElementRate(spacecraftState);

        // Get tesseral resonant harmonics contribution to mean elements
        if (tesseral != null) {
            final double[] tesseralMeanRate = tesseral.getMeanElementRate(spacecraftState);
            for (int i = 0; i < 6; i++) {
                meanElementRate[i] += tesseralMeanRate[i];
            }
        }

        return meanElementRate;
    }

    /** {@inheritDoc} */
    public double[] getShortPeriodicVariations(final AbsoluteDate date, final double[] meanElements)
        throws OrekitException {

        // Get zonal harmonics contribution to short periodic variations
        final double[] shortPeriodics = zonal.getShortPeriodicVariations(date, meanElements);

        // Get tesseral resonant harmonics contribution to short periodic variations
        if (tesseral != null) {
            final double[] tesseralShort = tesseral.getShortPeriodicVariations(date, meanElements);
            for (int i = 0; i < 6; i++) {
                shortPeriodics[i] += tesseralShort[i];
            }
        }

        return shortPeriodics;
    }

    /** {@inheritDoc} */
    public EventDetector[] getEventsDetectors() {
        return null;
    }

    /** Get the spherical harmonics provider.
     *  @return the spherical harmonics provider
     */
    public UnnormalizedSphericalHarmonicsProvider getProvider() {
        return zonal.getProvider();
    }

}
