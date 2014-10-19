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


import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.util.FastMath;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.orekit.Utils;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitMessages;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.EquinoctialOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.PositionAngle;
import org.orekit.propagation.AdditionalStateProvider;
import org.orekit.propagation.Propagator;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateComponents;
import org.orekit.time.TimeComponents;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.PVCoordinates;


public class TabulatedEphemerisTest {

    @Test
    public void testInterpolation() throws ParseException, OrekitException {

        double mass = 2500;
        double a = 7187990.1979844316;
        double e = 0.5e-4;
        double i = 1.7105407051081795;
        double omega = 1.9674147913622104;
        double OMEGA = FastMath.toRadians(261);
        double lv = 0;

        final AbsoluteDate initDate = new AbsoluteDate(new DateComponents(2004, 01, 01),
                                                       TimeComponents.H00,
                                                       TimeScalesFactory.getUTC());
        final AbsoluteDate finalDate = new AbsoluteDate(new DateComponents(2004, 01, 02),
                                                        TimeComponents.H00,
                                                        TimeScalesFactory.getUTC());
        double deltaT = finalDate.durationFrom(initDate);

        Orbit transPar = new KeplerianOrbit(a, e, i, omega, OMEGA, lv, PositionAngle.TRUE,
                                            FramesFactory.getEME2000(), initDate, mu);

        int nbIntervals = 720;
        EcksteinHechlerPropagator eck =
            new EcksteinHechlerPropagator(transPar, mass,
                                          ae, mu, c20, c30, c40, c50, c60);
        AdditionalStateProvider provider = new AdditionalStateProvider() {
            
            public String getName() {
                return "dt";
            }
            
           public double[] getAdditionalState(SpacecraftState state) {
                return new double[] { state.getDate().durationFrom(initDate) };
            }
        };
        eck.addAdditionalStateProvider(provider);
        try {
            eck.addAdditionalStateProvider(provider);
            Assert.fail("an exception should have been thrown");
        } catch (OrekitException oe) {
            Assert.assertEquals(OrekitMessages.ADDITIONAL_STATE_NAME_ALREADY_IN_USE,
                                oe.getSpecifier());
        }
        List<SpacecraftState> tab = new ArrayList<SpacecraftState>(nbIntervals + 1);
        for (int j = 0; j<= nbIntervals; j++) {
            AbsoluteDate current = initDate.shiftedBy((j * deltaT) / nbIntervals);
            tab.add(eck.propagate(current));
        }

        try {
            new Ephemeris(tab, nbIntervals + 2);
            Assert.fail("an exception should have been thrown");
        } catch (MathIllegalArgumentException miae) {
            // expected
        }
        Ephemeris te = new Ephemeris(tab, 2);

        Assert.assertEquals(0.0, te.getMaxDate().durationFrom(finalDate), 1.0e-9);
        Assert.assertEquals(0.0, te.getMinDate().durationFrom(initDate), 1.0e-9);

        checkEphemerides(eck, te, initDate.shiftedBy(3600),  1.0e-9, true);
        checkEphemerides(eck, te, initDate.shiftedBy(3660), 30, false);
        checkEphemerides(eck, te, initDate.shiftedBy(3720),  1.0e-9, true);

    }

    @Test
    public void testPiWraping() throws OrekitException {

        TimeScale utc= TimeScalesFactory.getUTC();
        Frame frame = FramesFactory.getEME2000();
        double mu=CelestialBodyFactory.getEarth().getGM();
        AbsoluteDate t0 = new AbsoluteDate(2009, 10, 29, 0, 0, 0, utc);

        AbsoluteDate t1 = new AbsoluteDate(t0, 1320.0);
        Vector3D p1 = new Vector3D(-0.17831296727974E+08,  0.67919502669856E+06, -0.16591008368477E+07);
        Vector3D v1 = new Vector3D(-0.38699705630724E+04, -0.36209408682762E+04, -0.16255053872347E+03);
        SpacecraftState s1 = new SpacecraftState(new EquinoctialOrbit(new PVCoordinates(p1, v1), frame, t1, mu));

        AbsoluteDate t2 = new AbsoluteDate(t0, 1440.0);
        Vector3D p2 = new Vector3D(-0.18286942572033E+08,  0.24442124296930E+06, -0.16777961761695E+07);
        Vector3D v2 = new Vector3D(-0.37252897467918E+04, -0.36246628128896E+04, -0.14917724596280E+03);
        SpacecraftState s2 = new SpacecraftState(new EquinoctialOrbit(new PVCoordinates(p2, v2), frame, t2, mu));

        AbsoluteDate t3 = new AbsoluteDate(t0, 1560.0);
        Vector3D p3 = new Vector3D(-0.18725635245837E+08, -0.19058407701834E+06, -0.16949352249614E+07);
        Vector3D v3 = new Vector3D(-0.35873348682393E+04, -0.36248828501784E+04, -0.13660045394149E+03);
        SpacecraftState s3 = new SpacecraftState(new EquinoctialOrbit(new PVCoordinates(p3, v3), frame, t3, mu));

        Ephemeris ephem= new Ephemeris(Arrays.asList(s1, s2, s3), 2);

        AbsoluteDate tA = new AbsoluteDate(t0, 24 * 60);
        Vector3D pA = ephem.propagate(tA).getPVCoordinates(frame).getPosition();
        Assert.assertEquals(1.766,
                            Vector3D.distance(pA, s1.shiftedBy(tA.durationFrom(s1.getDate())).getPVCoordinates(frame).getPosition()),
                            1.0e-3);
        Assert.assertEquals(0.000,
                            Vector3D.distance(pA, s2.shiftedBy(tA.durationFrom(s2.getDate())).getPVCoordinates(frame).getPosition()),
                            1.0e-3);
        Assert.assertEquals(1.556,
                            Vector3D.distance(pA, s3.shiftedBy(tA.durationFrom(s3.getDate())).getPVCoordinates(frame).getPosition()),
                            1.0e-3);

        AbsoluteDate tB = new AbsoluteDate(t0, 25 * 60);
        Vector3D pB = ephem.propagate(tB).getPVCoordinates(frame).getPosition();
        Assert.assertEquals(2.646,
                            Vector3D.distance(pB, s1.shiftedBy(tB.durationFrom(s1.getDate())).getPVCoordinates(frame).getPosition()),
                            1.0e-3);
        Assert.assertEquals(2.619,
                            Vector3D.distance(pB, s2.shiftedBy(tB.durationFrom(s2.getDate())).getPVCoordinates(frame).getPosition()),
                            1.0e-3);
        Assert.assertEquals(2.632,
                            Vector3D.distance(pB, s3.shiftedBy(tB.durationFrom(s3.getDate())).getPVCoordinates(frame).getPosition()),
                            1.0e-3);

        AbsoluteDate tC = new AbsoluteDate(t0, 26 * 60);
        Vector3D pC = ephem.propagate(tC).getPVCoordinates(frame).getPosition();
        Assert.assertEquals(6.851,
                            Vector3D.distance(pC, s1.shiftedBy(tC.durationFrom(s1.getDate())).getPVCoordinates(frame).getPosition()),
                            1.0e-3);
        Assert.assertEquals(1.605,
                            Vector3D.distance(pC, s2.shiftedBy(tC.durationFrom(s2.getDate())).getPVCoordinates(frame).getPosition()),
                            1.0e-3);
        Assert.assertEquals(0.000,
                            Vector3D.distance(pC, s3.shiftedBy(tC.durationFrom(s3.getDate())).getPVCoordinates(frame).getPosition()),
                            1.0e-3);

    }


    @Test
    public void testGetFrame() throws MathIllegalArgumentException, IllegalArgumentException, OrekitException {
        // setup
        Frame frame = FramesFactory.getICRF();
        AbsoluteDate date = AbsoluteDate.JULIAN_EPOCH;
        // create ephemeris with 2 arbitrary points
        SpacecraftState state = new SpacecraftState(
                new KeplerianOrbit(1e9, 0.01, 1, 1, 1, 1, PositionAngle.TRUE, frame, date, mu));
        Ephemeris ephem = new Ephemeris(Arrays.asList(state, state.shiftedBy(1)), 2);

        // action + verify
        Assert.assertSame(ephem.getFrame(), frame);
    }

    private void checkEphemerides(Propagator eph1, Propagator eph2, AbsoluteDate date,
                                  double threshold, boolean expectedBelow)
        throws OrekitException {

        Assert.assertEquals(eph1.getManagedAdditionalStates().length, eph2.getManagedAdditionalStates().length);
        for (String name : eph1.getManagedAdditionalStates()) {
            Assert.assertTrue(eph2.isAdditionalStateManaged(name));
        }
        SpacecraftState state1 = eph1.propagate(date);
        SpacecraftState state2 = eph2.propagate(date);
        double maxError = FastMath.abs(state1.getA() - state2.getA());
        maxError = FastMath.max(maxError, FastMath.abs(state1.getEquinoctialEx() - state2.getEquinoctialEx()));
        maxError = FastMath.max(maxError, FastMath.abs(state1.getEquinoctialEy() - state2.getEquinoctialEy()));
        maxError = FastMath.max(maxError, FastMath.abs(state1.getHx() - state2.getHx()));
        maxError = FastMath.max(maxError, FastMath.abs(state1.getHy() - state2.getHy()));
        maxError = FastMath.max(maxError, FastMath.abs(state1.getLv() - state2.getLv()));
        for (String name : eph1.getManagedAdditionalStates()) {
            double[] add1 = state1.getAdditionalState(name);
            double[] add2 = state2.getAdditionalState(name);
            Assert.assertEquals(add1.length, add2.length);
            for (int i = 0; i < add1.length; ++i) {
                maxError = FastMath.max(maxError, FastMath.abs(add1[i] - add2[i]));
            }
            Assert.assertTrue(eph2.isAdditionalStateManaged(name));
        }
        if (expectedBelow) {
            Assert.assertTrue(maxError <= threshold);
        } else {
            Assert.assertTrue(maxError >= threshold);
        }
    }

    @Before
    public void setUp() {
        Utils.setDataRoot("regular-data");
        mu  = 3.9860047e14;
        ae  = 6.378137e6;
        c20 = -1.08263e-3;
        c30 = 2.54e-6;
        c40 = 1.62e-6;
        c50 = 2.3e-7;
        c60 = -5.5e-7;
    }

    @After
    public void tearDown() {
        mu  = Double.NaN;
        ae  = Double.NaN;
        c20 = Double.NaN;
        c30 = Double.NaN;
        c40 = Double.NaN;
        c50 = Double.NaN;
        c60 = Double.NaN;
    }

    private double mu;
    private double ae;
    private double c20;
    private double c30;
    private double c40;
    private double c50;
    private double c60;

}
