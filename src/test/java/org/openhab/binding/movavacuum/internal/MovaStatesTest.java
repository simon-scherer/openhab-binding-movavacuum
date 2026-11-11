/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.movavacuum.internal;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests for the state and station code tables.
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public class MovaStatesTest {

    private static MovaStation.Sensor sensor(String channel) {
        return MovaStation.SENSORS.stream().filter(s -> s.channel.equals(channel)).findFirst().orElseThrow();
    }

    @Test
    public void stateNames() {
        assertEquals("sweeping", MovaStates.name(1));
        assertEquals("charging_completed", MovaStates.name(13));
        assertEquals("unknown (9999)", MovaStates.name(9999));
        assertTrue(MovaStates.isCleaning(1));
        assertFalse(MovaStates.isCleaning(13));
    }

    @Test
    public void stationSensors() {
        assertEquals("returning_to_drain", sensor("base-status").nameOf(6));
        assertEquals("returning", sensor("auto-empty-status").nameOf(3));
        assertEquals("over_use", sensor("dust-collection").nameOf(2));
        assertEquals("unknown_77", sensor("base-status").nameOf(77));
    }

    @Test
    public void channelIdsAreUnique() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        MovaSettings.DEFS.forEach(d -> assertTrue(seen.add(d.channel), "duplicate channel " + d.channel));
        MovaStation.SENSORS.forEach(s -> assertTrue(seen.add(s.channel), "duplicate channel " + s.channel));
    }
}
