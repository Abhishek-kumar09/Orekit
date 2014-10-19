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

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.orekit.bodies.BodyShape;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.errors.OrekitException;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinatesProvider;

/**
 * This class handles nadir pointing attitude provider.

 * <p>
 * This class represents the attitude provider where the satellite z axis is
 * pointing to the vertical of the ground point under satellite.</p>
 * <p>
 * The object <code>NadirPointing</code> is guaranteed to be immutable.
 * </p>
 * @see     GroundPointing
 * @author V&eacute;ronique Pommier-Maurussane
 */
public class NadirPointing extends GroundPointing {

    /** Serializable UID. */
    private static final long serialVersionUID = 9077899256315179822L;

    /** Body shape.  */
    private final BodyShape shape;

    /** Creates new instance.
     * @param shape Body shape
     */
    public NadirPointing(final BodyShape shape) {
        // Call constructor of superclass
        super(shape.getBodyFrame());
        this.shape = shape;
    }

    /** {@inheritDoc} */
    protected Vector3D getTargetPoint(final PVCoordinatesProvider pvProv,
                                      final AbsoluteDate date, final Frame frame)
        throws OrekitException {

        final Vector3D satInBodyFrame = pvProv.getPVCoordinates(date, getBodyFrame()).getPosition();

        // satellite position in geodetic coordinates
        final GeodeticPoint gpSat = shape.transform(satInBodyFrame, getBodyFrame(), date);

        // nadir position in geodetic coordinates
        final GeodeticPoint gpNadir = new GeodeticPoint(gpSat.getLatitude(), gpSat.getLongitude(), 0.0);

        // nadir point position in specified frame
        return getBodyFrame().getTransformTo(frame, date).transformPosition(shape.transform(gpNadir));

    }

}
