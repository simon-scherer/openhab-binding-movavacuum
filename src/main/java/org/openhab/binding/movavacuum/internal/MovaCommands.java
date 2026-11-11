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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Translates the text commands of the cleaning channels into the JSON payloads expected by the robot
 * (START_CUSTOM, siid 4 / aiid 1, piid 10). Pure functions without cloud access so that they can be unit tested.
 * Invalid input is reported with an {@link IllegalArgumentException} whose message is meant for the log.
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public final class MovaCommands {

    private MovaCommands() {
    }

    /** Suction level name to code (quiet 0 .. turbo 3), -1 if unknown. */
    public static int suctionToCode(String text) {
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "quiet" -> 0;
            case "standard" -> 1;
            case "strong" -> 2;
            case "turbo" -> 3;
            default -> -1;
        };
    }

    /** True if the point (mm) lies on floor of the map; true as well if no map is known. */
    public static boolean pointInMap(MovaMap.@Nullable Result r, int xMm, int yMm) {
        if (r == null || r.grid <= 0 || r.imgWidth <= 0 || r.image.length < r.imgWidth * r.imgHeight) {
            return true;
        }
        int px = Math.floorDiv(xMm - r.left, r.grid);
        int py = Math.floorDiv(yMm - r.top, r.grid);
        if (px < 0 || py < 0 || px >= r.imgWidth || py >= r.imgHeight) {
            return false;
        }
        int v = r.image[py * r.imgWidth + px] & 0xff;
        return v != 0 && (v & 0x80) == 0;
    }

    /**
     * Options after '@' (separated by '/'): times=1-3, suction=quiet..turbo or 0-3, water=n, and for rooms
     * "room" (use the stored room settings). Returns {times, suction, water}.
     */
    static int[] options(String options, int defSuction, int defWater, String what,
            MovaMap.@Nullable Room storedRoom, boolean allowRoom) {
        int times = 1;
        int suction = defSuction;
        int water = defWater;
        for (String opt : options.split("/")) {
            String o = opt.trim().toLowerCase(Locale.ROOT);
            if (o.isEmpty()) {
                continue;
            }
            if (allowRoom && "room".equals(o)) {
                if (storedRoom != null) {
                    Integer t = storedRoom.times;
                    Integer s = storedRoom.suction;
                    Integer w = storedRoom.waterVolume;
                    times = t != null ? Math.max(1, t) : times;
                    suction = s != null ? s : suction;
                    water = w != null ? w : water;
                }
                continue;
            }
            int eq = o.indexOf('=');
            String key = eq >= 0 ? o.substring(0, eq).trim() : o;
            String val = eq >= 0 ? o.substring(eq + 1).trim() : "";
            try {
                switch (key) {
                    case "times" -> times = Math.max(1, Math.min(3, Integer.parseInt(val)));
                    case "suction" -> {
                        int code = suctionToCode(val);
                        suction = code >= 0 ? code : Integer.parseInt(val);
                    }
                    case "water" -> water = Integer.parseInt(val);
                    default -> throw new IllegalArgumentException("Unknown " + what + " option '" + opt
                            + "' (allowed: times, suction, water" + (allowRoom ? ", room)" : ")"));
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid value in " + what + " option '" + opt + "'");
            }
        }
        return new int[] { times, suction, water };
    }

    private static String[] splitOptions(String part) {
        int at = part.indexOf('@');
        return at >= 0 ? new String[] { part.substring(0, at).trim(), part.substring(at + 1).trim() }
                : new String[] { part.trim(), "" };
    }

    private static int coord(String s) {
        return (int) Math.round(Double.parseDouble(s));
    }

    /** Spot cleaning: {@code x,y[@options]}, several separated by ';'. */
    public static String spotJson(String value, int defSuction, int defWater, MovaMap.@Nullable Result map) {
        List<Object> points = new ArrayList<>();
        for (String part : value.split(";+")) {
            if (part.isBlank()) {
                continue;
            }
            String[] po = splitOptions(part);
            String[] xy = po[0].split("[,\\s]+");
            if (xy.length != 2) {
                throw new IllegalArgumentException("Invalid point '" + po[0] + "': expected x,y");
            }
            int x;
            int y;
            try {
                x = coord(xy[0]);
                y = coord(xy[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid coordinates in point '" + po[0] + "'");
            }
            if (!pointInMap(map, x, y)) {
                throw new IllegalArgumentException("Point " + x + "," + y + " is not on the map");
            }
            int[] o = options(po[1], defSuction, defWater, "point", null, false);
            points.add(Arrays.asList(x, y, o[0], o[1], o[2]));
        }
        if (points.isEmpty()) {
            throw new IllegalArgumentException("No point given");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("points", points);
        return MovaSigner.toJson(data, false);
    }

    /** Zone cleaning: {@code x1,y1,x2,y2[@options]}, several separated by ';'. Corners are normalised. */
    public static String zoneJson(String value, int defSuction, int defWater) {
        List<Object> areas = new ArrayList<>();
        for (String part : value.split(";+")) {
            if (part.isBlank()) {
                continue;
            }
            String[] po = splitOptions(part);
            String[] xy = po[0].split("[,\\s]+");
            if (xy.length != 4) {
                throw new IllegalArgumentException("Invalid zone '" + po[0] + "': expected x1,y1,x2,y2");
            }
            int[] c = new int[4];
            try {
                for (int i = 0; i < 4; i++) {
                    c[i] = coord(xy[i]);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid coordinates in zone '" + po[0] + "'");
            }
            int[] o = options(po[1], defSuction, defWater, "zone", null, false);
            areas.add(Arrays.asList(Math.min(c[0], c[2]), Math.min(c[1], c[3]), Math.max(c[0], c[2]),
                    Math.max(c[1], c[3]), o[0], o[1], o[2]));
        }
        if (areas.isEmpty()) {
            throw new IllegalArgumentException("No zone given");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("areas", areas);
        return MovaSigner.toJson(data, false);
    }

    /**
     * Room cleaning: rooms by number or name (case insensitive), separated by ',' or ';', each with optional
     * options.
     */
    public static String roomsJson(String value, Map<String, Integer> idsByLowerName,
            Map<Integer, MovaMap.Room> roomsById, int defSuction, int defWater) {
        List<Object> selects = new ArrayList<>();
        for (String part : value.split("[,;]+")) {
            if (part.isBlank()) {
                continue;
            }
            String[] po = splitOptions(part);
            int id;
            try {
                id = Integer.parseInt(po[0]);
            } catch (NumberFormatException e) {
                Integer byName = idsByLowerName.get(po[0].toLowerCase(Locale.ROOT));
                if (byName == null) {
                    throw new IllegalArgumentException(
                            "Invalid room number or unknown room name '" + po[0] + "' in '" + value + "'");
                }
                id = byName;
            }
            int[] o = options(po[1], defSuction, defWater, "room", roomsById.get(id), true);
            selects.add(Arrays.asList(id, o[0], o[1], o[2], 1));
        }
        if (selects.isEmpty()) {
            throw new IllegalArgumentException("No room numbers given");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("selects", selects);
        return MovaSigner.toJson(data, false);
    }

    /** Go to point: {@code x,y}. */
    public static String goToJson(String value, MovaMap.@Nullable Result map) {
        String[] xy = value.trim().split("[,\\s;]+");
        if (xy.length != 2) {
            throw new IllegalArgumentException("Invalid target point '" + value + "': expected x,y");
        }
        int x;
        int y;
        try {
            x = coord(xy[0]);
            y = coord(xy[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid coordinates in target point '" + value + "'");
        }
        if (!pointInMap(map, x, y)) {
            throw new IllegalArgumentException("Target point " + x + "," + y + " is not on the map");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tpoint", List.of(Arrays.asList(x, y, 0, 0)));
        return MovaSigner.toJson(data, false);
    }

    /**
     * Remote control step: forward, backward, left, right, stop or {@code rotation,velocity}
     * (-360..360, -600..600). Returns the value for property 4/15.
     */
    public static String remoteJson(String value, int random) {
        String text = value.trim().toLowerCase(Locale.ROOT);
        int rotation;
        int velocity;
        switch (text) {
            case "forward" -> {
                rotation = 0;
                velocity = 200;
            }
            case "backward" -> {
                rotation = 0;
                velocity = -200;
            }
            case "left" -> {
                rotation = 120;
                velocity = 0;
            }
            case "right" -> {
                rotation = -120;
                velocity = 0;
            }
            case "stop" -> {
                rotation = 0;
                velocity = 0;
            }
            default -> {
                String[] parts = text.split("[,\\s;]+");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid remote control '" + value
                            + "' (allowed: forward, backward, left, right, stop or rotation,velocity)");
                }
                try {
                    rotation = Math.max(-360, Math.min(360, Integer.parseInt(parts[0])));
                    velocity = Math.max(-600, Math.min(600, Integer.parseInt(parts[1])));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid numbers in remote control '" + value + "'");
                }
            }
        }
        return "{\"spdv\":" + velocity + ",\"spdw\":" + rotation + ",\"audio\":\"false\",\"random\":" + random
                + "}";
    }
}
