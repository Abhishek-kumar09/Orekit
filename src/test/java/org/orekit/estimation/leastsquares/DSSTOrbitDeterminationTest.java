/* Copyright 2002-2019 CS Systèmes d'Information
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
package org.orekit.estimation.leastsquares;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.hipparchus.exception.LocalizedCoreFormats;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.linear.QRDecomposer;
import org.hipparchus.linear.RealMatrix;
import org.hipparchus.optim.nonlinear.vector.leastsquares.GaussNewtonOptimizer;
import org.hipparchus.optim.nonlinear.vector.leastsquares.LeastSquaresOptimizer;
import org.hipparchus.optim.nonlinear.vector.leastsquares.LeastSquaresProblem;
import org.hipparchus.optim.nonlinear.vector.leastsquares.LevenbergMarquardtOptimizer;
import org.hipparchus.stat.descriptive.StreamingStatistics;
import org.hipparchus.util.FastMath;
import org.hipparchus.util.Precision;
import org.junit.Assert;
import org.junit.Test;
import org.orekit.KeyValueFileParser;
import org.orekit.Utils;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.BodyCenterPointing;
import org.orekit.attitudes.LofOffset;
import org.orekit.attitudes.NadirPointing;
import org.orekit.attitudes.YawCompensation;
import org.orekit.attitudes.YawSteering;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataProvidersManager;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitMessages;
import org.orekit.estimation.measurements.AngularAzEl;
import org.orekit.estimation.measurements.EstimatedMeasurement;
import org.orekit.estimation.measurements.EstimationsProvider;
import org.orekit.estimation.measurements.GroundStation;
import org.orekit.estimation.measurements.ObservableSatellite;
import org.orekit.estimation.measurements.ObservedMeasurement;
import org.orekit.estimation.measurements.PV;
import org.orekit.estimation.measurements.Range;
import org.orekit.estimation.measurements.RangeRate;
import org.orekit.estimation.measurements.modifiers.AngularRadioRefractionModifier;
import org.orekit.estimation.measurements.modifiers.Bias;
import org.orekit.estimation.measurements.modifiers.OnBoardAntennaRangeModifier;
import org.orekit.estimation.measurements.modifiers.OutlierFilter;
import org.orekit.estimation.measurements.modifiers.RangeIonosphericDelayModifier;
import org.orekit.estimation.measurements.modifiers.RangeRateIonosphericDelayModifier;
import org.orekit.estimation.measurements.modifiers.RangeTroposphericDelayModifier;
import org.orekit.forces.drag.DragSensitive;
import org.orekit.forces.drag.IsotropicDrag;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.ICGEMFormatReader;
import org.orekit.forces.gravity.potential.UnnormalizedSphericalHarmonicsProvider;
import org.orekit.forces.radiation.IsotropicRadiationSingleCoefficient;
import org.orekit.forces.radiation.RadiationSensitive;
import org.orekit.frames.EOPHistory;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.LOFType;
import org.orekit.frames.TopocentricFrame;
import org.orekit.frames.Transform;
import org.orekit.gnss.Frequency;
import org.orekit.gnss.MeasurementType;
import org.orekit.gnss.ObservationData;
import org.orekit.gnss.ObservationDataSet;
import org.orekit.gnss.RinexLoader;
import org.orekit.gnss.SatelliteSystem;
import org.orekit.models.AtmosphericRefractionModel;
import org.orekit.models.earth.EarthITU453AtmosphereRefraction;
import org.orekit.models.earth.atmosphere.Atmosphere;
import org.orekit.models.earth.atmosphere.DTM2000;
import org.orekit.models.earth.atmosphere.data.MarshallSolarActivityFutureEstimation;
import org.orekit.models.earth.displacement.OceanLoading;
import org.orekit.models.earth.displacement.OceanLoadingCoefficientsBLQFactory;
import org.orekit.models.earth.displacement.StationDisplacement;
import org.orekit.models.earth.displacement.TidalDisplacement;
import org.orekit.models.earth.ionosphere.IonosphericModel;
import org.orekit.models.earth.ionosphere.KlobucharIonoCoefficientsLoader;
import org.orekit.models.earth.ionosphere.KlobucharIonoModel;
import org.orekit.models.earth.troposphere.DiscreteTroposphericModel;
import org.orekit.models.earth.troposphere.EstimatedTroposphericModel;
import org.orekit.models.earth.troposphere.GlobalMappingFunctionModel;
import org.orekit.models.earth.troposphere.MappingFunction;
import org.orekit.models.earth.troposphere.NiellMappingFunctionModel;
import org.orekit.models.earth.troposphere.SaastamoinenModel;
import org.orekit.models.earth.weather.GlobalPressureTemperatureModel;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.CircularOrbit;
import org.orekit.orbits.EquinoctialOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.PositionAngle;
import org.orekit.propagation.PropagationType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.propagation.conversion.DSSTPropagatorBuilder;
import org.orekit.propagation.conversion.DormandPrince853IntegratorBuilder;
import org.orekit.propagation.semianalytical.dsst.forces.DSSTAtmosphericDrag;
import org.orekit.propagation.semianalytical.dsst.forces.DSSTSolarRadiationPressure;
import org.orekit.propagation.semianalytical.dsst.forces.DSSTTesseral;
import org.orekit.propagation.semianalytical.dsst.forces.DSSTThirdBody;
import org.orekit.propagation.semianalytical.dsst.forces.DSSTZonal;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateComponents;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.ParameterDriver;
import org.orekit.utils.ParameterDriversList;
import org.orekit.utils.TimeStampedPVCoordinates;

public class DSSTOrbitDeterminationTest {

    @Test
    // Orbit determination using only mean elements for Lageos2 based on SLR (range) measurements
    // For better accuracy, adding short period terms is necessary
    public void testLageos2()
        throws URISyntaxException, IllegalArgumentException, IOException,
               OrekitException, ParseException {

        // input in tutorial resources directory/output
        final String inputPath = DSSTOrbitDeterminationTest.class.getClassLoader().getResource("orbit-determination/Lageos2/dsst_od_test_Lageos2.in").toURI().getPath();
        final File input  = new File(inputPath);

        // configure Orekit data access
        Utils.setDataRoot("orbit-determination/february-2016:potential/icgem-format");
        GravityFieldFactory.addPotentialCoefficientsReader(new ICGEMFormatReader("eigen-6s-truncated", true));

        //orbit determination run.
        ResultOD odLageos2 = run(input, false);

        //test
        //definition of the accuracy for the test
        final double distanceAccuracy = 579;
        final double velocityAccuracy = 1.4;

        //test on the convergence
        final int numberOfIte  = 9;
        final int numberOfEval = 9;

        Assert.assertEquals(numberOfIte, odLageos2.getNumberOfIteration());
        Assert.assertEquals(numberOfEval, odLageos2.getNumberOfEvaluation());

        //test on the estimated position and velocity
        final Vector3D estimatedPos = odLageos2.getEstimatedPV().getPosition();
        final Vector3D estimatedVel = odLageos2.getEstimatedPV().getVelocity();

        //final Vector3D refPos = new Vector3D(-5532124.989973327, 10025700.01763335, -3578940.840115321);
        //final Vector3D refVel = new Vector3D(-3871.2736402553, -607.8775965705, 4280.9744110925);
        final Vector3D refPos = new Vector3D(-5532131.956902, 10025696.592156, -3578940.040009);
        final Vector3D refVel = new Vector3D(-3871.275109, -607.880985, 4280.972530);
        Assert.assertEquals(0.0, Vector3D.distance(refPos, estimatedPos), distanceAccuracy);
        Assert.assertEquals(0.0, Vector3D.distance(refVel, estimatedVel), velocityAccuracy);

        //test on statistic for the range residuals
        final long nbRange = 258;
        //final double[] RefStatRange = { -2.795816, 6.171529, 0.310848, 1.657809 };
        final double[] RefStatRange = { -2.431135, 2.218644, 0.038483, 0.982017 };
        Assert.assertEquals(nbRange, odLageos2.getRangeStat().getN());
        Assert.assertEquals(RefStatRange[0], odLageos2.getRangeStat().getMin(),               distanceAccuracy);
        Assert.assertEquals(RefStatRange[1], odLageos2.getRangeStat().getMax(),               distanceAccuracy);
        Assert.assertEquals(RefStatRange[2], odLageos2.getRangeStat().getMean(),              distanceAccuracy);
        Assert.assertEquals(RefStatRange[3], odLageos2.getRangeStat().getStandardDeviation(), distanceAccuracy);

    }

    @Test
    // Orbit determination using only mean elements for GNSS satellite based on range measurements
    // For better accuracy, adding short period terms is necessary
    public void testGNSS()
        throws URISyntaxException, IllegalArgumentException, IOException,
               OrekitException, ParseException {

        // input in tutorial resources directory/output
        final String inputPath = DSSTOrbitDeterminationTest.class.getClassLoader().getResource("orbit-determination/GNSS/dsst_od_test_GPS07.in").toURI().getPath();
        final File input  = new File(inputPath);

        // configure Orekit data access
        Utils.setDataRoot("orbit-determination/february-2016:potential/icgem-format");
        GravityFieldFactory.addPotentialCoefficientsReader(new ICGEMFormatReader("eigen-6s-truncated", true));

        //orbit determination run.
        ResultOD odGNSS = run(input, false);

        //test
        //definition of the accuracy for the test
        final double distanceAccuracy = 59;
        final double velocityAccuracy = 0.23;

        //test on the convergence
        final int numberOfIte  = 2;
        final int numberOfEval = 3;

        Assert.assertEquals(numberOfIte, odGNSS.getNumberOfIteration());
        Assert.assertEquals(numberOfEval, odGNSS.getNumberOfEvaluation());

        //test on the estimated position and velocity (reference from IGS-MGEX file com18836.sp3)
        final Vector3D estimatedPos = odGNSS.getEstimatedPV().getPosition();
        final Vector3D estimatedVel = odGNSS.getEstimatedPV().getVelocity();
        final Vector3D refPos = new Vector3D(-2747606.680868164, 22572091.30648564, 13522761.402325712);
        final Vector3D refVel = new Vector3D(-2729.5151218788005, 1142.6629459030657, -2523.9055974487947);
        Assert.assertEquals(0.0, Vector3D.distance(refPos, estimatedPos), distanceAccuracy);
        Assert.assertEquals(0.0, Vector3D.distance(refVel, estimatedVel), velocityAccuracy);

        //test on statistic for the range residuals
        final long nbRange = 4009;
        final double[] RefStatRange = { -83.945, 59.365, 0.0, 20.857 };
        Assert.assertEquals(nbRange, odGNSS.getRangeStat().getN());
        Assert.assertEquals(RefStatRange[0], odGNSS.getRangeStat().getMin(),               1.0e-3);
        Assert.assertEquals(RefStatRange[1], odGNSS.getRangeStat().getMax(),               1.0e-3);
        Assert.assertEquals(RefStatRange[2], odGNSS.getRangeStat().getMean(),              0.23);
        Assert.assertEquals(RefStatRange[3], odGNSS.getRangeStat().getStandardDeviation(), 1.0e-3);

    }

    private class ResultOD {
        private int numberOfIteration;
        private int numberOfEvaluation;
        private TimeStampedPVCoordinates estimatedPV;
        private StreamingStatistics rangeStat;
        ResultOD(ParameterDriversList  propagatorParameters,
                 ParameterDriversList  measurementsParameters,
                 int numberOfIteration, int numberOfEvaluation, TimeStampedPVCoordinates estimatedPV,
                 StreamingStatistics rangeStat, StreamingStatistics rangeRateStat,
                 StreamingStatistics azimStat, StreamingStatistics elevStat,
                 StreamingStatistics posStat, StreamingStatistics velStat,
                 RealMatrix covariances) {
            this.numberOfIteration      = numberOfIteration;
            this.numberOfEvaluation     = numberOfEvaluation;
            this.estimatedPV            = estimatedPV;
            this.rangeStat              =  rangeStat;
        }

        public int getNumberOfIteration() {
            return numberOfIteration;
        }

        public int getNumberOfEvaluation() {
            return numberOfEvaluation;
        }

        public PVCoordinates getEstimatedPV() {
            return estimatedPV;
        }

        public StreamingStatistics getRangeStat() {
            return rangeStat;
        }

    }

    private ResultOD run(final File input, final boolean print)
        throws IOException, IllegalArgumentException, OrekitException, ParseException {

        // read input parameters
        KeyValueFileParser<ParameterKey> parser = new KeyValueFileParser<ParameterKey>(ParameterKey.class);
        parser.parseInput(input.getAbsolutePath(), new FileInputStream(input));

        // log file

        final RangeLog     rangeLog     = new RangeLog();
        final RangeRateLog rangeRateLog = new RangeRateLog();
        final AzimuthLog   azimuthLog   = new AzimuthLog();
        final ElevationLog elevationLog = new ElevationLog();
        final PositionLog  positionLog  = new PositionLog();
        final VelocityLog  velocityLog  = new VelocityLog();

        // gravity field
        GravityFieldFactory.addPotentialCoefficientsReader(new ICGEMFormatReader("eigen-5c.gfc", true));
        final UnnormalizedSphericalHarmonicsProvider gravityField = createGravityField(parser);

        // Orbit initial guess
        final Orbit initialGuess = createOrbit(parser, gravityField.getMu());

        // IERS conventions
        final IERSConventions conventions;
        if (!parser.containsKey(ParameterKey.IERS_CONVENTIONS)) {
            conventions = IERSConventions.IERS_2010;
        } else {
            conventions = IERSConventions.valueOf("IERS_" + parser.getInt(ParameterKey.IERS_CONVENTIONS));
        }

        // central body
        final OneAxisEllipsoid body = createBody(parser);

        // propagator builder
        final DSSTPropagatorBuilder propagatorBuilder =
                        createPropagatorBuilder(parser, conventions, gravityField, body, initialGuess);

        // estimator
        final BatchLSEstimator estimator = createEstimator(parser, propagatorBuilder);

        final Map<String, StationData>    stations                 = createStationsData(parser, conventions, body);
        final PVData                      pvData                   = createPVData(parser);
        final ObservableSatellite         satellite                = createObservableSatellite(parser);
        final Bias<Range>                 satRangeBias             = createSatRangeBias(parser);
        final OnBoardAntennaRangeModifier satAntennaRangeModifier  = createSatAntennaRangeModifier(parser);
        final Weights                     weights                  = createWeights(parser);
        final OutlierFilter<Range>        rangeOutliersManager     = createRangeOutliersManager(parser);
        final OutlierFilter<RangeRate>    rangeRateOutliersManager = createRangeRateOutliersManager(parser);
        final OutlierFilter<AngularAzEl>  azElOutliersManager      = createAzElOutliersManager(parser);
        final OutlierFilter<PV>           pvOutliersManager        = createPVOutliersManager(parser);

        // measurements
        final List<ObservedMeasurement<?>> measurements = new ArrayList<ObservedMeasurement<?>>();
        for (final String fileName : parser.getStringsList(ParameterKey.MEASUREMENTS_FILES, ',')) {
            if (Pattern.matches(RinexLoader.DEFAULT_RINEX_2_SUPPORTED_NAMES, fileName) ||
                Pattern.matches(RinexLoader.DEFAULT_RINEX_3_SUPPORTED_NAMES, fileName)) {
                // the measurements come from a Rinex file
                measurements.addAll(readRinex(new File(input.getParentFile(), fileName),
                                              parser.getString(ParameterKey.SATELLITE_ID_IN_RINEX_FILES),
                                              stations, satellite, satRangeBias, satAntennaRangeModifier, weights,
                                              rangeOutliersManager, rangeRateOutliersManager));
            } else {
                // the measurements come from an Orekit custom file
                measurements.addAll(readMeasurements(new File(input.getParentFile(), fileName),
                                                     stations, pvData, satellite,
                                                     satRangeBias, satAntennaRangeModifier, weights,
                                                     rangeOutliersManager,
                                                     rangeRateOutliersManager,
                                                     azElOutliersManager,
                                                     pvOutliersManager));
            }

        }

        for (ObservedMeasurement<?> measurement : measurements) {
            estimator.addMeasurement(measurement);
        }

        if (print) {
            estimator.setObserver(new BatchLSObserver() {

                private PVCoordinates previousPV;
                {
                    previousPV = initialGuess.getPVCoordinates();
                    final String header = "iteration evaluations      ΔP(m)        ΔV(m/s)           RMS          nb Range    nb Range-rate  nb Angular     nb PV%n";
                    System.out.format(Locale.US, header);
                }

                /** {@inheritDoc} */
                @Override
                public void evaluationPerformed(final int iterationsCount, final int evaluationsCount,
                                                final Orbit[] orbits,
                                                final ParameterDriversList estimatedOrbitalParameters,
                                                final ParameterDriversList estimatedPropagatorParameters,
                                                final ParameterDriversList estimatedMeasurementsParameters,
                                                final EstimationsProvider  evaluationsProvider,
                                                final LeastSquaresProblem.Evaluation lspEvaluation) {
                    PVCoordinates currentPV = orbits[0].getPVCoordinates();
                    final String format0 = "    %2d         %2d                                 %16.12f     %s       %s     %s     %s%n";
                    final String format  = "    %2d         %2d      %13.6f %12.9f %16.12f     %s       %s     %s     %s%n";
                    final EvaluationCounter<Range>     rangeCounter       = new EvaluationCounter<Range>();
                    final EvaluationCounter<RangeRate> rangeRateCounter   = new EvaluationCounter<RangeRate>();
                    final EvaluationCounter<AngularAzEl>   angularCounter = new EvaluationCounter<AngularAzEl>();
                    final EvaluationCounter<PV>        pvCounter          = new EvaluationCounter<PV>();
                    for (final Map.Entry<ObservedMeasurement<?>, EstimatedMeasurement<?>> entry : estimator.getLastEstimations().entrySet()) {
                        if (entry.getKey() instanceof Range) {
                            @SuppressWarnings("unchecked")
                            EstimatedMeasurement<Range> evaluation = (EstimatedMeasurement<Range>) entry.getValue();
                            rangeCounter.add(evaluation);
                        } else if (entry.getKey() instanceof RangeRate) {
                            @SuppressWarnings("unchecked")
                            EstimatedMeasurement<RangeRate> evaluation = (EstimatedMeasurement<RangeRate>) entry.getValue();
                            rangeRateCounter.add(evaluation);
                        } else if (entry.getKey() instanceof AngularAzEl) {
                            @SuppressWarnings("unchecked")
                            EstimatedMeasurement<AngularAzEl> evaluation = (EstimatedMeasurement<AngularAzEl>) entry.getValue();
                            angularCounter.add(evaluation);
                        } else if (entry.getKey() instanceof PV) {
                            @SuppressWarnings("unchecked")
                            EstimatedMeasurement<PV> evaluation = (EstimatedMeasurement<PV>) entry.getValue();
                            pvCounter.add(evaluation);
                        }
                    }
                    if (evaluationsCount == 1) {
                        System.out.format(Locale.US, format0,
                                          iterationsCount, evaluationsCount,
                                          lspEvaluation.getRMS(),
                                          rangeCounter.format(8), rangeRateCounter.format(8),
                                          angularCounter.format(8), pvCounter.format(8));
                    } else {
                        System.out.format(Locale.US, format,
                                          iterationsCount, evaluationsCount,
                                          Vector3D.distance(previousPV.getPosition(), currentPV.getPosition()),
                                          Vector3D.distance(previousPV.getVelocity(), currentPV.getVelocity()),
                                          lspEvaluation.getRMS(),
                                          rangeCounter.format(8), rangeRateCounter.format(8),
                                          angularCounter.format(8), pvCounter.format(8));
                    }
                    previousPV = currentPV;
                }
            });
        }
        Orbit estimated = estimator.estimate()[0].getInitialState().getOrbit();

        // compute some statistics
        for (final Map.Entry<ObservedMeasurement<?>, EstimatedMeasurement<?>> entry : estimator.getLastEstimations().entrySet()) {
            if (entry.getKey() instanceof Range) {
                @SuppressWarnings("unchecked")
                EstimatedMeasurement<Range> evaluation = (EstimatedMeasurement<Range>) entry.getValue();
                rangeLog.add(evaluation);
            } else if (entry.getKey() instanceof RangeRate) {
                @SuppressWarnings("unchecked")
                EstimatedMeasurement<RangeRate> evaluation = (EstimatedMeasurement<RangeRate>) entry.getValue();
                rangeRateLog.add(evaluation);
            } else if (entry.getKey() instanceof AngularAzEl) {
                @SuppressWarnings("unchecked")
                EstimatedMeasurement<AngularAzEl> evaluation = (EstimatedMeasurement<AngularAzEl>) entry.getValue();
                azimuthLog.add(evaluation);
                elevationLog.add(evaluation);
            } else if (entry.getKey() instanceof PV) {
                @SuppressWarnings("unchecked")
                EstimatedMeasurement<PV> evaluation = (EstimatedMeasurement<PV>) entry.getValue();
                positionLog.add(evaluation);
                velocityLog.add(evaluation);
            }
        }

        // parmaters and measurements.
        final ParameterDriversList propagatorParameters   = estimator.getPropagatorParametersDrivers(true);
        final ParameterDriversList measurementsParameters = estimator.getMeasurementsParametersDrivers(true);

        //instation of results
        return new ResultOD(propagatorParameters, measurementsParameters,
                            estimator.getIterationsCount(), estimator.getEvaluationsCount(), estimated.getPVCoordinates(),
                            rangeLog.createStatisticsSummary(),  rangeRateLog.createStatisticsSummary(),
                            azimuthLog.createStatisticsSummary(),  elevationLog.createStatisticsSummary(),
                            positionLog.createStatisticsSummary(),  velocityLog.createStatisticsSummary(),
                            estimator.getPhysicalCovariances(1.0e-10));
    } 

    /** Create a propagator builder from input parameters
     * @param parser input file parser
     * @param conventions IERS conventions to use
     * @param gravityField gravity field
     * @param body central body
     * @param orbit first orbit estimate
     * @return propagator builder
     * @throws NoSuchElementException if input parameters are missing
     */
    private DSSTPropagatorBuilder createPropagatorBuilder(final KeyValueFileParser<ParameterKey> parser,
                                                          final IERSConventions conventions,
                                                          final UnnormalizedSphericalHarmonicsProvider gravityField,
                                                          final OneAxisEllipsoid body,
                                                          final Orbit orbit)
        throws NoSuchElementException {

        final double minStep;
        if (!parser.containsKey(ParameterKey.PROPAGATOR_MIN_STEP)) {
            minStep = 6000.0;
        } else {
            minStep = parser.getDouble(ParameterKey.PROPAGATOR_MIN_STEP);
        }

        final double maxStep;
        if (!parser.containsKey(ParameterKey.PROPAGATOR_MAX_STEP)) {
            maxStep = 86400.0;
        } else {
            maxStep = parser.getDouble(ParameterKey.PROPAGATOR_MAX_STEP);
        }

        final double dP;
        if (!parser.containsKey(ParameterKey.PROPAGATOR_POSITION_ERROR)) {
            dP = 10.0;
        } else {
            dP = parser.getDouble(ParameterKey.PROPAGATOR_POSITION_ERROR);
        }

        final double positionScale;
        if (!parser.containsKey(ParameterKey.ESTIMATOR_ORBITAL_PARAMETERS_POSITION_SCALE)) {
            positionScale = dP;
        } else {
            positionScale = parser.getDouble(ParameterKey.ESTIMATOR_ORBITAL_PARAMETERS_POSITION_SCALE);
        }
        final EquinoctialOrbit orbitEqui = new EquinoctialOrbit(orbit);
        final DSSTPropagatorBuilder propagatorBuilder =
                        new DSSTPropagatorBuilder(orbitEqui,
                                                  new DormandPrince853IntegratorBuilder(minStep, maxStep, dP),
                                                  positionScale,
                                                  PropagationType.MEAN,
                                                  PropagationType.MEAN);
        
        // initial mass
        final double mass;
        if (!parser.containsKey(ParameterKey.MASS)) {
            mass = 1000.0;
        } else {
            mass = parser.getDouble(ParameterKey.MASS);
        }
        propagatorBuilder.setMass(mass);

        propagatorBuilder.addForceModel(new DSSTTesseral(body.getBodyFrame(),
                                                         Constants.WGS84_EARTH_ANGULAR_VELOCITY, gravityField,
                                                         gravityField.getMaxDegree(), gravityField.getMaxOrder(), 4, 12,
                                                         gravityField.getMaxDegree(), gravityField.getMaxOrder(), 4));
        propagatorBuilder.addForceModel(new DSSTZonal(gravityField, gravityField.getMaxDegree(), 4,
                                                      2 * gravityField.getMaxDegree() + 1));
        // third body attraction
        if (parser.containsKey(ParameterKey.THIRD_BODY_SUN) &&
            parser.getBoolean(ParameterKey.THIRD_BODY_SUN)) {
            propagatorBuilder.addForceModel(new DSSTThirdBody(CelestialBodyFactory.getSun(), gravityField.getMu()));
        }
        if (parser.containsKey(ParameterKey.THIRD_BODY_MOON) &&
            parser.getBoolean(ParameterKey.THIRD_BODY_MOON)) {
            propagatorBuilder.addForceModel(new DSSTThirdBody(CelestialBodyFactory.getMoon(), gravityField.getMu()));
        }

        // drag
        if (parser.containsKey(ParameterKey.DRAG) && parser.getBoolean(ParameterKey.DRAG)) {
            final double  cd          = parser.getDouble(ParameterKey.DRAG_CD);
            final double  area        = parser.getDouble(ParameterKey.DRAG_AREA);
            final boolean cdEstimated = parser.getBoolean(ParameterKey.DRAG_CD_ESTIMATED);

            MarshallSolarActivityFutureEstimation msafe =
                            new MarshallSolarActivityFutureEstimation("(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\p{Digit}\\p{Digit}\\p{Digit}\\p{Digit}F10\\.(?:txt|TXT)",
            MarshallSolarActivityFutureEstimation.StrengthLevel.AVERAGE);
            DataProvidersManager manager = DataProvidersManager.getInstance();
            manager.feed(msafe.getSupportedNames(), msafe);
            Atmosphere atmosphere = new DTM2000(msafe, CelestialBodyFactory.getSun(), body);
            propagatorBuilder.addForceModel(new DSSTAtmosphericDrag(atmosphere, new IsotropicDrag(area, cd), gravityField.getMu()));
            if (cdEstimated) {
                for (final ParameterDriver driver : propagatorBuilder.getPropagationParametersDrivers().getDrivers()) {
                    if (driver.getName().equals(DragSensitive.DRAG_COEFFICIENT)) {
                        driver.setSelected(true);
                    }
                }
            }
        }

        // solar radiation pressure
        if (parser.containsKey(ParameterKey.SOLAR_RADIATION_PRESSURE) && parser.getBoolean(ParameterKey.SOLAR_RADIATION_PRESSURE)) {
            final double  cr          = parser.getDouble(ParameterKey.SOLAR_RADIATION_PRESSURE_CR);
            final double  area        = parser.getDouble(ParameterKey.SOLAR_RADIATION_PRESSURE_AREA);
            final boolean cREstimated = parser.getBoolean(ParameterKey.SOLAR_RADIATION_PRESSURE_CR_ESTIMATED);

            propagatorBuilder.addForceModel(new DSSTSolarRadiationPressure(CelestialBodyFactory.getSun(),
                                                                       body.getEquatorialRadius(),
                                                                       new IsotropicRadiationSingleCoefficient(area, cr),
                                                                       gravityField.getMu()));
            if (cREstimated) {
                for (final ParameterDriver driver : propagatorBuilder.getPropagationParametersDrivers().getDrivers()) {
                    if (driver.getName().equals(RadiationSensitive.REFLECTION_COEFFICIENT)) {
                        driver.setSelected(true);
                    }
                }
            }
        }

        // attitude mode
        final AttitudeMode mode;
        if (parser.containsKey(ParameterKey.ATTITUDE_MODE)) {
            mode = AttitudeMode.valueOf(parser.getString(ParameterKey.ATTITUDE_MODE));
        } else {
            mode = AttitudeMode.NADIR_POINTING_WITH_YAW_COMPENSATION;
        }
        propagatorBuilder.setAttitudeProvider(mode.getProvider(orbit.getFrame(), body));

        return propagatorBuilder;

    }
    
    /** Create a gravity field from input parameters
     * @param parser input file parser
     * @return gravity field
     */
    private UnnormalizedSphericalHarmonicsProvider createGravityField(final KeyValueFileParser<ParameterKey> parser) {
        final int degree = parser.getInt(ParameterKey.CENTRAL_BODY_DEGREE);
        final int order  = FastMath.min(degree, parser.getInt(ParameterKey.CENTRAL_BODY_ORDER));
        return GravityFieldFactory.getUnnormalizedProvider(degree, order);
    }

    /** Create an orbit from input parameters
     * @param parser input file parser
     * @param mu     central attraction coefficient
     */
    private OneAxisEllipsoid createBody(final KeyValueFileParser<ParameterKey> parser) {

        final Frame bodyFrame;
        if (!parser.containsKey(ParameterKey.BODY_FRAME)) {
            bodyFrame = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
        } else {
            bodyFrame = parser.getEarthFrame(ParameterKey.BODY_FRAME);
        }

        final double equatorialRadius;
        if (!parser.containsKey(ParameterKey.BODY_EQUATORIAL_RADIUS)) {
            equatorialRadius = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
        } else {
            equatorialRadius = parser.getDouble(ParameterKey.BODY_EQUATORIAL_RADIUS);
        }

        final double flattening;
        if (!parser.containsKey(ParameterKey.BODY_INVERSE_FLATTENING)) {
            flattening = Constants.WGS84_EARTH_FLATTENING;
        } else {
            flattening = 1.0 / parser.getDouble(ParameterKey.BODY_INVERSE_FLATTENING);
        }

        return new OneAxisEllipsoid(equatorialRadius, flattening, bodyFrame);

    }
    
    /** Create an orbit from input parameters
     * @param parser input file parser
     * @param mu     central attraction coefficient
     */
    private Orbit createOrbit(final KeyValueFileParser<ParameterKey> parser, final double mu) {

        final Frame frame;
        if (!parser.containsKey(ParameterKey.INERTIAL_FRAME)) {
            frame = FramesFactory.getEME2000();
        } else {
            frame = parser.getInertialFrame(ParameterKey.INERTIAL_FRAME);
        }

        // Orbit definition
        PositionAngle angleType = PositionAngle.MEAN;
        if (parser.containsKey(ParameterKey.ORBIT_ANGLE_TYPE)) {
            angleType = PositionAngle.valueOf(parser.getString(ParameterKey.ORBIT_ANGLE_TYPE).toUpperCase());
        }
        if (parser.containsKey(ParameterKey.ORBIT_KEPLERIAN_A)) {
            return new KeplerianOrbit(parser.getDouble(ParameterKey.ORBIT_KEPLERIAN_A),
                                      parser.getDouble(ParameterKey.ORBIT_KEPLERIAN_E),
                                      parser.getAngle(ParameterKey.ORBIT_KEPLERIAN_I),
                                      parser.getAngle(ParameterKey.ORBIT_KEPLERIAN_PA),
                                      parser.getAngle(ParameterKey.ORBIT_KEPLERIAN_RAAN),
                                      parser.getAngle(ParameterKey.ORBIT_KEPLERIAN_ANOMALY),
                                      angleType,
                                      frame,
                                      parser.getDate(ParameterKey.ORBIT_DATE,
                                                     TimeScalesFactory.getUTC()),
                                      mu);
        } else if (parser.containsKey(ParameterKey.ORBIT_EQUINOCTIAL_A)) {
            return new EquinoctialOrbit(parser.getDouble(ParameterKey.ORBIT_EQUINOCTIAL_A),
                                        parser.getDouble(ParameterKey.ORBIT_EQUINOCTIAL_EX),
                                        parser.getDouble(ParameterKey.ORBIT_EQUINOCTIAL_EY),
                                        parser.getDouble(ParameterKey.ORBIT_EQUINOCTIAL_HX),
                                        parser.getDouble(ParameterKey.ORBIT_EQUINOCTIAL_HY),
                                        parser.getAngle(ParameterKey.ORBIT_EQUINOCTIAL_LAMBDA),
                                        angleType,
                                        frame,
                                        parser.getDate(ParameterKey.ORBIT_DATE,
                                                       TimeScalesFactory.getUTC()),
                                        mu);
        } else if (parser.containsKey(ParameterKey.ORBIT_CIRCULAR_A)) {
            return new CircularOrbit(parser.getDouble(ParameterKey.ORBIT_CIRCULAR_A),
                                     parser.getDouble(ParameterKey.ORBIT_CIRCULAR_EX),
                                     parser.getDouble(ParameterKey.ORBIT_CIRCULAR_EY),
                                     parser.getAngle(ParameterKey.ORBIT_CIRCULAR_I),
                                     parser.getAngle(ParameterKey.ORBIT_CIRCULAR_RAAN),
                                     parser.getAngle(ParameterKey.ORBIT_CIRCULAR_ALPHA),
                                     angleType,
                                     frame,
                                     parser.getDate(ParameterKey.ORBIT_DATE,
                                                    TimeScalesFactory.getUTC()),
                                     mu);
        } else if (parser.containsKey(ParameterKey.ORBIT_TLE_LINE_1)) {
            final String line1 = parser.getString(ParameterKey.ORBIT_TLE_LINE_1);
            final String line2 = parser.getString(ParameterKey.ORBIT_TLE_LINE_2);
            final TLE tle = new TLE(line1, line2);

            TLEPropagator propagator = TLEPropagator.selectExtrapolator(tle);
           // propagator.setEphemerisMode();

            AbsoluteDate initDate = tle.getDate();
            SpacecraftState initialState = propagator.getInitialState();


            //Transformation from TEME to frame.
            Transform t =FramesFactory.getTEME().getTransformTo(FramesFactory.getEME2000(), initDate.getDate());
            return new CartesianOrbit( t.transformPVCoordinates(initialState.getPVCoordinates()) ,
                                      frame,
                                      initDate,
                                       mu);


        } else {
            final double[] pos = {parser.getDouble(ParameterKey.ORBIT_CARTESIAN_PX),
                                  parser.getDouble(ParameterKey.ORBIT_CARTESIAN_PY),
                                  parser.getDouble(ParameterKey.ORBIT_CARTESIAN_PZ)};
            final double[] vel = {parser.getDouble(ParameterKey.ORBIT_CARTESIAN_VX),
                                  parser.getDouble(ParameterKey.ORBIT_CARTESIAN_VY),
                                  parser.getDouble(ParameterKey.ORBIT_CARTESIAN_VZ)};

            return new CartesianOrbit(new PVCoordinates(new Vector3D(pos), new Vector3D(vel)),
                                      frame,
                                      parser.getDate(ParameterKey.ORBIT_DATE,
                                                     TimeScalesFactory.getUTC()),
                                      mu);
        }

    }

    /** Set up range bias due to transponder delay.
     * @param parser input file parser
     * @RETURN range bias (may be null if bias is fixed to zero)
     */
    private Bias<Range> createSatRangeBias(final KeyValueFileParser<ParameterKey> parser) {

        // transponder delay
        final double transponderDelayBias;
        if (!parser.containsKey(ParameterKey.ONBOARD_RANGE_BIAS)) {
            transponderDelayBias = 0;
        } else {
            transponderDelayBias = parser.getDouble(ParameterKey.ONBOARD_RANGE_BIAS);
        }

        final double transponderDelayBiasMin;
        if (!parser.containsKey(ParameterKey.ONBOARD_RANGE_BIAS_MIN)) {
            transponderDelayBiasMin = Double.NEGATIVE_INFINITY;
        } else {
            transponderDelayBiasMin = parser.getDouble(ParameterKey.ONBOARD_RANGE_BIAS_MIN);
        }

        final double transponderDelayBiasMax;
        if (!parser.containsKey(ParameterKey.ONBOARD_RANGE_BIAS_MAX)) {
            transponderDelayBiasMax = Double.NEGATIVE_INFINITY;
        } else {
            transponderDelayBiasMax = parser.getDouble(ParameterKey.ONBOARD_RANGE_BIAS_MAX);
        }

        // bias estimation flag
        final boolean transponderDelayBiasEstimated;
        if (!parser.containsKey(ParameterKey.ONBOARD_RANGE_BIAS_ESTIMATED)) {
            transponderDelayBiasEstimated = false;
        } else {
            transponderDelayBiasEstimated = parser.getBoolean(ParameterKey.ONBOARD_RANGE_BIAS_ESTIMATED);
        }

        if (FastMath.abs(transponderDelayBias) >= Precision.SAFE_MIN || transponderDelayBiasEstimated) {
            // bias is either non-zero or will be estimated,
            // we really need to create a modifier for this
            final Bias<Range> bias = new Bias<Range>(new String[] {
                                                         "transponder delay bias",
                                                     },
                                                     new double[] {
                                                         transponderDelayBias
                                                     },
                                                     new double[] {
                                                         1.0
                                                     },
                                                     new double[] {
                                                         transponderDelayBiasMin
                                                     },
                                                     new double[] {
                                                         transponderDelayBiasMax
                                                     });
            bias.getParametersDrivers().get(0).setSelected(transponderDelayBiasEstimated);
            return bias;
        } else {
            // fixed zero bias, we don't need any modifier
            return null;
        }

    }

    /** Set up range modifier taking on-board antenna offset.
     * @param parser input file parser
     * @return range modifier (may be null if antenna offset is zero or undefined)
     */
    public OnBoardAntennaRangeModifier createSatAntennaRangeModifier(final KeyValueFileParser<ParameterKey> parser) {
        final Vector3D offset;
        if (!parser.containsKey(ParameterKey.ON_BOARD_ANTENNA_PHASE_CENTER_X)) {
            offset = Vector3D.ZERO;
        } else {
            offset = parser.getVector(ParameterKey.ON_BOARD_ANTENNA_PHASE_CENTER_X,
                                      ParameterKey.ON_BOARD_ANTENNA_PHASE_CENTER_Y,
                                      ParameterKey.ON_BOARD_ANTENNA_PHASE_CENTER_Z);
        }
        return offset.getNorm() > 0 ? new OnBoardAntennaRangeModifier(offset) : null;
    }
    
    /** Set up stations.
     * @param parser input file parser
     * @param conventions IERS conventions to use
     * @param body central body
     * @return name to station data map
     */
    private Map<String, StationData> createStationsData(final KeyValueFileParser<ParameterKey> parser,
                                                        final IERSConventions conventions,
                                                        final OneAxisEllipsoid body) {

        final Map<String, StationData> stations       = new HashMap<String, StationData>();

        final String[]  stationNames                      = parser.getStringArray(ParameterKey.GROUND_STATION_NAME);
        final double[]  stationLatitudes                  = parser.getAngleArray(ParameterKey.GROUND_STATION_LATITUDE);
        final double[]  stationLongitudes                 = parser.getAngleArray(ParameterKey.GROUND_STATION_LONGITUDE);
        final double[]  stationAltitudes                  = parser.getDoubleArray(ParameterKey.GROUND_STATION_ALTITUDE);
        final boolean[] stationPositionEstimated          = parser.getBooleanArray(ParameterKey.GROUND_STATION_POSITION_ESTIMATED);
        final double[]  stationClockOffsets               = parser.getDoubleArray(ParameterKey.GROUND_STATION_CLOCK_OFFSET);
        final double[]  stationClockOffsetsMin            = parser.getDoubleArray(ParameterKey.GROUND_STATION_CLOCK_OFFSET_MIN);
        final double[]  stationClockOffsetsMax            = parser.getDoubleArray(ParameterKey.GROUND_STATION_CLOCK_OFFSET_MAX);
        final boolean[] stationClockOffsetEstimated       = parser.getBooleanArray(ParameterKey.GROUND_STATION_CLOCK_OFFSET_ESTIMATED);
        final double[]  stationRangeSigma                 = parser.getDoubleArray(ParameterKey.GROUND_STATION_RANGE_SIGMA);
        final double[]  stationRangeBias                  = parser.getDoubleArray(ParameterKey.GROUND_STATION_RANGE_BIAS);
        final double[]  stationRangeBiasMin               = parser.getDoubleArray(ParameterKey.GROUND_STATION_RANGE_BIAS_MIN);
        final double[]  stationRangeBiasMax               = parser.getDoubleArray(ParameterKey.GROUND_STATION_RANGE_BIAS_MAX);
        final boolean[] stationRangeBiasEstimated         = parser.getBooleanArray(ParameterKey.GROUND_STATION_RANGE_BIAS_ESTIMATED);
        final double[]  stationRangeRateSigma             = parser.getDoubleArray(ParameterKey.GROUND_STATION_RANGE_RATE_SIGMA);
        final double[]  stationRangeRateBias              = parser.getDoubleArray(ParameterKey.GROUND_STATION_RANGE_RATE_BIAS);
        final double[]  stationRangeRateBiasMin           = parser.getDoubleArray(ParameterKey.GROUND_STATION_RANGE_RATE_BIAS_MIN);
        final double[]  stationRangeRateBiasMax           = parser.getDoubleArray(ParameterKey.GROUND_STATION_RANGE_RATE_BIAS_MAX);
        final boolean[] stationRangeRateBiasEstimated     = parser.getBooleanArray(ParameterKey.GROUND_STATION_RANGE_RATE_BIAS_ESTIMATED);
        final double[]  stationAzimuthSigma               = parser.getAngleArray(ParameterKey.GROUND_STATION_AZIMUTH_SIGMA);
        final double[]  stationAzimuthBias                = parser.getAngleArray(ParameterKey.GROUND_STATION_AZIMUTH_BIAS);
        final double[]  stationAzimuthBiasMin             = parser.getAngleArray(ParameterKey.GROUND_STATION_AZIMUTH_BIAS_MIN);
        final double[]  stationAzimuthBiasMax             = parser.getAngleArray(ParameterKey.GROUND_STATION_AZIMUTH_BIAS_MAX);
        final double[]  stationElevationSigma             = parser.getAngleArray(ParameterKey.GROUND_STATION_ELEVATION_SIGMA);
        final double[]  stationElevationBias              = parser.getAngleArray(ParameterKey.GROUND_STATION_ELEVATION_BIAS);
        final double[]  stationElevationBiasMin           = parser.getAngleArray(ParameterKey.GROUND_STATION_ELEVATION_BIAS_MIN);
        final double[]  stationElevationBiasMax           = parser.getAngleArray(ParameterKey.GROUND_STATION_ELEVATION_BIAS_MAX);
        final boolean[] stationAzElBiasesEstimated        = parser.getBooleanArray(ParameterKey.GROUND_STATION_AZ_EL_BIASES_ESTIMATED);
        final boolean[] stationElevationRefraction        = parser.getBooleanArray(ParameterKey.GROUND_STATION_ELEVATION_REFRACTION_CORRECTION);
        final boolean[] stationTroposphericModelEstimated = parser.getBooleanArray(ParameterKey.GROUND_STATION_TROPOSPHERIC_MODEL_ESTIMATED);
        final double[]  stationTroposphericZenithDelay    = parser.getDoubleArray(ParameterKey.GROUND_STATION_TROPOSPHERIC_ZENITH_DELAY);
        final boolean[] stationZenithDelayEstimated       = parser.getBooleanArray(ParameterKey.GROUND_STATION_TROPOSPHERIC_DELAY_ESTIMATED);
        final boolean[] stationGlobalMappingFunction      = parser.getBooleanArray(ParameterKey.GROUND_STATION_GLOBAL_MAPPING_FUNCTION);
        final boolean[] stationNiellMappingFunction       = parser.getBooleanArray(ParameterKey.GROUND_STATION_NIELL_MAPPING_FUNCTION);
        final boolean[] stationWeatherEstimated           = parser.getBooleanArray(ParameterKey.GROUND_STATION_WEATHER_ESTIMATED);
        final boolean[] stationRangeTropospheric          = parser.getBooleanArray(ParameterKey.GROUND_STATION_RANGE_TROPOSPHERIC_CORRECTION);
        //final boolean[] stationIonosphericCorrection    = parser.getBooleanArray(ParameterKey.GROUND_STATION_IONOSPHERIC_CORRECTION);

        final TidalDisplacement tidalDisplacement;
        if (parser.containsKey(ParameterKey.SOLID_TIDES_DISPLACEMENT_CORRECTION) &&
            parser.getBoolean(ParameterKey.SOLID_TIDES_DISPLACEMENT_CORRECTION)) {
            final boolean removePermanentDeformation =
                            parser.containsKey(ParameterKey.SOLID_TIDES_DISPLACEMENT_REMOVE_PERMANENT_DEFORMATION) &&
                            parser.getBoolean(ParameterKey.SOLID_TIDES_DISPLACEMENT_REMOVE_PERMANENT_DEFORMATION);
            tidalDisplacement = new TidalDisplacement(Constants.EIGEN5C_EARTH_EQUATORIAL_RADIUS,
                                                      Constants.JPL_SSD_SUN_EARTH_PLUS_MOON_MASS_RATIO,
                                                      Constants.JPL_SSD_EARTH_MOON_MASS_RATIO,
                                                      CelestialBodyFactory.getSun(),
                                                      CelestialBodyFactory.getMoon(),
                                                      conventions,
                                                      removePermanentDeformation);
        } else {
            tidalDisplacement = null;
        }

        final OceanLoadingCoefficientsBLQFactory blqFactory;
        if (parser.containsKey(ParameterKey.OCEAN_LOADING_CORRECTION) &&
            parser.getBoolean(ParameterKey.OCEAN_LOADING_CORRECTION)) {
            blqFactory = new OceanLoadingCoefficientsBLQFactory("^.*\\.blq$");
        } else {
            blqFactory = null;
        }

        final EOPHistory eopHistory = FramesFactory.findEOP(body.getBodyFrame());
        for (int i = 0; i < stationNames.length; ++i) {

            // displacements
            final StationDisplacement[] displacements;
            final OceanLoading oceanLoading = (blqFactory == null) ?
                                              null :
                                              new OceanLoading(body, blqFactory.getCoefficients(stationNames[i]));
            if (tidalDisplacement == null) {
                if (oceanLoading == null) {
                    displacements = new StationDisplacement[0];
                } else {
                    displacements = new StationDisplacement[] {
                        oceanLoading
                    };
                }
            } else {
                if (oceanLoading == null) {
                    displacements = new StationDisplacement[] {
                        tidalDisplacement
                    };
                } else {
                    displacements = new StationDisplacement[] {
                        tidalDisplacement, oceanLoading
                    };
                }
            }

            // the station itself
            final GeodeticPoint position = new GeodeticPoint(stationLatitudes[i],
                                                             stationLongitudes[i],
                                                             stationAltitudes[i]);
            final TopocentricFrame topo = new TopocentricFrame(body, position, stationNames[i]);
            final GroundStation station = new GroundStation(topo, eopHistory, displacements);
            station.getClockOffsetDriver().setReferenceValue(stationClockOffsets[i]);
            station.getClockOffsetDriver().setValue(stationClockOffsets[i]);
            station.getClockOffsetDriver().setMinValue(stationClockOffsetsMin[i]);
            station.getClockOffsetDriver().setMaxValue(stationClockOffsetsMax[i]);
            station.getClockOffsetDriver().setSelected(stationClockOffsetEstimated[i]);
            station.getEastOffsetDriver().setSelected(stationPositionEstimated[i]);
            station.getNorthOffsetDriver().setSelected(stationPositionEstimated[i]);
            station.getZenithOffsetDriver().setSelected(stationPositionEstimated[i]);

            // range
            final double rangeSigma = stationRangeSigma[i];
            final Bias<Range> rangeBias;
            if (FastMath.abs(stationRangeBias[i])   >= Precision.SAFE_MIN || stationRangeBiasEstimated[i]) {
                 rangeBias = new Bias<Range>(new String[] {
                                                 stationNames[i] + "/range bias",
                                             },
                                             new double[] {
                                                 stationRangeBias[i]
                                             },
                                             new double[] {
                                                 rangeSigma
                                             },
                                             new double[] {
                                                 stationRangeBiasMin[i]
                                             },
                                             new double[] {
                                                 stationRangeBiasMax[i]
                                             });
                 rangeBias.getParametersDrivers().get(0).setSelected(stationRangeBiasEstimated[i]);
            } else {
                // bias fixed to zero, we don't need to create a modifier for this
                rangeBias = null;
            }

            // range rate
            final double rangeRateSigma = stationRangeRateSigma[i];
            final Bias<RangeRate> rangeRateBias;
            if (FastMath.abs(stationRangeRateBias[i])   >= Precision.SAFE_MIN || stationRangeRateBiasEstimated[i]) {
                rangeRateBias = new Bias<RangeRate>(new String[] {
                                                        stationNames[i] + "/range rate bias"
                                                    },
                                                    new double[] {
                                                        stationRangeRateBias[i]
                                                    },
                                                    new double[] {
                                                        rangeRateSigma
                                                    },
                                                    new double[] {
                                                        stationRangeRateBiasMin[i]
                                                    },
                                                    new double[] {
                                                        stationRangeRateBiasMax[i]
                                                    });
                rangeRateBias.getParametersDrivers().get(0).setSelected(stationRangeRateBiasEstimated[i]);
            } else {
                // bias fixed to zero, we don't need to create a modifier for this
                rangeRateBias = null;
            }

            // angular biases
            final double[] azELSigma = new double[] {
                stationAzimuthSigma[i], stationElevationSigma[i]
            };
            final Bias<AngularAzEl> azELBias;
            if (FastMath.abs(stationAzimuthBias[i])   >= Precision.SAFE_MIN ||
                FastMath.abs(stationElevationBias[i]) >= Precision.SAFE_MIN ||
                stationAzElBiasesEstimated[i]) {
                azELBias = new Bias<AngularAzEl>(new String[] {
                                                 stationNames[i] + "/az bias",
                                                 stationNames[i] + "/el bias"
                                             },
                                             new double[] {
                                                 stationAzimuthBias[i],
                                                 stationElevationBias[i]
                                             },
                                             azELSigma,
                                             new double[] {
                                                 stationAzimuthBiasMin[i],
                                                 stationElevationBiasMin[i]
                                             },
                                             new double[] {
                                                 stationAzimuthBiasMax[i],
                                                 stationElevationBiasMax[i]
                                             });
                azELBias.getParametersDrivers().get(0).setSelected(stationAzElBiasesEstimated[i]);
                azELBias.getParametersDrivers().get(1).setSelected(stationAzElBiasesEstimated[i]);
            } else {
                // bias fixed to zero, we don't need to create a modifier for this
                azELBias = null;
            }

            //Refraction correction
            final AngularRadioRefractionModifier refractionCorrection;
            if (stationElevationRefraction[i]) {
                final double                     altitude        = station.getBaseFrame().getPoint().getAltitude();
                final AtmosphericRefractionModel refractionModel = new EarthITU453AtmosphereRefraction(altitude);
                refractionCorrection = new AngularRadioRefractionModifier(refractionModel);
            } else {
                refractionCorrection = null;
            }


            //Tropospheric correction
            final RangeTroposphericDelayModifier rangeTroposphericCorrection;
            if (stationRangeTropospheric[i]) {

                MappingFunction mappingModel = null;
                if (stationGlobalMappingFunction[i]) {
                    mappingModel = new GlobalMappingFunctionModel(stationLatitudes[i],
                                                                  stationLongitudes[i]);
                } else if (stationNiellMappingFunction[i]) {
                    mappingModel = new NiellMappingFunctionModel(stationLatitudes[i]);
                }

                DiscreteTroposphericModel troposphericModel;
                if (stationTroposphericModelEstimated[i] && mappingModel != null) {

                    if(stationWeatherEstimated[i]) {
                        final GlobalPressureTemperatureModel weather = new GlobalPressureTemperatureModel(stationLatitudes[i],
                                                                                                          stationLongitudes[i],
                                                                                                          body.getBodyFrame());
                        weather.weatherParameters(stationAltitudes[i], parser.getDate(ParameterKey.ORBIT_DATE,
                                                                                      TimeScalesFactory.getUTC()));
                        final double temperature = weather.getTemperature();
                        final double pressure    = weather.getPressure();
                        troposphericModel = new EstimatedTroposphericModel(temperature, pressure, mappingModel,
                                                                           stationTroposphericZenithDelay[i]);
                    } else {
                        troposphericModel = new EstimatedTroposphericModel(mappingModel, stationTroposphericZenithDelay[i]);   
                    }

                    ParameterDriver totalDelay = troposphericModel.getParametersDrivers().get(0);
                    totalDelay.setSelected(stationZenithDelayEstimated[i]);
                    totalDelay.setName(stationNames[i].substring(0, 5) + EstimatedTroposphericModel.TOTAL_ZENITH_DELAY);
                } else {
                    troposphericModel = SaastamoinenModel.getStandardModel();
                }

                rangeTroposphericCorrection = new  RangeTroposphericDelayModifier(troposphericModel);
            } else {
                rangeTroposphericCorrection = null;
            }

        stations.put(stationNames[i], new StationData(station,
                                                      rangeSigma,     rangeBias,
                                                      rangeRateSigma, rangeRateBias,
                                                      azELSigma,      azELBias,
                                                      refractionCorrection, rangeTroposphericCorrection));
        }
        return stations;

    }

    /** Set up weights.
     * @param parser input file parser
     * @return base weights
     * @throws NoSuchElementException if input parameters are missing
     */
    private Weights createWeights(final KeyValueFileParser<ParameterKey> parser)
        throws NoSuchElementException {
        return new Weights(parser.getDouble(ParameterKey.RANGE_MEASUREMENTS_BASE_WEIGHT),
                           parser.getDouble(ParameterKey.RANGE_RATE_MEASUREMENTS_BASE_WEIGHT),
                           new double[] {
                               parser.getDouble(ParameterKey.AZIMUTH_MEASUREMENTS_BASE_WEIGHT),
                               parser.getDouble(ParameterKey.ELEVATION_MEASUREMENTS_BASE_WEIGHT)
                           },
                           parser.getDouble(ParameterKey.PV_MEASUREMENTS_BASE_WEIGHT));
    }

    /** Set up outliers manager for range measurements.
     * @param parser input file parser
     * @return outliers manager (null if none configured)
     */
    private OutlierFilter<Range> createRangeOutliersManager(final KeyValueFileParser<ParameterKey> parser) {
        if (parser.containsKey(ParameterKey.RANGE_OUTLIER_REJECTION_MULTIPLIER) !=
            parser.containsKey(ParameterKey.RANGE_OUTLIER_REJECTION_STARTING_ITERATION)) {
            throw new OrekitException(LocalizedCoreFormats.SIMPLE_MESSAGE,
                                      ParameterKey.RANGE_OUTLIER_REJECTION_MULTIPLIER.toString().toLowerCase().replace('_', '.') +
                                      " and  " +
                                      ParameterKey.RANGE_OUTLIER_REJECTION_STARTING_ITERATION.toString().toLowerCase().replace('_', '.') +
                                      " must be both present or both absent");
        }
        return new OutlierFilter<Range>(parser.getInt(ParameterKey.RANGE_OUTLIER_REJECTION_STARTING_ITERATION),
                                        parser.getInt(ParameterKey.RANGE_OUTLIER_REJECTION_MULTIPLIER));
    }

    /** Set up outliers manager for range-rate measurements.
     * @param parser input file parser
     * @return outliers manager (null if none configured)
     */
    private OutlierFilter<RangeRate> createRangeRateOutliersManager(final KeyValueFileParser<ParameterKey> parser) {
        if (parser.containsKey(ParameterKey.RANGE_RATE_OUTLIER_REJECTION_MULTIPLIER) !=
            parser.containsKey(ParameterKey.RANGE_RATE_OUTLIER_REJECTION_STARTING_ITERATION)) {
            throw new OrekitException(LocalizedCoreFormats.SIMPLE_MESSAGE,
                                      ParameterKey.RANGE_RATE_OUTLIER_REJECTION_MULTIPLIER.toString().toLowerCase().replace('_', '.') +
                                      " and  " +
                                      ParameterKey.RANGE_RATE_OUTLIER_REJECTION_STARTING_ITERATION.toString().toLowerCase().replace('_', '.') +
                                      " must be both present or both absent");
        }
        return new OutlierFilter<RangeRate>(parser.getInt(ParameterKey.RANGE_RATE_OUTLIER_REJECTION_STARTING_ITERATION),
                                            parser.getInt(ParameterKey.RANGE_RATE_OUTLIER_REJECTION_MULTIPLIER));
    }

    /** Set up outliers manager for azimuth-elevation measurements.
     * @param parser input file parser
     * @return outliers manager (null if none configured)
     */
    private OutlierFilter<AngularAzEl> createAzElOutliersManager(final KeyValueFileParser<ParameterKey> parser) {
        if (parser.containsKey(ParameterKey.AZ_EL_OUTLIER_REJECTION_MULTIPLIER) !=
            parser.containsKey(ParameterKey.AZ_EL_OUTLIER_REJECTION_STARTING_ITERATION)) {
            throw new OrekitException(LocalizedCoreFormats.SIMPLE_MESSAGE,
                                      ParameterKey.AZ_EL_OUTLIER_REJECTION_MULTIPLIER.toString().toLowerCase().replace('_', '.') +
                                      " and  " +
                                      ParameterKey.AZ_EL_OUTLIER_REJECTION_STARTING_ITERATION.toString().toLowerCase().replace('_', '.') +
                                      " must be both present or both absent");
        }
        return new OutlierFilter<AngularAzEl>(parser.getInt(ParameterKey.AZ_EL_OUTLIER_REJECTION_STARTING_ITERATION),
                                          parser.getInt(ParameterKey.AZ_EL_OUTLIER_REJECTION_MULTIPLIER));
    }

    /** Set up outliers manager for PV measurements.
     * @param parser input file parser
     * @return outliers manager (null if none configured)
     */
    private OutlierFilter<PV> createPVOutliersManager(final KeyValueFileParser<ParameterKey> parser) {
        if (parser.containsKey(ParameterKey.PV_OUTLIER_REJECTION_MULTIPLIER) !=
            parser.containsKey(ParameterKey.PV_OUTLIER_REJECTION_STARTING_ITERATION)) {
            throw new OrekitException(LocalizedCoreFormats.SIMPLE_MESSAGE,
                                      ParameterKey.PV_OUTLIER_REJECTION_MULTIPLIER.toString().toLowerCase().replace('_', '.') +
                                      " and  " +
                                      ParameterKey.PV_OUTLIER_REJECTION_STARTING_ITERATION.toString().toLowerCase().replace('_', '.') +
                                      " must be both present or both absent");
        }
        return new OutlierFilter<PV>(parser.getInt(ParameterKey.PV_OUTLIER_REJECTION_STARTING_ITERATION),
                                     parser.getInt(ParameterKey.PV_OUTLIER_REJECTION_MULTIPLIER));
    }

    /** Set up PV data.
     * @param parser input file parser
     * @return PV data
     * @throws NoSuchElementException if input parameters are missing
     */
    private PVData createPVData(final KeyValueFileParser<ParameterKey> parser)
        throws NoSuchElementException {
        return new PVData(parser.getDouble(ParameterKey.PV_MEASUREMENTS_POSITION_SIGMA),
                          parser.getDouble(ParameterKey.PV_MEASUREMENTS_VELOCITY_SIGMA));
    }

    /** Set up satellite data.
     * @param parser input file parser
     * @return satellite data
     * @throws NoSuchElementException if input parameters are missing
     */
    private ObservableSatellite createObservableSatellite(final KeyValueFileParser<ParameterKey> parser)
        throws NoSuchElementException {
        ObservableSatellite obsSat = new ObservableSatellite(0);
        ParameterDriver clockOffsetDriver = obsSat.getClockOffsetDriver();
        if (parser.containsKey(ParameterKey.ON_BOARD_CLOCK_OFFSET)) {
            clockOffsetDriver.setReferenceValue(parser.getDouble(ParameterKey.ON_BOARD_CLOCK_OFFSET));
            clockOffsetDriver.setValue(parser.getDouble(ParameterKey.ON_BOARD_CLOCK_OFFSET));
        }
        if (parser.containsKey(ParameterKey.ON_BOARD_CLOCK_OFFSET_MIN)) {
            clockOffsetDriver.setMinValue(parser.getDouble(ParameterKey.ON_BOARD_CLOCK_OFFSET_MIN));
        }
        if (parser.containsKey(ParameterKey.ON_BOARD_CLOCK_OFFSET_MAX)) {
            clockOffsetDriver.setMaxValue(parser.getDouble(ParameterKey.ON_BOARD_CLOCK_OFFSET_MAX));
        }
        if (parser.containsKey(ParameterKey.ON_BOARD_CLOCK_OFFSET_ESTIMATED)) {
            clockOffsetDriver.setSelected(parser.getBoolean(ParameterKey.ON_BOARD_CLOCK_OFFSET_ESTIMATED));
        }
        return obsSat;
    }

    /** Set up estimator.
     * @param parser input file parser
     * @param propagatorBuilder propagator builder
     * @return estimator
     * @throws NoSuchElementException if input parameters are missing
     */
    private BatchLSEstimator createEstimator(final KeyValueFileParser<ParameterKey> parser,
                                             final DSSTPropagatorBuilder propagatorBuilder) {
        final boolean optimizerIsLevenbergMarquardt;
        if (! parser.containsKey(ParameterKey.ESTIMATOR_OPTIMIZATION_ENGINE)) {
            optimizerIsLevenbergMarquardt = true;
        } else {
            final String engine = parser.getString(ParameterKey.ESTIMATOR_OPTIMIZATION_ENGINE);
            optimizerIsLevenbergMarquardt = engine.toLowerCase().contains("levenberg");
        }
        final LeastSquaresOptimizer optimizer;

        if (optimizerIsLevenbergMarquardt) {
            // we want to use a Levenberg-Marquardt optimization engine
            final double initialStepBoundFactor;
            if (! parser.containsKey(ParameterKey.ESTIMATOR_LEVENBERG_MARQUARDT_INITIAL_STEP_BOUND_FACTOR)) {
                initialStepBoundFactor = 100.0;
            } else {
                initialStepBoundFactor = parser.getDouble(ParameterKey.ESTIMATOR_LEVENBERG_MARQUARDT_INITIAL_STEP_BOUND_FACTOR);
            }

            optimizer = new LevenbergMarquardtOptimizer().withInitialStepBoundFactor(initialStepBoundFactor);
        } else {
            // we want to use a Gauss-Newton optimization engine
            optimizer = new GaussNewtonOptimizer(new QRDecomposer(1e-11), false);
        }

        final double convergenceThreshold;
        if (! parser.containsKey(ParameterKey.ESTIMATOR_NORMALIZED_PARAMETERS_CONVERGENCE_THRESHOLD)) {
            convergenceThreshold = 1.0e-3;
        } else {
            convergenceThreshold = parser.getDouble(ParameterKey.ESTIMATOR_NORMALIZED_PARAMETERS_CONVERGENCE_THRESHOLD);
        }
        final int maxIterations;
        if (! parser.containsKey(ParameterKey.ESTIMATOR_MAX_ITERATIONS)) {
            maxIterations = 10;
        } else {
            maxIterations = parser.getInt(ParameterKey.ESTIMATOR_MAX_ITERATIONS);
        }
        final int maxEvaluations;
        if (! parser.containsKey(ParameterKey.ESTIMATOR_MAX_EVALUATIONS)) {
            maxEvaluations = 20;
        } else {
            maxEvaluations = parser.getInt(ParameterKey.ESTIMATOR_MAX_EVALUATIONS);
        }

        final BatchLSEstimator estimator = new BatchLSEstimator(optimizer, propagatorBuilder);
        estimator.setParametersConvergenceThreshold(convergenceThreshold);
        estimator.setMaxIterations(maxIterations);
        estimator.setMaxEvaluations(maxEvaluations);

        return estimator;

    }

    /** Read a RINEX measurements file.
     * @param file measurements file
     * @param satId satellite we are interested in
     * @param stations name to stations data map
     * @param satellite satellite reference
     * @param satRangeBias range bias due to transponder delay
     * @param satAntennaRangeModifier modifier for on-board antenna offset
     * @param weights base weights for measurements
     * @param rangeOutliersManager manager for range measurements outliers (null if none configured)
     * @param rangeRateOutliersManager manager for range-rate measurements outliers (null if none configured)
     * @return measurements list
     */
    private List<ObservedMeasurement<?>> readRinex(final File file, final String satId,
                                                   final Map<String, StationData> stations,
                                                   final ObservableSatellite satellite,
                                                   final Bias<Range> satRangeBias,
                                                   final OnBoardAntennaRangeModifier satAntennaRangeModifier,
                                                   final Weights weights,
                                                   final OutlierFilter<Range> rangeOutliersManager,
                                                   final OutlierFilter<RangeRate> rangeRateOutliersManager)
        throws UnsupportedEncodingException, IOException, OrekitException {
        final List<ObservedMeasurement<?>> measurements = new ArrayList<ObservedMeasurement<?>>();
        final SatelliteSystem system = SatelliteSystem.parseSatelliteSystem(satId);
        final int prnNumber;
        switch (system) {
            case GPS:
            case GLONASS:
            case GALILEO:
                prnNumber = Integer.parseInt(satId.substring(1));
                break;
            case SBAS:
                prnNumber = Integer.parseInt(satId.substring(1)) + 100;
                break;
            default:
                prnNumber = -1;
        }
        final Iono iono = new Iono(false);
        final RinexLoader loader = new RinexLoader(new FileInputStream(file), file.getAbsolutePath());
        for (final ObservationDataSet observationDataSet : loader.getObservationDataSets()) {
            if (observationDataSet.getSatelliteSystem() == system    &&
                observationDataSet.getPrnNumber()       == prnNumber) {
                for (final ObservationData od : observationDataSet.getObservationData()) {
                    if (!Double.isNaN(od.getValue())) {
                        if (od.getObservationType().getMeasurementType() == MeasurementType.PSEUDO_RANGE) {
                            // this is a measurement we want
                            final String stationName = observationDataSet.getHeader().getMarkerName() + "/" + od.getObservationType();
                            StationData stationData = stations.get(stationName);
                            if (stationData == null) {
                                throw new OrekitException(LocalizedCoreFormats.SIMPLE_MESSAGE,
                                                          stationName + " not configured");
                            }
                            Range range = new Range(stationData.station, false, observationDataSet.getDate(),
                                                    od.getValue(), stationData.rangeSigma,
                                                    weights.rangeBaseWeight, satellite);
                            range.addModifier(iono.getRangeModifier(od.getObservationType().getFrequency(system),
                                                                    observationDataSet.getDate()));
                            if (satAntennaRangeModifier != null) {
                                range.addModifier(satAntennaRangeModifier);
                            }
                            if (stationData.rangeBias != null) {
                                range.addModifier(stationData.rangeBias);
                            }
                            if (satRangeBias != null) {
                                range.addModifier(satRangeBias);
                            }
                            if (stationData.rangeTroposphericCorrection != null) {
                                range.addModifier(stationData.rangeTroposphericCorrection);
                            }
                            addIfNonZeroWeight(range, measurements);

                        } else if (od.getObservationType().getMeasurementType() == MeasurementType.DOPPLER) {
                            // this is a measurement we want
                            final String stationName = observationDataSet.getHeader().getMarkerName() + "/" + od.getObservationType();
                            StationData stationData = stations.get(stationName);
                            if (stationData == null) {
                                throw new OrekitException(LocalizedCoreFormats.SIMPLE_MESSAGE,
                                                          stationName + " not configured");
                            }
                            RangeRate rangeRate = new RangeRate(stationData.station, observationDataSet.getDate(),
                                                                od.getValue(), stationData.rangeRateSigma,
                                                                weights.rangeRateBaseWeight, false, satellite);
                            rangeRate.addModifier(iono.getRangeRateModifier(od.getObservationType().getFrequency(system),
                                                                            observationDataSet.getDate()));
                            if (stationData.rangeRateBias != null) {
                                rangeRate.addModifier(stationData.rangeRateBias);
                            }
                            addIfNonZeroWeight(rangeRate, measurements);
                        }
                    }
                }
            }
        }

        return measurements;

    }

    /** Read a measurements file.
     * @param file measurements file
     * @param stations name to stations data map
     * @param pvData PV measurements data
     * @param satellite satellite reference
     * @param satRangeBias range bias due to transponder delay
     * @param satAntennaRangeModifier modifier for on-board antenna offset
     * @param weights base weights for measurements
     * @param rangeOutliersManager manager for range measurements outliers (null if none configured)
     * @param rangeRateOutliersManager manager for range-rate measurements outliers (null if none configured)
     * @param azElOutliersManager manager for azimuth-elevation measurements outliers (null if none configured)
     * @param pvOutliersManager manager for PV measurements outliers (null if none configured)
     * @return measurements list
     */
    private List<ObservedMeasurement<?>> readMeasurements(final File file,
                                                          final Map<String, StationData> stations,
                                                          final PVData pvData,
                                                          final ObservableSatellite satellite,
                                                          final Bias<Range> satRangeBias,
                                                          final OnBoardAntennaRangeModifier satAntennaRangeModifier,
                                                          final Weights weights,
                                                          final OutlierFilter<Range> rangeOutliersManager,
                                                          final OutlierFilter<RangeRate> rangeRateOutliersManager,
                                                          final OutlierFilter<AngularAzEl> azElOutliersManager,
                                                          final OutlierFilter<PV> pvOutliersManager)
        throws UnsupportedEncodingException, IOException, OrekitException {

        final List<ObservedMeasurement<?>> measurements = new ArrayList<ObservedMeasurement<?>>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            int lineNumber = 0;
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                ++lineNumber;
                line = line.trim();
                if (line.length() > 0 && !line.startsWith("#")) {
                    String[] fields = line.split("\\s+");
                    if (fields.length < 2) {
                        throw new OrekitException(OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE,
                                                  lineNumber, file.getName(), line);
                    }
                    switch (fields[1]) {
                        case "RANGE" :
                            final Range range = new RangeParser().parseFields(fields, stations, pvData, satellite,
                                                                              satRangeBias, weights,
                                                                              line, lineNumber, file.getName());
                            if (satAntennaRangeModifier != null) {
                                range.addModifier(satAntennaRangeModifier);
                            }
                            if (rangeOutliersManager != null) {
                                range.addModifier(rangeOutliersManager);
                            }
                            addIfNonZeroWeight(range, measurements);
                            break;
                        case "RANGE_RATE" :
                            final RangeRate rangeRate = new RangeRateParser().parseFields(fields, stations, pvData, satellite,
                                                                                          satRangeBias, weights,
                                                                                          line, lineNumber, file.getName());
                            if (rangeOutliersManager != null) {
                                rangeRate.addModifier(rangeRateOutliersManager);
                            }
                            addIfNonZeroWeight(rangeRate, measurements);
                            break;
                        case "AZ_EL" :
                            final AngularAzEl angular = new AzElParser().parseFields(fields, stations, pvData, satellite,
                                                                                 satRangeBias, weights,
                                                                                 line, lineNumber, file.getName());
                            if (azElOutliersManager != null) {
                                angular.addModifier(azElOutliersManager);
                            }
                            addIfNonZeroWeight(angular, measurements);
                            break;
                        case "PV" :
                            final PV pv = new PVParser().parseFields(fields, stations, pvData, satellite,
                                                                     satRangeBias, weights,
                                                                     line, lineNumber, file.getName());
                            if (pvOutliersManager != null) {
                                pv.addModifier(pvOutliersManager);
                            }
                            addIfNonZeroWeight(pv, measurements);
                            break;
                        default :
                            throw new OrekitException(LocalizedCoreFormats.SIMPLE_MESSAGE,
                                                      "unknown measurement type " + fields[1] +
                                                      " at line " + lineNumber +
                                                      " in file " + file.getName());
                    }
                }
            }
        } finally {
            if (br != null) {
                br.close();
            }
        }

        if (measurements.isEmpty()) {
            throw new OrekitException(LocalizedCoreFormats.SIMPLE_MESSAGE,
                                      "not measurements read from file " + file.getAbsolutePath());
        }

        return measurements;

    }

    /** Add a measurement to a list if it has non-zero weight.
     * @param measurement measurement to add
     * @param measurements measurements list
     */
    private void addIfNonZeroWeight(final ObservedMeasurement<?> measurement, final List<ObservedMeasurement<?>> measurements) {
        double sum = 0;
        for (double w : measurement.getBaseWeight()) {
            sum += FastMath.abs(w);
        }
        if (sum > Precision.SAFE_MIN) {
            // we only consider measurements with non-zero weight
            measurements.add(measurement);
        }
    }

    /** Container for stations-related data. */
    private static class StationData {

        /** Ground station. */
        private final GroundStation station;

        /** Range sigma. */
        private final double rangeSigma;

        /** Range bias (may be if bias is fixed to zero). */
        private final Bias<Range> rangeBias;

        /** Range rate sigma. */
        private final double rangeRateSigma;

        /** Range rate bias (may be null if bias is fixed to zero). */
        private final Bias<RangeRate> rangeRateBias;

        /** Azimuth-elevation sigma. */
        private final double[] azElSigma;

        /** Azimuth-elevation bias (may be null if bias is fixed to zero). */
        private final Bias<AngularAzEl> azELBias;

        /** Elevation refraction correction (may be null). */
        private final AngularRadioRefractionModifier refractionCorrection;

        /** Tropospheric correction (may be null). */
        private final RangeTroposphericDelayModifier rangeTroposphericCorrection;

        /** Simple constructor.
         * @param station ground station
         * @param rangeSigma range sigma
         * @param rangeBias range bias (may be null if bias is fixed to zero)
         * @param rangeRateSigma range rate sigma
         * @param rangeRateBias range rate bias (may be null if bias is fixed to zero)
         * @param azElSigma azimuth-elevation sigma
         * @param azELBias azimuth-elevation bias (may be null if bias is fixed to zero)
         * @param refractionCorrection refraction correction for elevation (may be null)
         * @param rangeTroposphericCorrection tropospheric correction  for the range (may be null)
         */
        public StationData(final GroundStation station,
                           final double rangeSigma, final Bias<Range> rangeBias,
                           final double rangeRateSigma, final Bias<RangeRate> rangeRateBias,
                           final double[] azElSigma, final Bias<AngularAzEl> azELBias,
                           final AngularRadioRefractionModifier refractionCorrection,
                           final RangeTroposphericDelayModifier rangeTroposphericCorrection) {
            this.station                     = station;
            this.rangeSigma                  = rangeSigma;
            this.rangeBias                   = rangeBias;
            this.rangeRateSigma              = rangeRateSigma;
            this.rangeRateBias               = rangeRateBias;
            this.azElSigma                   = azElSigma.clone();
            this.azELBias                    = azELBias;
            this.refractionCorrection        = refractionCorrection;
            this.rangeTroposphericCorrection = rangeTroposphericCorrection;
        }

    }

    /** Container for base weights. */
    private static class Weights {

        /** Base weight for range measurements. */
        private final double rangeBaseWeight;

        /** Base weight for range rate measurements. */
        private final double rangeRateBaseWeight;

        /** Base weight for azimuth-elevation measurements. */
        private final double[] azElBaseWeight;

        /** Base weight for PV measurements. */
        private final double pvBaseWeight;

        /** Simple constructor.
         * @param rangeBaseWeight base weight for range measurements
         * @param rangeRateBaseWeight base weight for range rate measurements
         * @param azElBaseWeight base weight for azimuth-elevation measurements
         * @param pvBaseWeight base weight for PV measurements
         */
        public Weights(final double rangeBaseWeight,
                       final double rangeRateBaseWeight,
                       final double[] azElBaseWeight,
                       final double pvBaseWeight) {
            this.rangeBaseWeight     = rangeBaseWeight;
            this.rangeRateBaseWeight = rangeRateBaseWeight;
            this.azElBaseWeight      = azElBaseWeight.clone();
            this.pvBaseWeight        = pvBaseWeight;
        }

    }

    /** Container for Position-velocity data. */
    private static class PVData {

        /** Position sigma. */
        private final double positionSigma;

        /** Velocity sigma. */
        private final double velocitySigma;

        /** Simple constructor.
         * @param positionSigma position sigma
         * @param velocitySigma velocity sigma
         */
        public PVData(final double positionSigma, final double velocitySigma) {
            this.positionSigma = positionSigma;
            this.velocitySigma = velocitySigma;
        }

    }

    /** Measurements types. */
    private static abstract class MeasurementsParser<T extends ObservedMeasurement<T>> {

        /** Parse the fields of a measurements line.
         * @param fields measurements line fields
         * @param stations name to stations data map
         * @param pvData PV measurements data
         * @param satellite satellite reference
         * @param satRangeBias range bias due to transponder delay
         * @param weight base weights for measurements
         * @param line complete line
         * @param lineNumber line number
         * @param fileName file name
         * @return parsed measurement
         */
        public abstract T parseFields(String[] fields,
                                      Map<String, StationData> stations,
                                      PVData pvData, ObservableSatellite satellite,
                                      Bias<Range> satRangeBias, Weights weight,
                                      String line, int lineNumber, String fileName);

        /** Check the number of fields.
         * @param expected expected number of fields
         * @param fields measurements line fields
         * @param line complete line
         * @param lineNumber line number
         * @param fileName file name
         */
        protected void checkFields(final int expected, final String[] fields,
                                   final String line, final int lineNumber, final String fileName) {
            if (fields.length != expected) {
                throw new OrekitException(OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE,
                                          lineNumber, fileName, line);
            }
        }

        /** Get the date for the line.
         * @param date date field
         * @param line complete line
         * @param lineNumber line number
         * @param fileName file name
         * @return parsed measurement
         */
        protected AbsoluteDate getDate(final String date,
                                       final String line, final int lineNumber, final String fileName) {
            try {
                return new AbsoluteDate(date, TimeScalesFactory.getUTC());
            } catch (OrekitException oe) {
                throw new OrekitException(LocalizedCoreFormats.SIMPLE_MESSAGE,
                                          "wrong date " + date +
                                          " at line " + lineNumber +
                                          " in file " + fileName +
                                          "\n" + line);
            }
        }

        /** Get the station data for the line.
         * @param stationName name of the station
         * @param stations name to stations data map
         * @param line complete line
         * @param lineNumber line number
         * @param fileName file name
         * @return parsed measurement
         */
        protected StationData getStationData(final String stationName,
                                             final Map<String, StationData> stations,
                                             final String line, final int lineNumber, final String fileName) {
            final StationData stationData = stations.get(stationName);
            if (stationData == null) {
                throw new OrekitException(LocalizedCoreFormats.SIMPLE_MESSAGE,
                                          "unknown station " + stationName +
                                          " at line " + lineNumber +
                                          " in file " + fileName +
                                          "\n" + line);
            }
            return stationData;
        }
    }

    /** Parser for range measurements. */
    private static class RangeParser extends MeasurementsParser<Range> {
        /** {@inheritDoc} */
        @Override
        public Range parseFields(final String[] fields,
                                 final Map<String, StationData> stations,
                                 final PVData pvData,
                                 final ObservableSatellite satellite,
                                 final Bias<Range> satRangeBias,
                                 final Weights weights,
                                 final String line,
                                 final int lineNumber,
                                 final String fileName) {
            checkFields(4, fields, line, lineNumber, fileName);
            final StationData stationData = getStationData(fields[2], stations, line, lineNumber, fileName);
            final Range range = new Range(stationData.station, true,
                                          getDate(fields[0], line, lineNumber, fileName),
                                          Double.parseDouble(fields[3]) * 1000.0,
                                          stationData.rangeSigma,
                                          weights.rangeBaseWeight,
                                          satellite);
            if (stationData.rangeBias != null) {
                range.addModifier(stationData.rangeBias);
            }
            if (satRangeBias != null) {
                range.addModifier(satRangeBias);
            }
            if (stationData.rangeTroposphericCorrection != null) {
                range.addModifier(stationData.rangeTroposphericCorrection);
            }
            return range;
        }
    }

    /** Parser for range rate measurements. */
    private static class RangeRateParser extends MeasurementsParser<RangeRate> {
        /** {@inheritDoc} */
        @Override
        public RangeRate parseFields(final String[] fields,
                                     final Map<String, StationData> stations,
                                     final PVData pvData,
                                     final ObservableSatellite satellite,
                                     final Bias<Range> satRangeBias,
                                     final Weights weights,
                                     final String line,
                                     final int lineNumber,
                                     final String fileName) {
            checkFields(4, fields, line, lineNumber, fileName);
            final StationData stationData = getStationData(fields[2], stations, line, lineNumber, fileName);
            final RangeRate rangeRate = new RangeRate(stationData.station,
                                                      getDate(fields[0], line, lineNumber, fileName),
                                                      Double.parseDouble(fields[3]) * 1000.0,
                                                      stationData.rangeRateSigma,
                                                      weights.rangeRateBaseWeight,
                                                      true, satellite);
            if (stationData.rangeRateBias != null) {
                rangeRate.addModifier(stationData.rangeRateBias);
            }
            return rangeRate;
        }
    };

    /** Parser for azimuth-elevation measurements. */
    private static class AzElParser extends MeasurementsParser<AngularAzEl> {
        /** {@inheritDoc} */
        @Override
        public AngularAzEl parseFields(final String[] fields,
                                   final Map<String, StationData> stations,
                                   final PVData pvData,
                                   final ObservableSatellite satellite,
                                   final Bias<Range> satRangeBias,
                                   final Weights weights,
                                   final String line,
                                   final int lineNumber,
                                   final String fileName) {
            checkFields(5, fields, line, lineNumber, fileName);
            final StationData stationData = getStationData(fields[2], stations, line, lineNumber, fileName);
            final AngularAzEl azEl = new AngularAzEl(stationData.station,
                                             getDate(fields[0], line, lineNumber, fileName),
                                             new double[] {
                                                           FastMath.toRadians(Double.parseDouble(fields[3])),
                                                           FastMath.toRadians(Double.parseDouble(fields[4]))
                                             },
                                             stationData.azElSigma,
                                             weights.azElBaseWeight,
                                             satellite);
            if (stationData.refractionCorrection != null) {
                azEl.addModifier(stationData.refractionCorrection);
            }
            if (stationData.azELBias != null) {
                azEl.addModifier(stationData.azELBias);
            }
            return azEl;
        }
    };

    /** Parser for PV measurements. */
    private static class PVParser extends MeasurementsParser<PV> {
        /** {@inheritDoc} */
        @Override
        public PV parseFields(final String[] fields,
                              final Map<String, StationData> stations,
                              final PVData pvData,
                              final ObservableSatellite satellite,
                              final Bias<Range> satRangeBias,
                              final Weights weights,
                              final String line,
                              final int lineNumber,
                              final String fileName) {
            // field 2, which corresponds to stations in other measurements, is ignored
            // this allows the measurements files to be columns aligned
            // by inserting something like "----" instead of a station name
            checkFields(9, fields, line, lineNumber, fileName);
            return new org.orekit.estimation.measurements.PV(getDate(fields[0], line, lineNumber, fileName),
                                                             new Vector3D(Double.parseDouble(fields[3]) * 1000.0,
                                                                          Double.parseDouble(fields[4]) * 1000.0,
                                                                          Double.parseDouble(fields[5]) * 1000.0),
                                                             new Vector3D(Double.parseDouble(fields[6]) * 1000.0,
                                                                          Double.parseDouble(fields[7]) * 1000.0,
                                                                          Double.parseDouble(fields[8]) * 1000.0),
                                                             pvData.positionSigma,
                                                             pvData.velocitySigma,
                                                             weights.pvBaseWeight,
                                                             satellite);
        }
    };

    /** Local class for measurement-specific log.
     * @param T type of mesurement
     */
    private static abstract class MeasurementLog<T extends ObservedMeasurement<T>> {

        /** Residuals. */
        private final SortedSet<EstimatedMeasurement<T>> evaluations;

        /** Measurements name. */
        private final String name;


        /** Simple constructor.
         * @param name measurement name
         * @exception IOException if output file cannot be created
         */
        MeasurementLog(final String name) throws IOException {
            this.evaluations = new TreeSet<EstimatedMeasurement<T>>(Comparator.naturalOrder());
            this.name        = name;
        }

        /** Get the measurement name.
         * @return measurement name
         */
        public String getName() {
            return name;
        }

        /** Compute residual value.
         * @param evaluation evaluation to consider
         */
        abstract double residual(final EstimatedMeasurement<T> evaluation);

        /** Add an evaluation.
         * @param evaluation evaluation to add
         */
        void add(final EstimatedMeasurement<T> evaluation) {
            evaluations.add(evaluation);
        }

        /**Create a  statistics summary
         */
        public StreamingStatistics createStatisticsSummary() {
            if (!evaluations.isEmpty()) {
                // compute statistics
                final StreamingStatistics stats = new StreamingStatistics();
                for (final EstimatedMeasurement<T> evaluation : evaluations) {
                    stats.addValue(residual(evaluation));
                }
               return stats;

            }
            return null;
        }
    }

    /** Logger for range measurements. */
    class RangeLog extends MeasurementLog<Range> {

        /** Simple constructor.
         * @exception IOException if output file cannot be created
         */
        RangeLog() throws IOException {
            super("range");
        }

        /** {@inheritDoc} */
        @Override
        double residual(final EstimatedMeasurement<Range> evaluation) {
            return evaluation.getEstimatedValue()[0] - evaluation.getObservedMeasurement().getObservedValue()[0];
        }

    }
    
    /** Logger for range rate measurements. */
    class RangeRateLog extends MeasurementLog<RangeRate> {

        /** Simple constructor.
         * @exception IOException if output file cannot be created
         */
        RangeRateLog() throws IOException {
            super("range-rate");
        }

        /** {@inheritDoc} */
        @Override
        double residual(final EstimatedMeasurement<RangeRate> evaluation) {
            return evaluation.getEstimatedValue()[0] - evaluation.getObservedMeasurement().getObservedValue()[0];
        }

    }

    /** Logger for azimuth measurements. */
    class AzimuthLog extends MeasurementLog<AngularAzEl> {

        /** Simple constructor.
         * @exception IOException if output file cannot be created
         */
        AzimuthLog() throws IOException {
            super("azimuth");
        }

        /** {@inheritDoc} */
        @Override
        double residual(final EstimatedMeasurement<AngularAzEl> evaluation) {
            return FastMath.toDegrees(evaluation.getEstimatedValue()[0] - evaluation.getObservedMeasurement().getObservedValue()[0]);
        }

    }

    /** Logger for elevation measurements. */
    class ElevationLog extends MeasurementLog<AngularAzEl> {

        /** Simple constructor.
         * @param home home directory
         * @param baseName output file base name (may be null)
         * @exception IOException if output file cannot be created
         */
        ElevationLog() throws IOException {
            super("elevation");
        }

        /** {@inheritDoc} */
        @Override
        double residual(final EstimatedMeasurement<AngularAzEl> evaluation) {
            return FastMath.toDegrees(evaluation.getEstimatedValue()[1] - evaluation.getObservedMeasurement().getObservedValue()[1]);
        }

    }

    /** Logger for position measurements. */
    class PositionLog extends MeasurementLog<PV> {

        /** Simple constructor.
         * @param home home directory
         * @param baseName output file base name (may be null)
         * @exception IOException if output file cannot be created
         */
        PositionLog() throws IOException  {
            super("position");
        }

        /** {@inheritDoc} */
        @Override
        double residual(final EstimatedMeasurement<PV> evaluation) {
            final double[] theoretical = evaluation.getEstimatedValue();
            final double[] observed    = evaluation.getObservedMeasurement().getObservedValue();
            return Vector3D.distance(new Vector3D(theoretical[0], theoretical[1], theoretical[2]),
                                     new Vector3D(observed[0],    observed[1],    observed[2]));
        }

    }

    /** Logger for velocity measurements. */
    class VelocityLog extends MeasurementLog<PV> {

        /** Simple constructor.
         * @param home home directory
         * @param baseName output file base name (may be null)
         * @exception IOException if output file cannot be created
         */
        VelocityLog() throws IOException {
            super("velocity");
        }

        /** {@inheritDoc} */
        @Override
        double residual(final EstimatedMeasurement<PV> evaluation) {
            final double[] theoretical = evaluation.getEstimatedValue();
            final double[] observed    = evaluation.getObservedMeasurement().getObservedValue();
            return Vector3D.distance(new Vector3D(theoretical[3], theoretical[4], theoretical[5]),
                                     new Vector3D(observed[3],    observed[4],    observed[5]));
        }

    }

    private static class EvaluationCounter<T extends ObservedMeasurement<T>> {

        /** Total number of measurements. */
        private int total;

        /** Number of active (i.e. positive weight) measurements. */
        private int active;

        /** Add a measurement evaluation.
         * @param evaluation measurement evaluation to add
         */
        public void add(EstimatedMeasurement<T> evaluation) {
            ++total;
            if (evaluation.getStatus() == EstimatedMeasurement.Status.PROCESSED) {
                ++active;
            }
        }

        /** Format an active/total count.
         * @param size field minimum size
         */
        public String format(final int size) {
            StringBuilder builder = new StringBuilder();
            builder.append(active);
            builder.append('/');
            builder.append(total);
            while (builder.length() < size) {
                if (builder.length() % 2 == 0) {
                    builder.insert(0, ' ');
                } else {
                    builder.append(' ');
                }
            }
            return builder.toString();
        }

    }

    /** Ionospheric modifiers. */
    private static class Iono {

        /** Flag for two-way range-rate. */
        private final boolean twoWay;

        /** Map for range modifiers. */
        private final Map<Frequency, Map<DateComponents, RangeIonosphericDelayModifier>> rangeModifiers;

        /** Map for range-rate modifiers. */
        private final Map<Frequency, Map<DateComponents, RangeRateIonosphericDelayModifier>> rangeRateModifiers;

        /** Simple constructor.
         * @param twoWay flag for two-way range-rate
         */
        Iono(final boolean twoWay) {
            this.twoWay             = twoWay;
            this.rangeModifiers     = new HashMap<>();
            this.rangeRateModifiers = new HashMap<>();
        }

        /** Get range modifier for a measurement.
         * @param frequency frequency of the signal
         * @param date measurement date
         * @return range modifier
         */
        public RangeIonosphericDelayModifier getRangeModifier(final Frequency frequency,
                                                              final AbsoluteDate date) {
            final DateComponents dc = date.getComponents(TimeScalesFactory.getUTC()).getDate();
            ensureFrequencyAndDateSupported(frequency, dc);
            return rangeModifiers.get(frequency).get(dc);
        }

        /** Get range-rate modifier for a measurement.
         * @param frequency frequency of the signal
         * @param date measurement date
         * @return range-rate modifier
         */
        public RangeRateIonosphericDelayModifier getRangeRateModifier(final Frequency frequency,
                                                                      final AbsoluteDate date) {
            final DateComponents dc = date.getComponents(TimeScalesFactory.getUTC()).getDate();
            ensureFrequencyAndDateSupported(frequency, dc);
            return rangeRateModifiers.get(frequency).get(dc);
         }

        /** Create modifiers for a frequency and date if needed.
         * @param frequency frequency of the signal
         * @param dc date for which modifiers are required
         */
        private void ensureFrequencyAndDateSupported(final Frequency frequency, final DateComponents dc) {

            if (!rangeModifiers.containsKey(frequency)) {
                rangeModifiers.put(frequency, new HashMap<>());
                rangeRateModifiers.put(frequency, new HashMap<>());
            }

            if (!rangeModifiers.get(frequency).containsKey(dc)) {

                // load Klobuchar model for the L1 frequency
                final KlobucharIonoCoefficientsLoader loader = new KlobucharIonoCoefficientsLoader();
                loader.loadKlobucharIonosphericCoefficients(dc);
                final IonosphericModel l1Model   = new KlobucharIonoModel(loader.getAlpha(), loader.getBeta());

                // scale for current frequency
                final double fL1   = Frequency.G01.getMHzFrequency();
                final double f     = frequency.getMHzFrequency();
                final double ratio = (fL1 * fL1) / (f * f);
                final IonosphericModel scaledModel = (date, geo, elevation, azimuth) ->
                                                     ratio * l1Model.pathDelay(date, geo, elevation, azimuth);

                // create modifiers
                rangeModifiers.get(frequency).put(dc, new RangeIonosphericDelayModifier(scaledModel));
                rangeRateModifiers.get(frequency).put(dc, new RangeRateIonosphericDelayModifier(scaledModel, twoWay));

            }

        }

    }

    /** Attitude modes. */
    private static enum AttitudeMode {
        NADIR_POINTING_WITH_YAW_COMPENSATION() {
            public AttitudeProvider getProvider(final Frame inertialFrame, final OneAxisEllipsoid body) {
                return new YawCompensation(inertialFrame, new NadirPointing(inertialFrame, body));
            }
        },
        CENTER_POINTING_WITH_YAW_STEERING {
            public AttitudeProvider getProvider(final Frame inertialFrame, final OneAxisEllipsoid body) {
                return new YawSteering(inertialFrame,
                                       new BodyCenterPointing(inertialFrame, body),
                                       CelestialBodyFactory.getSun(),
                                       Vector3D.PLUS_I);
            }
        },
        LOF_ALIGNED_LVLH {
            public AttitudeProvider getProvider(final Frame inertialFrame, final OneAxisEllipsoid body) {
                return new LofOffset(inertialFrame, LOFType.LVLH);
            }
        },
        LOF_ALIGNED_QSW {
            public AttitudeProvider getProvider(final Frame inertialFrame, final OneAxisEllipsoid body) {
                return new LofOffset(inertialFrame, LOFType.QSW);
            }
        },
        LOF_ALIGNED_TNW {
            public AttitudeProvider getProvider(final Frame inertialFrame, final OneAxisEllipsoid body) {
                return new LofOffset(inertialFrame, LOFType.TNW);
            }
        },
        LOF_ALIGNED_VNC {
            public AttitudeProvider getProvider(final Frame inertialFrame, final OneAxisEllipsoid body) {
                return new LofOffset(inertialFrame, LOFType.VNC);
            }
        },
        LOF_ALIGNED_VVLH {
            public AttitudeProvider getProvider(final Frame inertialFrame, final OneAxisEllipsoid body) {
                return new LofOffset(inertialFrame, LOFType.VVLH);
            }
        };

        public abstract AttitudeProvider getProvider(final Frame inertialFrame, final OneAxisEllipsoid body);

    }

    /** Input parameter keys. */
    private static enum ParameterKey {
        ORBIT_DATE,
        ORBIT_CIRCULAR_A,
        ORBIT_CIRCULAR_EX,
        ORBIT_CIRCULAR_EY,
        ORBIT_CIRCULAR_I,
        ORBIT_CIRCULAR_RAAN,
        ORBIT_CIRCULAR_ALPHA,
        ORBIT_EQUINOCTIAL_A,
        ORBIT_EQUINOCTIAL_EX,
        ORBIT_EQUINOCTIAL_EY,
        ORBIT_EQUINOCTIAL_HX,
        ORBIT_EQUINOCTIAL_HY,
        ORBIT_EQUINOCTIAL_LAMBDA,
        ORBIT_KEPLERIAN_A,
        ORBIT_KEPLERIAN_E,
        ORBIT_KEPLERIAN_I,
        ORBIT_KEPLERIAN_PA,
        ORBIT_KEPLERIAN_RAAN,
        ORBIT_KEPLERIAN_ANOMALY,
        ORBIT_ANGLE_TYPE,
        ORBIT_TLE_LINE_1,
        ORBIT_TLE_LINE_2,
        ORBIT_CARTESIAN_PX,
        ORBIT_CARTESIAN_PY,
        ORBIT_CARTESIAN_PZ,
        ORBIT_CARTESIAN_VX,
        ORBIT_CARTESIAN_VY,
        ORBIT_CARTESIAN_VZ,
        MASS,
        IERS_CONVENTIONS,
        INERTIAL_FRAME,
        PROPAGATOR_MIN_STEP,
        PROPAGATOR_MAX_STEP,
        PROPAGATOR_POSITION_ERROR,
        BODY_FRAME,
        BODY_EQUATORIAL_RADIUS,
        BODY_INVERSE_FLATTENING,
        CENTRAL_BODY_DEGREE,
        CENTRAL_BODY_ORDER,
        OCEAN_TIDES_DEGREE,
        OCEAN_TIDES_ORDER,
        SOLID_TIDES_SUN,
        SOLID_TIDES_MOON,
        THIRD_BODY_SUN,
        THIRD_BODY_MOON,
        DRAG,
        DRAG_CD,
        DRAG_CD_ESTIMATED,
        DRAG_AREA,
        SOLAR_RADIATION_PRESSURE,
        SOLAR_RADIATION_PRESSURE_CR,
        SOLAR_RADIATION_PRESSURE_CR_ESTIMATED,
        SOLAR_RADIATION_PRESSURE_AREA,
        GENERAL_RELATIVITY,
        ATTITUDE_MODE,
        POLYNOMIAL_ACCELERATION_NAME,
        POLYNOMIAL_ACCELERATION_DIRECTION_X,
        POLYNOMIAL_ACCELERATION_DIRECTION_Y,
        POLYNOMIAL_ACCELERATION_DIRECTION_Z,
        POLYNOMIAL_ACCELERATION_COEFFICIENTS,
        POLYNOMIAL_ACCELERATION_ESTIMATED,
        ONBOARD_RANGE_BIAS,
        ONBOARD_RANGE_BIAS_MIN,
        ONBOARD_RANGE_BIAS_MAX,
        ONBOARD_RANGE_BIAS_ESTIMATED,
        ON_BOARD_ANTENNA_PHASE_CENTER_X,
        ON_BOARD_ANTENNA_PHASE_CENTER_Y,
        ON_BOARD_ANTENNA_PHASE_CENTER_Z,
        ON_BOARD_CLOCK_OFFSET,
        ON_BOARD_CLOCK_OFFSET_MIN,
        ON_BOARD_CLOCK_OFFSET_MAX,
        ON_BOARD_CLOCK_OFFSET_ESTIMATED,
        GROUND_STATION_NAME,
        GROUND_STATION_LATITUDE,
        GROUND_STATION_LONGITUDE,
        GROUND_STATION_ALTITUDE,
        GROUND_STATION_POSITION_ESTIMATED,
        GROUND_STATION_CLOCK_OFFSET,
        GROUND_STATION_CLOCK_OFFSET_MIN,
        GROUND_STATION_CLOCK_OFFSET_MAX,
        GROUND_STATION_CLOCK_OFFSET_ESTIMATED,
        GROUND_STATION_TROPOSPHERIC_MODEL_ESTIMATED,
        GROUND_STATION_TROPOSPHERIC_ZENITH_DELAY,
        GROUND_STATION_TROPOSPHERIC_DELAY_ESTIMATED,
        GROUND_STATION_GLOBAL_MAPPING_FUNCTION,
        GROUND_STATION_NIELL_MAPPING_FUNCTION,
        GROUND_STATION_WEATHER_ESTIMATED,
        GROUND_STATION_RANGE_SIGMA,
        GROUND_STATION_RANGE_BIAS,
        GROUND_STATION_RANGE_BIAS_MIN,
        GROUND_STATION_RANGE_BIAS_MAX,
        GROUND_STATION_RANGE_BIAS_ESTIMATED,
        GROUND_STATION_RANGE_RATE_SIGMA,
        GROUND_STATION_RANGE_RATE_BIAS,
        GROUND_STATION_RANGE_RATE_BIAS_MIN,
        GROUND_STATION_RANGE_RATE_BIAS_MAX,
        GROUND_STATION_RANGE_RATE_BIAS_ESTIMATED,
        GROUND_STATION_AZIMUTH_SIGMA,
        GROUND_STATION_AZIMUTH_BIAS,
        GROUND_STATION_AZIMUTH_BIAS_MIN,
        GROUND_STATION_AZIMUTH_BIAS_MAX,
        GROUND_STATION_ELEVATION_SIGMA,
        GROUND_STATION_ELEVATION_BIAS,
        GROUND_STATION_ELEVATION_BIAS_MIN,
        GROUND_STATION_ELEVATION_BIAS_MAX,
        GROUND_STATION_AZ_EL_BIASES_ESTIMATED,
        GROUND_STATION_ELEVATION_REFRACTION_CORRECTION,
        GROUND_STATION_RANGE_TROPOSPHERIC_CORRECTION,
        GROUND_STATION_IONOSPHERIC_CORRECTION,
        SOLID_TIDES_DISPLACEMENT_CORRECTION,
        SOLID_TIDES_DISPLACEMENT_REMOVE_PERMANENT_DEFORMATION,
        OCEAN_LOADING_CORRECTION,
        RANGE_MEASUREMENTS_BASE_WEIGHT,
        RANGE_RATE_MEASUREMENTS_BASE_WEIGHT,
        AZIMUTH_MEASUREMENTS_BASE_WEIGHT,
        ELEVATION_MEASUREMENTS_BASE_WEIGHT,
        PV_MEASUREMENTS_BASE_WEIGHT,
        PV_MEASUREMENTS_POSITION_SIGMA,
        PV_MEASUREMENTS_VELOCITY_SIGMA,
        RANGE_OUTLIER_REJECTION_MULTIPLIER,
        RANGE_OUTLIER_REJECTION_STARTING_ITERATION,
        RANGE_RATE_OUTLIER_REJECTION_MULTIPLIER,
        RANGE_RATE_OUTLIER_REJECTION_STARTING_ITERATION,
        AZ_EL_OUTLIER_REJECTION_MULTIPLIER,
        AZ_EL_OUTLIER_REJECTION_STARTING_ITERATION,
        PV_OUTLIER_REJECTION_MULTIPLIER,
        PV_OUTLIER_REJECTION_STARTING_ITERATION,
        SATELLITE_ID_IN_RINEX_FILES,
        MEASUREMENTS_FILES,
        OUTPUT_BASE_NAME,
        ESTIMATOR_OPTIMIZATION_ENGINE,
        ESTIMATOR_LEVENBERG_MARQUARDT_INITIAL_STEP_BOUND_FACTOR,
        ESTIMATOR_ORBITAL_PARAMETERS_POSITION_SCALE,
        ESTIMATOR_NORMALIZED_PARAMETERS_CONVERGENCE_THRESHOLD,
        ESTIMATOR_MAX_ITERATIONS,
        ESTIMATOR_MAX_EVALUATIONS;
    }
}