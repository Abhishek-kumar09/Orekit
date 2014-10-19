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
package org.orekit.bodies;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.commons.math3.geometry.euclidean.oned.Vector1D;
import org.apache.commons.math3.geometry.euclidean.threed.Line;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.MathUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.orekit.Utils;
import org.orekit.errors.OrekitException;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.CircularOrbit;
import org.orekit.orbits.PositionAngle;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateComponents;
import org.orekit.time.TimeComponents;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;


public class OneAxisEllipsoidTest {

    @Test
    public void testOrigin() throws OrekitException {
        double ae = 6378137.0;
        checkCartesianToEllipsoidic(ae, 1.0 / 298.257222101,
                                    ae, 0, 0,
                                    0, 0, 0);
    }

    @Test
    public void testStandard() throws OrekitException {
        checkCartesianToEllipsoidic(6378137.0, 1.0 / 298.257222101,
                                    4637885.347, 121344.608, 4362452.869,
                                    0.026157811533131, 0.757987116290729, 260.455572965555);
    }

    @Test
    public void testLongitudeZero() throws OrekitException {
        checkCartesianToEllipsoidic(6378137.0, 1.0 / 298.257222101,
                                    6378400.0, 0, 6379000.0,
                                    0.0, 0.787815771252351, 2653416.77864152);
    }

    @Test
    public void testLongitudePi() throws OrekitException {
        checkCartesianToEllipsoidic(6378137.0, 1.0 / 298.257222101,
                                    -6379999.0, 0, 6379000.0,
                                    3.14159265358979, 0.787690146758403, 2654544.7767725);
    }

    @Test
    public void testNorthPole() throws OrekitException {
        checkCartesianToEllipsoidic(6378137.0, 1.0 / 298.257222101,
                                    0.0, 0.0, 7000000.0,
                                    0.0, 1.57079632679490, 643247.685859644);
    }

    @Test
    public void testEquator() throws OrekitException {
        checkCartesianToEllipsoidic(6378137.0, 1.0 / 298.257222101,
                                    6379888.0, 6377000.0, 0.0,
                                    0.785171775899913, 0.0, 2642345.24279301);
    }

    @Test
    public void testNoFlattening() throws OrekitException {
        final double r      = 7000000.0;
        final double lambda = 2.345;
        final double phi    = -1.23;
        final double cL = FastMath.cos(lambda);
        final double sL = FastMath.sin(lambda);
        final double cH = FastMath.cos(phi);
        final double sH = FastMath.sin(phi);
        checkCartesianToEllipsoidic(6378137.0, 0,
                                    r * cL * cH, r * sL * cH, r * sH,
                                    lambda, phi, r - 6378137.0);
    }

    @Test
    public void testOnSurface() throws OrekitException {
        Vector3D surfacePoint = new Vector3D(-1092200.775949484,
                                             -3944945.7282234835,
                                              4874931.946956173);
        OneAxisEllipsoid earthShape = new OneAxisEllipsoid(6378136.460, 1 / 298.257222101,
                                                           FramesFactory.getITRF(IERSConventions.IERS_2010, true));
        GeodeticPoint gp = earthShape.transform(surfacePoint, earthShape.getBodyFrame(),
                                                   AbsoluteDate.J2000_EPOCH);
        Vector3D rebuilt = earthShape.transform(gp);
        Assert.assertEquals(0, rebuilt.distance(surfacePoint), 3.0e-9);
    }

    @Test
    public void testInside3Roots() throws OrekitException {
        checkCartesianToEllipsoidic(6378137.0, 1.0 / 298.257,
                                    9219.0, -5322.0, 6056743.0,
                                    5.75963470503781, 1.56905114598949, -300000.009586231);
    }

    @Test
    public void testInsideLessThan3Roots() throws OrekitException {
        checkCartesianToEllipsoidic(6378137.0, 1.0 / 298.257,
                                    1366863.0, -789159.0, -5848.988,
                                    -0.523598928689, -0.00380885831963, -4799808.27951);
    }

    @Test
    public void testOutside() throws OrekitException {
        checkCartesianToEllipsoidic(6378137.0, 1.0 / 298.257,
                                    5722966.0, -3304156.0, -24621187.0,
                                    5.75958652642615, -1.3089969725151, 19134410.3342696);
    }

    @Test
    public void testGeoCar() throws OrekitException {
        OneAxisEllipsoid model =
            new OneAxisEllipsoid(6378137.0, 1.0 / 298.257222101,
                                 FramesFactory.getITRF(IERSConventions.IERS_2010, true));
        GeodeticPoint nsp =
            new GeodeticPoint(0.852479154923577, 0.0423149994747243, 111.6);
        Vector3D p = model.transform(nsp);
        Assert.assertEquals(4201866.69291890, p.getX(), 1.0e-6);
        Assert.assertEquals(177908.184625686, p.getY(), 1.0e-6);
        Assert.assertEquals(4779203.64408617, p.getZ(), 1.0e-6);
    }

    @Test
    public void testLineIntersection() throws OrekitException {
        AbsoluteDate date = AbsoluteDate.J2000_EPOCH;
        Frame frame = FramesFactory.getITRF(IERSConventions.IERS_2010, true);

        OneAxisEllipsoid model = new OneAxisEllipsoid(100.0, 0.9, frame);
        Vector3D point         = new Vector3D(0.0, 93.7139699, 3.5930796);
        Vector3D direction     = new Vector3D(0.0, 1.0, 1.0);
        Line line = new Line(point, point.add(direction), 1.0e-10);
        GeodeticPoint gp = model.getIntersectionPoint(line, point, frame, date);
        Assert.assertEquals(gp.getAltitude(), 0.0, 1.0e-12);
        Assert.assertTrue(line.contains(model.transform(gp)));

        model = new OneAxisEllipsoid(100.0, 0.9, frame);
        point = new Vector3D(0.0, -93.7139699, -3.5930796);
        direction = new Vector3D(0.0, -1.0, -1.0);
        line = new Line(point, point.add(direction), 1.0e-10).revert();
        gp = model.getIntersectionPoint(line, point, frame, date);
        Assert.assertTrue(line.contains(model.transform(gp)));

        model = new OneAxisEllipsoid(100.0, 0.9, frame);
        point = new Vector3D(0.0, -93.7139699, 3.5930796);
        direction = new Vector3D(0.0, -1.0, 1.0);
        line = new Line(point, point.add(direction), 1.0e-10);
        gp = model.getIntersectionPoint(line, point, frame, date);
        Assert.assertTrue(line.contains(model.transform(gp)));

        model = new OneAxisEllipsoid(100.0, 0.9, frame);
        point = new Vector3D(-93.7139699, 0.0, 3.5930796);
        direction = new Vector3D(-1.0, 0.0, 1.0);
        line = new Line(point, point.add(direction), 1.0e-10);
        gp = model.getIntersectionPoint(line, point, frame, date);
        Assert.assertTrue(line.contains(model.transform(gp)));
        Assert.assertFalse(line.contains(new Vector3D(0, 0, 7000000)));

        point = new Vector3D(0.0, 0.0, 110);
        direction = new Vector3D(0.0, 0.0, 1.0);
        line = new Line(point, point.add(direction), 1.0e-10);
        gp = model.getIntersectionPoint(line, point, frame, date);
        Assert.assertEquals(gp.getLatitude(), FastMath.PI/2, 1.0e-12);

        point = new Vector3D(0.0, 110, 0);
        direction = new Vector3D(0.0, 1.0, 0.0);
        line = new Line(point, point.add(direction), 1.0e-10);
        gp = model.getIntersectionPoint(line, point, frame, date);
        Assert.assertEquals(gp.getLatitude(),0, 1.0e-12);

    }

    @Test
    public void testNoLineIntersection() throws OrekitException {
        AbsoluteDate date = AbsoluteDate.J2000_EPOCH;
        Frame frame = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
        OneAxisEllipsoid model = new OneAxisEllipsoid(100.0, 0.9, frame);
        Vector3D point     = new Vector3D(0.0, 93.7139699, 3.5930796);
        Vector3D direction = new Vector3D(0.0, 9.0, -2.0);
        Line line = new Line(point, point.add(direction), 1.0e-10);
        Assert.assertNull(model.getIntersectionPoint(line, point, frame, date));
    }

    @Test
    public void testNegativeZ() throws OrekitException {
        AbsoluteDate date = AbsoluteDate.J2000_EPOCH;
        Frame frame = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
        OneAxisEllipsoid model = new OneAxisEllipsoid(90.0, 5.0 / 9.0, frame);
        Vector3D point     = new Vector3D(140.0, 0.0, -30.0);
        GeodeticPoint gp = model.transform(point, frame, date);
        Vector3D rebuilt = model.transform(gp);
        Assert.assertEquals(0.0, rebuilt.distance(point), 1.0e-10);
    }

    @Test
    public void testEquatorialInside() throws OrekitException {
        AbsoluteDate date = AbsoluteDate.J2000_EPOCH;
        Frame frame = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
        OneAxisEllipsoid model = new OneAxisEllipsoid(90.0, 5.0 / 9.0, frame);
        for (double rho = 0; rho < model.getEquatorialRadius(); rho += 0.01) {
            Vector3D point     = new Vector3D(rho, 0.0, 0.0);
            GeodeticPoint gp = model.transform(point, frame, date);
            Vector3D rebuilt = model.transform(gp);
            Assert.assertEquals(0.0, rebuilt.distance(point), 1.0e-10);
        }
    }

    @Test
    public void testFarPoint() throws OrekitException {
        AbsoluteDate date = AbsoluteDate.J2000_EPOCH;
        Frame frame = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
        OneAxisEllipsoid model = new OneAxisEllipsoid(90.0, 5.0 / 9.0, frame);
        Vector3D point     = new Vector3D(1.0e15, 2.0e15, -1.0e12);
        GeodeticPoint gp = model.transform(point, frame, date);
        Vector3D rebuilt = model.transform(gp);
        Assert.assertEquals(0.0, rebuilt.distance(point), 1.0e-15 * point.getNorm());
    }

    @Test
    public void testIssue141() throws OrekitException {
        AbsoluteDate date = new AbsoluteDate("2002-03-06T20:50:20.44188731559965033", TimeScalesFactory.getUTC());
        Frame frame = FramesFactory.getGTOD(IERSConventions.IERS_1996, true);
        OneAxisEllipsoid model = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                                                      Constants.WGS84_EARTH_FLATTENING,
                                                      frame);
        Vector3D point     = new Vector3D(-6838696.282102453, -2148321.403361013, -0.011907944179711194);
        GeodeticPoint gp = model.transform(point, frame, date);
        Vector3D rebuilt = model.transform(gp);
        Assert.assertEquals(0.0, rebuilt.distance(point), 1.0e-15 * point.getNorm());
    }

    @Test
    public void testSerialization() throws OrekitException, IOException, ClassNotFoundException {
        OneAxisEllipsoid original = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                                                         Constants.WGS84_EARTH_FLATTENING,
                                                         FramesFactory.getITRFEquinox(IERSConventions.IERS_1996, true));
        original.setAngularThreshold(1.0e-3);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream    oos = new ObjectOutputStream(bos);
        oos.writeObject(original);
        Assert.assertTrue(bos.size() > 250);
        Assert.assertTrue(bos.size() < 350);

        ByteArrayInputStream  bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream     ois = new ObjectInputStream(bis);
        OneAxisEllipsoid deserialized  = (OneAxisEllipsoid) ois.readObject();
        Assert.assertEquals(original.getEquatorialRadius(), deserialized.getEquatorialRadius(), 1.0e-12);
        Assert.assertEquals(original.getFlattening(), deserialized.getFlattening(), 1.0e-12);

    }

    @Test
    public void testIntersectionFromPoints() throws OrekitException {
        AbsoluteDate date = new AbsoluteDate(new DateComponents(2008, 03, 21),
                                             TimeComponents.H12,
                                             TimeScalesFactory.getUTC());

        Frame frame = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
        OneAxisEllipsoid earth = new OneAxisEllipsoid(6378136.460, 1 / 298.257222101, frame);

        // Satellite on polar position
        // ***************************
        final double mu = 3.9860047e14;
        CircularOrbit circ =
            new CircularOrbit(7178000.0, 0.5e-4, 0., FastMath.toRadians(90.), FastMath.toRadians(60.),
                                   FastMath.toRadians(90.), PositionAngle.MEAN,
                                   FramesFactory.getEME2000(), date, mu);

        // Transform satellite position to position/velocity parameters in EME2000 and ITRF200B
        PVCoordinates pvSatEME2000 = circ.getPVCoordinates();
        PVCoordinates pvSatItrf  = frame.getTransformTo(FramesFactory.getEME2000(), date).transformPVCoordinates(pvSatEME2000);
        Vector3D pSatItrf  = pvSatItrf.getPosition();

        // Test first visible surface points
        GeodeticPoint geoPoint = new GeodeticPoint(FastMath.toRadians(70.), FastMath.toRadians(60.), 0.);
        Vector3D pointItrf     = earth.transform(geoPoint);
        Line line = new Line(pSatItrf, pointItrf, 1.0e-10);
        GeodeticPoint geoInter = earth.getIntersectionPoint(line, pSatItrf, frame, date);
        Assert.assertEquals(geoPoint.getLongitude(), geoInter.getLongitude(), Utils.epsilonAngle);
        Assert.assertEquals(geoPoint.getLatitude(), geoInter.getLatitude(), Utils.epsilonAngle);

        // Test second visible surface points
        geoPoint = new GeodeticPoint(FastMath.toRadians(65.), FastMath.toRadians(-120.), 0.);
        pointItrf     = earth.transform(geoPoint);
        line = new Line(pSatItrf, pointItrf, 1.0e-10);
        geoInter = earth.getIntersectionPoint(line, pSatItrf, frame, date);
        Assert.assertEquals(geoPoint.getLongitude(), geoInter.getLongitude(), Utils.epsilonAngle);
        Assert.assertEquals(geoPoint.getLatitude(), geoInter.getLatitude(), Utils.epsilonAngle);

        // Test non visible surface points
        geoPoint = new GeodeticPoint(FastMath.toRadians(30.), FastMath.toRadians(60.), 0.);
        pointItrf     = earth.transform(geoPoint);
        line = new Line(pSatItrf, pointItrf, 1.0e-10);

        geoInter = earth.getIntersectionPoint(line, pSatItrf, frame, date);

        // For polar satellite position, intersection point is at the same longitude but different latitude
        Assert.assertEquals(1.04437199, geoInter.getLongitude(), Utils.epsilonAngle);
        Assert.assertEquals(1.36198012, geoInter.getLatitude(),  Utils.epsilonAngle);

        // Satellite on equatorial position
        // ********************************
        circ =
            new CircularOrbit(7178000.0, 0.5e-4, 0., FastMath.toRadians(1.e-4), FastMath.toRadians(0.),
                                   FastMath.toRadians(0.), PositionAngle.MEAN,
                                   FramesFactory.getEME2000(), date, mu);

        // Transform satellite position to position/velocity parameters in EME2000 and ITRF200B
        pvSatEME2000 = circ.getPVCoordinates();
        pvSatItrf  = frame.getTransformTo(FramesFactory.getEME2000(), date).transformPVCoordinates(pvSatEME2000);
        pSatItrf  = pvSatItrf.getPosition();

        // Test first visible surface points
        geoPoint = new GeodeticPoint(FastMath.toRadians(5.), FastMath.toRadians(0.), 0.);
        pointItrf     = earth.transform(geoPoint);
        line = new Line(pSatItrf, pointItrf, 1.0e-10);
        Assert.assertTrue(line.toSubSpace(pSatItrf).getX() < 0);
        geoInter = earth.getIntersectionPoint(line, pSatItrf, frame, date);
        Assert.assertEquals(geoPoint.getLongitude(), geoInter.getLongitude(), Utils.epsilonAngle);
        Assert.assertEquals(geoPoint.getLatitude(), geoInter.getLatitude(), Utils.epsilonAngle);

        // With the point opposite to satellite point along the line
        GeodeticPoint geoInter2 = earth.getIntersectionPoint(line, line.toSpace(new Vector1D(-line.toSubSpace(pSatItrf).getX())), frame, date);
        Assert.assertTrue(FastMath.abs(geoInter.getLongitude() - geoInter2.getLongitude()) > FastMath.toRadians(0.1));
        Assert.assertTrue(FastMath.abs(geoInter.getLatitude() - geoInter2.getLatitude()) > FastMath.toRadians(0.1));

        // Test second visible surface points
        geoPoint = new GeodeticPoint(FastMath.toRadians(-5.), FastMath.toRadians(0.), 0.);
        pointItrf     = earth.transform(geoPoint);
        line = new Line(pSatItrf, pointItrf, 1.0e-10);
        geoInter = earth.getIntersectionPoint(line, pSatItrf, frame, date);
        Assert.assertEquals(geoPoint.getLongitude(), geoInter.getLongitude(), Utils.epsilonAngle);
        Assert.assertEquals(geoPoint.getLatitude(), geoInter.getLatitude(), Utils.epsilonAngle);

        // Test non visible surface points
        geoPoint = new GeodeticPoint(FastMath.toRadians(40.), FastMath.toRadians(0.), 0.);
        pointItrf     = earth.transform(geoPoint);
        line = new Line(pSatItrf, pointItrf, 1.0e-10);
        geoInter = earth.getIntersectionPoint(line, pSatItrf, frame, date);
        Assert.assertEquals(-0.00768481, geoInter.getLongitude(), Utils.epsilonAngle);
        Assert.assertEquals( 0.32180410, geoInter.getLatitude(),  Utils.epsilonAngle);


        // Satellite on any position
        // *************************
        circ =
            new CircularOrbit(7178000.0, 0.5e-4, 0., FastMath.toRadians(50.), FastMath.toRadians(0.),
                                   FastMath.toRadians(90.), PositionAngle.MEAN,
                                   FramesFactory.getEME2000(), date, mu);

        // Transform satellite position to position/velocity parameters in EME2000 and ITRF200B
        pvSatEME2000 = circ.getPVCoordinates();
        pvSatItrf  = frame.getTransformTo(FramesFactory.getEME2000(), date).transformPVCoordinates(pvSatEME2000);
        pSatItrf  = pvSatItrf.getPosition();

        // Test first visible surface points
        geoPoint = new GeodeticPoint(FastMath.toRadians(40.), FastMath.toRadians(90.), 0.);
        pointItrf     = earth.transform(geoPoint);
        line = new Line(pSatItrf, pointItrf, 1.0e-10);
        geoInter = earth.getIntersectionPoint(line, pSatItrf, frame, date);
        Assert.assertEquals(geoPoint.getLongitude(), geoInter.getLongitude(), Utils.epsilonAngle);
        Assert.assertEquals(geoPoint.getLatitude(), geoInter.getLatitude(), Utils.epsilonAngle);

        // Test second visible surface points
        geoPoint = new GeodeticPoint(FastMath.toRadians(60.), FastMath.toRadians(90.), 0.);
        pointItrf     = earth.transform(geoPoint);
        line = new Line(pSatItrf, pointItrf, 1.0e-10);
        geoInter = earth.getIntersectionPoint(line, pSatItrf, frame, date);
        Assert.assertEquals(geoPoint.getLongitude(), geoInter.getLongitude(), Utils.epsilonAngle);
        Assert.assertEquals(geoPoint.getLatitude(), geoInter.getLatitude(), Utils.epsilonAngle);

        // Test non visible surface points
        geoPoint = new GeodeticPoint(FastMath.toRadians(0.), FastMath.toRadians(90.), 0.);
        pointItrf     = earth.transform(geoPoint);
        line = new Line(pSatItrf, pointItrf, 1.0e-10);
        geoInter = earth.getIntersectionPoint(line, pSatItrf, frame, date);
        Assert.assertEquals(FastMath.toRadians(89.5364061088196), geoInter.getLongitude(), Utils.epsilonAngle);
        Assert.assertEquals(FastMath.toRadians(35.555543683351125), geoInter.getLatitude(), Utils.epsilonAngle);

    }

    private void checkCartesianToEllipsoidic(double ae, double f,
                                             double x, double y, double z,
                                             double longitude, double latitude,
                                             double altitude)
        throws OrekitException {

        AbsoluteDate date = AbsoluteDate.J2000_EPOCH;
        Frame frame = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
        OneAxisEllipsoid model = new OneAxisEllipsoid(ae, f, frame);
        GeodeticPoint gp = model.transform(new Vector3D(x, y, z), frame, date);
        Assert.assertEquals(longitude, MathUtils.normalizeAngle(gp.getLongitude(), longitude), 1.0e-10);
        Assert.assertEquals(latitude,  gp.getLatitude(),  1.0e-10);
        Assert.assertEquals(altitude,  gp.getAltitude(),  1.0e-10 * FastMath.abs(ae));
        Vector3D rebuiltNadir = Vector3D.crossProduct(gp.getSouth(), gp.getWest());
        Assert.assertEquals(0, rebuiltNadir.subtract(gp.getNadir()).getNorm(), 1.0e-15);
    }

    @Before
    public void setUp() {
        Utils.setDataRoot("regular-data");
    }

}

