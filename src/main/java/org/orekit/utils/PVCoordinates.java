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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.util.Pair;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeShiftable;

/** Simple container for Position/Velocity pairs.
 * <p>
 * The state can be slightly shifted to close dates. This shift is based on
 * a simple linear model. It is <em>not</em> intended as a replacement for
 * proper orbit propagation (it is not even Keplerian!) but should be sufficient
 * for either small time shifts or coarse accuracy.
 * </p>
 * <p>
 * This class is the angular counterpart to {@link AngularCoordinates}.
 * </p>
 * <p>Instances of this class are guaranteed to be immutable.</p>
 * @author Fabien Maussion
 * @author Luc Maisonobe
 */
public class PVCoordinates implements TimeShiftable<PVCoordinates>, Serializable {

    /** Fixed position/velocity at origin (both p and v are zero vectors). */
    public static final PVCoordinates ZERO = new PVCoordinates(Vector3D.ZERO, Vector3D.ZERO);

    /** Serializable UID. */
    private static final long serialVersionUID = 4157449919684833834L;

    /** The position. */
    private final Vector3D position;

    /** The velocity. */
    private final Vector3D velocity;

    /** Simple constructor.
     * <p> Sets the Coordinates to default : (0 0 0) (0 0 0).</p>
     */
    public PVCoordinates() {
        position = Vector3D.ZERO;
        velocity = Vector3D.ZERO;
    }

    /** Builds a PVCoordinates pair.
     * @param position the position vector (m)
     * @param velocity the velocity vector (m/s)
     */
    public PVCoordinates(final Vector3D position, final Vector3D velocity) {
        this.position = position;
        this.velocity = velocity;
    }

    /** Multiplicative constructor
     * <p>Build a PVCoordinates from another one and a scale factor.</p>
     * <p>The PVCoordinates built will be a * pv</p>
     * @param a scale factor
     * @param pv base (unscaled) PVCoordinates
     */
    public PVCoordinates(final double a, final PVCoordinates pv) {
        position = new Vector3D(a, pv.position);
        velocity = new Vector3D(a, pv.velocity);
    }

    /** Subtractive constructor
     * <p>Build a relative PVCoordinates from a start and an end position.</p>
     * <p>The PVCoordinates built will be end - start.</p>
     * @param start Starting PVCoordinates
     * @param end ending PVCoordinates
     */
    public PVCoordinates(final PVCoordinates start, final PVCoordinates end) {
        this.position = end.position.subtract(start.position);
        this.velocity = end.velocity.subtract(start.velocity);
    }

    /** Linear constructor
     * <p>Build a PVCoordinates from two other ones and corresponding scale factors.</p>
     * <p>The PVCoordinates built will be a1 * u1 + a2 * u2</p>
     * @param a1 first scale factor
     * @param pv1 first base (unscaled) PVCoordinates
     * @param a2 second scale factor
     * @param pv2 second base (unscaled) PVCoordinates
     */
    public PVCoordinates(final double a1, final PVCoordinates pv1,
                         final double a2, final PVCoordinates pv2) {
        position = new Vector3D(a1, pv1.position, a2, pv2.position);
        velocity = new Vector3D(a1, pv1.velocity, a2, pv2.velocity);
    }

    /** Linear constructor
     * <p>Build a PVCoordinates from three other ones and corresponding scale factors.</p>
     * <p>The PVCoordinates built will be a1 * u1 + a2 * u2 + a3 * u3</p>
     * @param a1 first scale factor
     * @param pv1 first base (unscaled) PVCoordinates
     * @param a2 second scale factor
     * @param pv2 second base (unscaled) PVCoordinates
     * @param a3 third scale factor
     * @param pv3 third base (unscaled) PVCoordinates
     */
    public PVCoordinates(final double a1, final PVCoordinates pv1,
                         final double a2, final PVCoordinates pv2,
                         final double a3, final PVCoordinates pv3) {
        position = new Vector3D(a1, pv1.position, a2, pv2.position, a3, pv3.position);
        velocity = new Vector3D(a1, pv1.velocity, a2, pv2.velocity, a3, pv3.velocity);
    }

    /** Linear constructor
     * <p>Build a PVCoordinates from four other ones and corresponding scale factors.</p>
     * <p>The PVCoordinates built will be a1 * u1 + a2 * u2 + a3 * u3 + a4 * u4</p>
     * @param a1 first scale factor
     * @param pv1 first base (unscaled) PVCoordinates
     * @param a2 second scale factor
     * @param pv2 second base (unscaled) PVCoordinates
     * @param a3 third scale factor
     * @param pv3 third base (unscaled) PVCoordinates
     * @param a4 fourth scale factor
     * @param pv4 fourth base (unscaled) PVCoordinates
     */
    public PVCoordinates(final double a1, final PVCoordinates pv1,
                         final double a2, final PVCoordinates pv2,
                         final double a3, final PVCoordinates pv3,
                         final double a4, final PVCoordinates pv4) {
        position = new Vector3D(a1, pv1.position, a2, pv2.position, a3, pv3.position, a4, pv4.position);
        velocity = new Vector3D(a1, pv1.velocity, a2, pv2.velocity, a3, pv3.velocity, a4, pv4.velocity);
    }

    /** Estimate velocity between two positions.
     * <p>Estimation is based on a simple fixed velocity translation
     * during the time interval between the two positions.</p>
     * @param start start position
     * @param end end position
     * @param dt time elapsed between the dates of the two positions
     * @return velocity allowing to go from start to end positions
     */
    public static Vector3D estimateVelocity(final Vector3D start, final Vector3D end, final double dt) {
        final double scale = 1.0 / dt;
        return new Vector3D(scale, end, -scale, start);
    }

    /** Get a time-shifted state.
     * <p>
     * The state can be slightly shifted to close dates. This shift is based on
     * a simple linear model. It is <em>not</em> intended as a replacement for
     * proper orbit propagation (it is not even Keplerian!) but should be sufficient
     * for either small time shifts or coarse accuracy.
     * </p>
     * @param dt time shift in seconds
     * @return a new state, shifted with respect to the instance (which is immutable)
     */
    public PVCoordinates shiftedBy(final double dt) {
        return new PVCoordinates(new Vector3D(1, position, dt, velocity), velocity);
    }

    /** Interpolate position-velocity.
     * <p>
     * The interpolated instance is created by polynomial Hermite interpolation
     * ensuring velocity remains the exact derivative of position.
     * </p>
     * <p>
     * Note that even if first time derivatives (velocities)
     * from sample can be ignored, the interpolated instance always includes
     * interpolated derivatives. This feature can be used explicitly to
     * compute these derivatives when it would be too complex to compute them
     * from an analytical formula: just compute a few sample points from the
     * explicit formula and set the derivatives to zero in these sample points,
     * then use interpolation to add derivatives consistent with the positions.
     * </p>
     * @param date interpolation date
     * @param useVelocities if true, use sample points velocities,
     * otherwise ignore them and use only positions
     * @param sample sample points on which interpolation should be done
     * @return a new position-velocity, interpolated at specified date
     * @deprecated since 7.0 replaced with {@link TimeStampedPVCoordinates#interpolate(AbsoluteDate, CartesianDerivativesFilter, Collection)}
     */
    @Deprecated
    public static PVCoordinates interpolate(final AbsoluteDate date, final boolean useVelocities,
                                            final Collection<Pair<AbsoluteDate, PVCoordinates>> sample) {
        final List<TimeStampedPVCoordinates> list = new ArrayList<TimeStampedPVCoordinates>(sample.size());
        for (final Pair<AbsoluteDate, PVCoordinates> pair : sample) {
            list.add(new TimeStampedPVCoordinates(pair.getFirst(),
                                                       pair.getSecond().getPosition(), pair.getSecond().getVelocity()));
        }
        return TimeStampedPVCoordinates.interpolate(date,
                                                    useVelocities ? CartesianDerivativesFilter.USE_PV : CartesianDerivativesFilter.USE_P,
                                                    list);
    }

    /** Gets the position.
     * @return the position vector (m).
     */
    public Vector3D getPosition() {
        return position;
    }

    /** Gets the velocity.
     * @return the velocity vector (m/s).
     */
    public Vector3D getVelocity() {
        return velocity;
    }

    /** Gets the momentum.
     * <p>This vector is the p &otimes; v where p is position, v is velocity
     * and &otimes; is cross product. To get the real physical angular momentum
     * you need to multiply this vector by the mass.</p>
     * <p>The returned vector is recomputed each time this method is called, it
     * is not cached.</p>
     * @return a new instance of the momentum vector (m<sup>2</sup>/s).
     */
    public Vector3D getMomentum() {
        return Vector3D.crossProduct(position, velocity);
    }

    /**
     * Get the angular velocity (spin) of this point as seen from the origin.
     * <p/>
     * The angular velocity vector is parallel to the {@link #getMomentum() angular
     * momentum} and is computed by &omega; = p &times; v / ||p||<sup>2</sup>
     *
     * @return the angular velocity vector
     * @see <a href="http://en.wikipedia.org/wiki/Angular_velocity">Angular Velocity on
     *      Wikipedia</a>
     */
    public Vector3D getAngularVelocity() {
        return this.getMomentum().scalarMultiply(1.0 / this.getPosition().getNormSq());
    }

    /** Get the opposite of the instance.
     * @return a new position-velocity which is opposite to the instance
     */
    public PVCoordinates negate() {
        return new PVCoordinates(position.negate(), velocity.negate());
    }

    /** Return a string representation of this position/velocity pair.
     * @return string representation of this position/velocity pair
     */
    public String toString() {
        final String comma = ", ";
        return new StringBuffer().append('{').append("P(").
                                  append(position.getX()).append(comma).
                                  append(position.getY()).append(comma).
                                  append(position.getZ()).append("), V(").
                                  append(velocity.getX()).append(comma).
                                  append(velocity.getY()).append(comma).
                                  append(velocity.getZ()).append(")}").toString();
    }

    /** Replace the instance with a data transfer object for serialization.
     * @return data transfer object that will be serialized
     */
    private Object writeReplace() {
        return new DTO(this);
    }

    /** Internal class used only for serialization. */
    private static class DTO implements Serializable {

        /** Serializable UID. */
        private static final long serialVersionUID = 20140617L;

        /** Double values. */
        private double[] d;

        /** Simple constructor.
         * @param pv instance to serialize
         */
        private DTO(final PVCoordinates pv) {
            this.d = new double[] {
                pv.getPosition().getX(), pv.getPosition().getY(), pv.getPosition().getZ(),
                pv.getVelocity().getX(), pv.getVelocity().getY(), pv.getVelocity().getZ(),
            };
        }

        /** Replace the deserialized data transfer object with a {@link PVCoordinates}.
         * @return replacement {@link PVCoordinates}
         */
        private Object readResolve() {
            return new PVCoordinates(new Vector3D(d[0], d[1], d[2]), new Vector3D(d[3], d[4], d[5]));
        }

    }

}
