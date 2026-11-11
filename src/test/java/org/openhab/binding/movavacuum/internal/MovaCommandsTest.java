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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MovaCommands}: translation of the channel commands into robot payloads.
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public class MovaCommandsTest {

    private static final Map<String, Integer> NAMES = Map.of("küche", 8, "flur", 7);

    @Test
    public void suctionNames() {
        assertEquals(0, MovaCommands.suctionToCode("quiet"));
        assertEquals(3, MovaCommands.suctionToCode(" Turbo "));
        assertEquals(-1, MovaCommands.suctionToCode("max"));
    }

    @Test
    public void roomsByNumberAndNameWithOptions() {
        assertEquals("{\"selects\":[[8,2,2,3,1],[7,1,1,2,1]]}",
                MovaCommands.roomsJson("Küche@times=2/suction=strong/water=3, 7", NAMES, Map.of(), 1, 2));
    }

    @Test
    public void roomsSeparatedBySemicolonAndTimesClamped() {
        assertEquals("{\"selects\":[[1,3,0,1,1],[2,1,0,1,1]]}",
                MovaCommands.roomsJson("1@times=9;2", NAMES, Map.of(), 0, 1));
    }

    @Test
    public void roomOptionUsesStoredRoomSettings() throws Exception {
        MovaMap.Room stored = MovaMap.decode(TestMaps.encode(TestMaps.raw(TestMaps.roomsJson(), true)), null, null).rooms
                .get(0);
        stored.times = 2;
        stored.suction = 3;
        stored.waterVolume = 1;
        Map<Integer, MovaMap.Room> byId = new HashMap<>();
        byId.put(stored.id, stored);
        assertEquals("{\"selects\":[[" + stored.id + ",2,3,1,1]]}",
                MovaCommands.roomsJson(stored.id + "@room", NAMES, byId, 0, 2));
    }

    @Test
    public void unknownRoomAndOptionAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> MovaCommands.roomsJson("Keller", NAMES, Map.of(), 1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> MovaCommands.roomsJson("7@speed=2", NAMES, Map.of(), 1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> MovaCommands.roomsJson("7@times=x", NAMES, Map.of(), 1, 2));
        assertThrows(IllegalArgumentException.class, () -> MovaCommands.roomsJson(" , ", NAMES, Map.of(), 1, 2));
    }

    @Test
    public void zoneCornersAreNormalised() {
        assertEquals("{\"areas\":[[-1000,-500,1000,500,2,1,2],[0,0,10,10,1,3,1]]}",
                MovaCommands.zoneJson("1000,500,-1000,-500@times=2; 0 0 10 10@suction=turbo/water=1", 1, 2));
    }

    @Test
    public void invalidZonesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> MovaCommands.zoneJson("1,2,3", 1, 2));
        assertThrows(IllegalArgumentException.class, () -> MovaCommands.zoneJson("a,b,c,d", 1, 2));
        assertThrows(IllegalArgumentException.class, () -> MovaCommands.zoneJson("", 1, 2));
    }

    @Test
    public void spotWithoutMapIsAccepted() {
        assertEquals("{\"points\":[[700,-1500,1,0,2]]}", MovaCommands.spotJson("700,-1500@suction=quiet", 1, 2, null));
    }

    @Test
    public void spotMustBeOnFloor() throws Exception {
        MovaMap.Result map = TestMaps.map();
        // pixel (5,5) -> x = -1000 + 5*50 = -750, y = -500 + 5*50 = -250: floor of room 1
        assertEquals("{\"points\":[[-740,-240,1,1,2]]}", MovaCommands.spotJson("-740,-240", 1, 2, map));
        // pixel (30,10) lies in the hole of room 2
        assertThrows(IllegalArgumentException.class, () -> MovaCommands.spotJson("510,10", 1, 2, map));
        // outside the map
        assertThrows(IllegalArgumentException.class, () -> MovaCommands.spotJson("5000,5000", 1, 2, map));
    }

    @Test
    public void goTo() throws Exception {
        assertEquals("{\"tpoint\":[[-740,-240,0,0]]}", MovaCommands.goToJson("-740, -240", TestMaps.map()));
        assertThrows(IllegalArgumentException.class, () -> MovaCommands.goToJson("1", null));
        assertThrows(IllegalArgumentException.class, () -> MovaCommands.goToJson("5000,5000", TestMaps.map()));
    }

    @Test
    public void remoteControl() {
        assertEquals("{\"spdv\":200,\"spdw\":0,\"audio\":\"false\",\"random\":5}",
                MovaCommands.remoteJson("Forward", 5));
        assertEquals("{\"spdv\":0,\"spdw\":-120,\"audio\":\"false\",\"random\":5}",
                MovaCommands.remoteJson("right", 5));
        assertEquals("{\"spdv\":600,\"spdw\":-360,\"audio\":\"false\",\"random\":1}",
                MovaCommands.remoteJson("-500,900", 1));
        assertThrows(IllegalArgumentException.class, () -> MovaCommands.remoteJson("jump", 1));
    }
}
