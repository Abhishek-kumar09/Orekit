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
package org.orekit.files.ccsds;

/** Operational status used in CCSDS {@link OCMFile Orbit Comprehensive Messages}.
 * @author Luc Maisonobe
 * @since 10.1
 */
public enum CCSDSOpsStatus {

    /** Operational object. */
    OPERATIONAL,

    /** Non-operational object. */
    NONOPERATIONAL,

    /** partially operational object. */
    PARTIALLY_OPERATIONAL,

    /** Backup object. */
    BACKUP,

    /** Object in stand-by. */
    STANBY,

    /** Object in extended mission. */
    EXTENDED_MISSION,

    /** Object in reentry mode. */
    REENTRY_MODE,

    /** Decayed object. */
    DECAYED,

    /** Unknown status. */
    UNKNOWN;

}