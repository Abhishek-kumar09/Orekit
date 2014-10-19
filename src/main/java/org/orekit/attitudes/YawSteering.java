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
package org.orekit.attitudes;

import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.orekit.errors.OrekitException;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinatesProvider;


/**
 * This class handles yaw steering law.

 * <p>
 * Yaw steering is mainly used for low Earth orbiting satellites with no
 * missions-related constraints on yaw angle. It sets the yaw angle in
 * such a way the solar arrays have maximal lightning without changing the
 * roll and pitch.
 * </p>
 * <p>
 * The motion in yaw is smooth when the Sun is far from the orbital plane,
 * but gets more and more <i>square like</i> as the Sun gets closer to the
 * orbital plane. The degenerate extreme case with the Sun in the orbital
 * plane leads to a yaw angle switching between two steady states, with
 * instantaneaous &pi; radians rotations at each switch, two times per orbit.
 * This degenerate case is clearly not operationally sound so another pointing
 * mode is chosen when Sun comes closer than some predefined threshold to the
 * orbital plane.
 * </p>
 * <p>
 * This class can handle (for now) only a theoretically perfect yaw steering
 * (i.e. the yaw angle is exactly the optimal angle). Smoothed yaw steering with a
 * few sine waves approaching the optimal angle will be added in the future if
 * needed.
 * </p>
 * <p>
 * This attitude is implemented as a wrapper on top of an underlying ground
 * pointing law that defines the roll and pitch angles.
 * </p>
 * <p>
 * Instances of this class are guaranteed to be immutable.
 * </p>
 * @see     GroundPointing
 * @author Luc Maisonobe
 */
public class YawSteering extends GroundPointingWrapper {

    /** Serializable UID. */
    private static final long serialVersionUID = -5804405406938727964L;

    /** Sun motion model. */
    private final PVCoordinatesProvider sun;

    /** Satellite axis that must be roughly in Sun direction. */
    private final Vector3D phasingAxis;

    /** Creates a new instance.
     * @param groundPointingLaw ground pointing attitude provider without yaw compensation
     * @param sun sun motion model
     * @param phasingAxis satellite axis that must be roughly in Sun direction
     * (if solar arrays rotation axis is Y, then this axis should be either +X or -X)
     */
    public YawSteering(final GroundPointing groundPointingLaw,
                       final PVCoordinatesProvider sun,
                       final Vector3D phasingAxis) {
        super(groundPointingLaw);
        this.sun = sun;
        this.phasingAxis = phasingAxis;
    }

    /** {@inheritDoc} */
    public Rotation getCompensation(final PVCoordinatesProvider pvProv,
                                    final AbsoluteDate date, final Frame orbitFrame,
                                    final Attitude base)
        throws OrekitException {

        // Compensation rotation definition :
        //  . Z satellite axis is unchanged
        //  . phasing axis shall be aligned to sun direction
        final Vector3D sunPosition  = sun.getPVCoordinates(date, orbitFrame).getPosition();
        final Vector3D sunDirection = sunPosition.subtract(pvProv.getPVCoordinates(date, orbitFrame).getPosition());
        final Rotation compensation =
            new Rotation(Vector3D.PLUS_K, base.getRotation().applyTo(sunDirection),
                         Vector3D.PLUS_K, phasingAxis);

        return compensation;
    }

}
