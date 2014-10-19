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
package org.orekit.utils;

import java.util.Collection;

import org.apache.commons.math3.analysis.differentiation.DerivativeStructure;
import org.apache.commons.math3.analysis.interpolation.HermiteInterpolator;
import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.MathArrays;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitMessages;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeStamped;

/** {@link TimeStamped time-stamped} version of {@link AngularCoordinates}.
 * <p>Instances of this class are guaranteed to be immutable.</p>
 * @author Luc Maisonobe
 * @since 7.0
 */
public class TimeStampedAngularCoordinates extends AngularCoordinates implements TimeStamped {

    /** Serializable UID. */
    private static final long serialVersionUID = 20140611L;

    /** The date. */
    private final AbsoluteDate date;

    /** Builds a rotation/rotation rate pair.
     * @param date coordinates date
     * @param rotation rotation
     * @param rotationRate rotation rate (rad/s)
     */
    public TimeStampedAngularCoordinates(final AbsoluteDate date,
                                         final Rotation rotation, final Vector3D rotationRate) {
        super(rotation, rotationRate);
        this.date = date;
    }

    /** {@inheritDoc} */
    public AbsoluteDate getDate() {
        return date;
    }

    /** Revert a rotation/rotation rate pair.
     * Build a pair which reverse the effect of another pair.
     * @return a new pair whose effect is the reverse of the effect
     * of the instance
     */
    public TimeStampedAngularCoordinates revert() {
        return new TimeStampedAngularCoordinates(date,
                                                 getRotation().revert(),
                                                 getRotation().applyInverseTo(getRotationRate().negate()));
    }

    /** Get a time-shifted state.
     * <p>
     * The state can be slightly shifted to close dates. This shift is based on
     * a simple linear model. It is <em>not</em> intended as a replacement for
     * proper attitude propagation but should be sufficient for either small
     * time shifts or coarse accuracy.
     * </p>
     * @param dt time shift in seconds
     * @return a new state, shifted with respect to the instance (which is immutable)
     */
    public TimeStampedAngularCoordinates shiftedBy(final double dt) {
        final AngularCoordinates sac = super.shiftedBy(dt);
        return new TimeStampedAngularCoordinates(date.shiftedBy(dt), sac.getRotation(), sac.getRotationRate());

    }

    /** Add an offset from the instance.
     * <p>
     * We consider here that the offset rotation is applied first and the
     * instance is applied afterward. Note that angular coordinates do <em>not</em>
     * commute under this operation, i.e. {@code a.addOffset(b)} and {@code
     * b.addOffset(a)} lead to <em>different</em> results in most cases.
     * </p>
     * <p>
     * The two methods {@link #addOffset(AngularCoordinates) addOffset} and
     * {@link #subtractOffset(AngularCoordinates) subtractOffset} are designed
     * so that round trip applications are possible. This means that both {@code
     * ac1.subtractOffset(ac2).addOffset(ac2)} and {@code
     * ac1.addOffset(ac2).subtractOffset(ac2)} return angular coordinates equal to ac1.
     * </p>
     * @param offset offset to subtract
     * @return new instance, with offset subtracted
     * @see #subtractOffset(AngularCoordinates)
     */
    @Override
    public TimeStampedAngularCoordinates addOffset(final AngularCoordinates offset) {
        return new TimeStampedAngularCoordinates(date,
                                                 getRotation().applyTo(offset.getRotation()),
                                                 getRotationRate().add(getRotation().applyTo(offset.getRotationRate())));
    }

    /** Subtract an offset from the instance.
     * <p>
     * We consider here that the offset rotation is applied first and the
     * instance is applied afterward. Note that angular coordinates do <em>not</em>
     * commute under this operation, i.e. {@code a.subtractOffset(b)} and {@code
     * b.subtractOffset(a)} lead to <em>different</em> results in most cases.
     * </p>
     * <p>
     * The two methods {@link #addOffset(AngularCoordinates) addOffset} and
     * {@link #subtractOffset(AngularCoordinates) subtractOffset} are designed
     * so that round trip applications are possible. This means that both {@code
     * ac1.subtractOffset(ac2).addOffset(ac2)} and {@code
     * ac1.addOffset(ac2).subtractOffset(ac2)} return angular coordinates equal to ac1.
     * </p>
     * @param offset offset to subtract
     * @return new instance, with offset subtracted
     * @see #addOffset(AngularCoordinates)
     */
    @Override
    public TimeStampedAngularCoordinates subtractOffset(final AngularCoordinates offset) {
        return addOffset(offset.revert());
    }

    /** Interpolate angular coordinates.
     * <p>
     * The interpolated instance is created by polynomial Hermite interpolation
     * on Rodrigues vector ensuring rotation rate remains the exact derivative of rotation.
     * </p>
     * <p>
     * This method is based on Sergei Tanygin's paper <a
     * href="http://www.agi.com/downloads/resources/white-papers/Attitude-interpolation.pdf">Attitude
     * Interpolation</a>, changing the norm of the vector to match the modified Rodrigues
     * vector as described in Malcolm D. Shuster's paper <a
     * href="http://www.ladispe.polito.it/corsi/Meccatronica/02JHCOR/2011-12/Slides/Shuster_Pub_1993h_J_Repsurv_scan.pdf">A
     * Survey of Attitude Representations</a>. This change avoids the singularity at &pi;.
     * There is still a singularity at 2&pi;, which is handled by slightly offsetting all rotations
     * when this singularity is detected.
     * </p>
     * <p>
     * Note that even if first time derivatives (rotation rates)
     * from sample can be ignored, the interpolated instance always includes
     * interpolated derivatives. This feature can be used explicitly to
     * compute these derivatives when it would be too complex to compute them
     * from an analytical formula: just compute a few sample points from the
     * explicit formula and set the derivatives to zero in these sample points,
     * then use interpolation to add derivatives consistent with the rotations.
     * </p>
     * @param date interpolation date
     * @param filter filter for derivatives from the sample to use in interpolation
     * @param sample sample points on which interpolation should be done
     * @return a new position-velocity, interpolated at specified date
     * @exception OrekitException if the number of point is too small for interpolating
     */
    public static TimeStampedAngularCoordinates interpolate(final AbsoluteDate date,
                                                            final AngularDerivativesFilter filter,
                                                            final Collection<TimeStampedAngularCoordinates> sample)
        throws OrekitException {

        // set up safety elements for 2PI singularity avoidance
        final double epsilon   = 2 * FastMath.PI / (100 * sample.size());
        final double threshold = FastMath.min(-(1.0 - 1.0e-4), -FastMath.cos(epsilon / 4));

        // set up a linear offset model canceling mean rotation rate
        final Vector3D meanRate;
        if (filter == AngularDerivativesFilter.USE_RR) {
            Vector3D sum = Vector3D.ZERO;
            for (final TimeStampedAngularCoordinates datedAC : sample) {
                sum = sum.add(datedAC.getRotationRate());
            }
            meanRate = new Vector3D(1.0 / sample.size(), sum);
        } else {
            if (sample.size() < 2) {
                throw new OrekitException(OrekitMessages.NOT_ENOUGH_DATA_FOR_INTERPOLATION,
                                          sample.size());
            }
            Vector3D sum = Vector3D.ZERO;
            TimeStampedAngularCoordinates previous = null;
            for (final TimeStampedAngularCoordinates datedAC : sample) {
                if (previous != null) {
                    sum = sum.add(estimateRate(previous.getRotation(), datedAC.getRotation(),
                                               datedAC.date.durationFrom(previous.date)));
                }
                previous = datedAC;
            }
            meanRate = new Vector3D(1.0 / (sample.size() - 1), sum);
        }
        TimeStampedAngularCoordinates offset = new TimeStampedAngularCoordinates(date, Rotation.IDENTITY, meanRate);

        boolean restart = true;
        for (int i = 0; restart && i < sample.size() + 2; ++i) {

            // offset adaptation parameters
            restart = false;

            // set up an interpolator taking derivatives into account
            final HermiteInterpolator interpolator = new HermiteInterpolator();

            // add sample points
            final double[] previous = new double[] {
                1.0, 0.0, 0.0, 0.0
            };
            if (filter == AngularDerivativesFilter.USE_RR) {
                // populate sample with rotation and rotation rate data
                for (final TimeStampedAngularCoordinates datedAC : sample) {

                    // remove linear offset from the current coordinates
                    final double dt = datedAC.date.durationFrom(date);
                    final TimeStampedAngularCoordinates fixed = datedAC.subtractOffset(offset.shiftedBy(dt));

                    final double[] rodrigues = getModifiedRodrigues(fixed, previous, threshold);

                    if (rodrigues == null) {
                        // the sample point is close to a modified Rodrigues vector singularity
                        // we need to change the linear offset model to avoid this
                        restart = true;
                        break;
                    }
                    interpolator.addSamplePoint(datedAC.date.durationFrom(date),
                                                new double[] {
                                                    rodrigues[0],
                                                    rodrigues[1],
                                                    rodrigues[2]
                                                },
                                                new double[] {
                                                    rodrigues[3],
                                                    rodrigues[4],
                                                    rodrigues[5]
                                                });
                }
            } else {
                // populate sample with rotation data only, ignoring rotation rate
                for (final TimeStampedAngularCoordinates datedAC : sample) {

                    // remove linear offset from the current coordinates
                    final double dt = datedAC.date.durationFrom(date);
                    final TimeStampedAngularCoordinates fixed = datedAC.subtractOffset(offset.shiftedBy(dt));

                    final double[] rodrigues = getModifiedRodrigues(fixed, previous, threshold);

                    if (rodrigues == null) {
                        // the sample point is close to a modified Rodrigues vector singularity
                        // we need to change the linear offset model to avoid this
                        restart = true;
                        break;
                    }
                    interpolator.addSamplePoint(datedAC.date.durationFrom(date),
                                                new double[] {
                                                    rodrigues[0],
                                                    rodrigues[1],
                                                    rodrigues[2]
                                                });
                }
            }

            if (restart) {
                // interpolation failed, some intermediate rotation was too close to 2PI
                // we need to offset all rotations to avoid the singularity
                offset = offset.addOffset(new AngularCoordinates(new Rotation(Vector3D.PLUS_I, epsilon),
                                                                 Vector3D.ZERO));
            } else {
                // interpolation succeeded with the current offset
                final DerivativeStructure zero = new DerivativeStructure(1, 1, 0, 0.0);
                final DerivativeStructure[] p = interpolator.value(zero);
                return createFromModifiedRodrigues(new double[] {
                    p[0].getValue(), p[1].getValue(), p[2].getValue(),
                    p[0].getPartialDerivative(1), p[1].getPartialDerivative(1), p[2].getPartialDerivative(1)
                }, offset);
            }

        }

        // this should never happen
        throw OrekitException.createInternalError(null);

    }

    /** Convert rotation and rate to modified Rodrigues vector and derivative.
     * <p>
     * The modified Rodrigues vector is tan(&theta;/4) u where &theta; and u are the
     * rotation angle and axis respectively.
     * </p>
     * @param fixed coordinates to convert, with offset already fixed
     * @param previous previous quaternion used
     * @param threshold threshold for rotations too close to 2&pi;
     * @return modified Rodrigues vector and derivative, or null if rotation is too close to 2&pi;
     */
    private static double[] getModifiedRodrigues(final TimeStampedAngularCoordinates fixed,
                                                 final double[] previous, final double threshold) {

        // make sure all interpolated points will be on the same branch
        double q0 = fixed.getRotation().getQ0();
        double q1 = fixed.getRotation().getQ1();
        double q2 = fixed.getRotation().getQ2();
        double q3 = fixed.getRotation().getQ3();
        if (MathArrays.linearCombination(q0, previous[0], q1, previous[1], q2, previous[2], q3, previous[3]) < 0) {
            q0 = -q0;
            q1 = -q1;
            q2 = -q2;
            q3 = -q3;
        }
        previous[0] = q0;
        previous[1] = q1;
        previous[2] = q2;
        previous[3] = q3;

        // check modified Rodrigues vector singularity
        if (q0 < threshold) {
            // this is an intermediate point that happens to be 2PI away from reference
            // we need to change the linear offset model to avoid this point
            return null;
        }

        final double x  = fixed.getRotationRate().getX();
        final double y  = fixed.getRotationRate().getY();
        final double z  = fixed.getRotationRate().getZ();

        // derivatives of the quaternion
        final double q0Dot = -0.5 * MathArrays.linearCombination(q1, x, q2, y,  q3, z);
        final double q1Dot =  0.5 * MathArrays.linearCombination(q0, x, q2, z, -q3, y);
        final double q2Dot =  0.5 * MathArrays.linearCombination(q0, y, q3, x, -q1, z);
        final double q3Dot =  0.5 * MathArrays.linearCombination(q0, z, q1, y, -q2, x);

        final double inv = 1.0 / (1.0 + q0);
        return new double[] {
            inv * q1,
            inv * q2,
            inv * q3,
            inv * (q1Dot - inv * q1 * q0Dot),
            inv * (q2Dot - inv * q2 * q0Dot),
            inv * (q3Dot - inv * q3 * q0Dot)
        };

    }

    /** Convert a modified Rodrigues vector and derivative to angular coordinates.
     * @param r modified Rodrigues vector (with first derivatives)
     * @param offset linear offset model to add (its date must be consistent with the modified Rodrigues vector)
     * @return angular coordinates
     */
    private static TimeStampedAngularCoordinates createFromModifiedRodrigues(final double[] r,
                                                                             final TimeStampedAngularCoordinates offset) {

        // rotation
        final double rSquared = r[0] * r[0] + r[1] * r[1] + r[2] * r[2];
        final double inv      = 1.0 / (1 + rSquared);
        final double ratio    = inv * (1 - rSquared);
        final Rotation rotation =
                new Rotation(ratio, 2 * inv * r[0], 2 * inv * r[1], 2 * inv * r[2], false);

        // rotation rate
        final Vector3D p    = new Vector3D(r[0], r[1], r[2]);
        final Vector3D pDot = new Vector3D(r[3], r[4], r[5]);
        final Vector3D rate = new Vector3D( 4 * ratio * inv, pDot,
                                           -8 * inv * inv, p.crossProduct(pDot),
                                            8 * inv * inv * p.dotProduct(pDot), p);

        return new TimeStampedAngularCoordinates(offset.date, rotation, rate).addOffset(offset);

    }

}
