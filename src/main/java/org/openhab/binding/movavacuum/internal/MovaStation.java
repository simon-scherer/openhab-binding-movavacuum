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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Statussensoren der Basisstation (nur lesen), Basisstations-Befehle und Zurücksetzen von Verbrauchsmaterial
 * (Werte und Aktionen nach Tasshack/dreame-vacuum v2.0.1).
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public final class MovaStation {

    public static final String CHANNEL_CLEANING_MODE = "cleaning-mode";
    public static final String CHANNEL_WATER_LEVEL = "water-level";
    public static final String CHANNEL_BASE_COMMAND = "base-command";
    public static final String CHANNEL_RESET = "reset-consumable";
    public static final String CHANNEL_ROOM_INFO = "room-info";
    public static final String CHANNEL_ROOM_ZONES = "room-zones";
    public static final String CHANNEL_ROBOT_POSITION = "robot-position";
    public static final String CHANNEL_ROOM_SETTINGS = "room-settings";
    public static final int SIID_MAP = 6;
    public static final int AIID_UPDATE_MAP_DATA = 2;
    public static final int PIID_MAP_EXTEND_DATA = 4;
    public static final String CHANNEL_ERROR_TEXT = "error-text";
    public static final String CHANNEL_LAST_CLEANING = "last-cleaning";
    public static final String CHANNEL_CLEANING_HISTORY = "cleaning-history";
    public static final String CHANNEL_MAP_IMAGE = "map-image";
    public static final String CHANNEL_MAP_IMAGE_DARK = "map-image-dark";
    public static final String CHANNEL_CHARGE_LIMIT = "charge-limit";
    public static final String CHANNEL_OFF_PEAK = "off-peak-charging";
    public static final String CHANNEL_OFF_PEAK_START = "off-peak-start";
    public static final String CHANNEL_OFF_PEAK_END = "off-peak-end";

    /** Ladelimit in Prozent (Tasshack: BATTERY_CHARGE_LEVEL). */
    public static final int SIID_CHARGE_LIMIT = 28;
    public static final int PIID_CHARGE_LIMIT = 47;
    public static final int MIN_CHARGE_LIMIT = 80;
    public static final int MAX_CHARGE_LIMIT = 100;

    /** Niedertarif-Laden als JSON-Text {"enable":false,"startTime":"22:00","endTime":"08:00"} in 3/3. */
    public static final int SIID_OFF_PEAK = 3;
    public static final int PIID_OFF_PEAK = 3;

    /** Wetness-Level (Wasserstufe) der Mopp-Pad-Modelle. */
    public static final int SIID_WETNESS = 28;
    public static final int PIID_WETNESS = 1;
    public static final int MIN_WETNESS = 1;
    public static final int MAX_WETNESS = 32;

    /** Art des Sensors. */
    public enum Kind {
        ENUM,
        PERCENT,
        FLAG
    }

    /** Beschreibung eines Sensors. */
    public static final class Sensor {
        public final String channel;
        public final String itemType;
        public final String channelTypeId;
        public final String label;
        public final Kind kind;
        public final int siid;
        public final int piid;
        public final @Nullable Map<Integer, String> names;

        Sensor(String channel, String itemType, String channelTypeId, String label, Kind kind, int siid, int piid,
                @Nullable Map<Integer, String> names) {
            this.channel = channel;
            this.itemType = itemType;
            this.channelTypeId = channelTypeId;
            this.label = label;
            this.kind = kind;
            this.siid = siid;
            this.piid = piid;
            this.names = names;
        }

        public String nameOf(int code) {
            Map<Integer, String> n = names;
            String name = n == null ? null : n.get(code);
            return name != null ? name : "unknown_" + code;
        }
    }

    /** Eine Aktion mit fester siid/aiid. */
    public static final class Reset {
        public final int siid;
        public final int aiid;

        Reset(int siid, int aiid) {
            this.siid = siid;
            this.aiid = aiid;
        }
    }

    public static final List<Sensor> SENSORS = buildSensors();

    /** Zurücksetzen: Name -> Aktion (Tasshack: RESET_*). */
    public static final Map<String, Reset> RESETS = buildResets();

    /**
     * Basisstations-Befehle: Name -> Parameter für Aktion 4/4 (Eigenschaft 4/10), z. B. "3,1" = Mopp trocknen.
     * "empty_dust" ist keine 4/4-Aktion, sondern Aktion 15/1.
     */
    public static final Map<String, String> BASE_COMMANDS = buildBaseCommands();

    public static final int[][] READ_PAIRS = buildReadPairs();

    private MovaStation() {
    }

    private static Map<Integer, String> names(String... n) {
        Map<Integer, String> m = new LinkedHashMap<>();
        for (int i = 0; i < n.length; i++) {
            m.put(i, n[i]);
        }
        return m;
    }

    private static List<Sensor> buildSensors() {
        List<Sensor> l = new ArrayList<>();
        l.add(new Sensor("clean-water-tank", "String", "station-status-channel", "Frischwassertank", Kind.ENUM, 27, 1,
                names("installed", "not_installed", "low_water", "checking")));
        l.add(new Sensor("dirty-water-tank", "String", "station-status-channel", "Schmutzwassertank", Kind.ENUM, 27, 2,
                names("installed", "not_installed_or_full")));
        l.add(new Sensor("dust-bag", "String", "station-status-channel", "Staubbeutel", Kind.ENUM, 27, 3,
                names("installed", "not_installed", "check")));
        l.add(new Sensor("detergent-status", "String", "station-status-channel", "Reinigungsmittel", Kind.ENUM, 27, 4,
                names("installed", "disabled", "low_detergent")));
        l.add(new Sensor("base-status", "String", "station-status-channel", "Station Status", Kind.ENUM, 4, 25,
                names("idle", "washing", "drying", "returning", "paused", "clean_add_water", "returning_to_drain",
                        "returning_for_dry_mop")));
        l.add(new Sensor("auto-empty-status", "String", "station-status-channel", "Absaugstatus", Kind.ENUM, 15, 5,
                names("idle", "active", "not_performed", "returning")));
        l.add(new Sensor("dust-collection", "String", "station-status-channel", "Absaugen möglich", Kind.ENUM, 15, 3,
                names("not_available", "available", "over_use", "never")));
        l.add(new Sensor("dust-bag-drying-status", "String", "station-status-channel", "Staubbeutel-Trocknung",
                Kind.ENUM, 27, 18, names("idle", "drying", "paused")));
        l.add(new Sensor("drainage-status", "String", "station-status-channel", "Wasser ablassen", Kind.ENUM, 4, 60,
                names("idle", "draining", "draining_success", "draining_failed")));
        l.add(new Sensor("drying-progress", "Number:Dimensionless", "station-percent-channel", "Trocknungsfortschritt",
                Kind.PERCENT, 4, 64, null));
        l.add(new Sensor("mop-in-station", "Switch", "station-flag-channel", "Mopp in Station", Kind.FLAG, 4, 52, null));
        return l;
    }

    private static Map<String, Reset> buildResets() {
        Map<String, Reset> m = new LinkedHashMap<>();
        m.put("main_brush", new Reset(9, 1));
        m.put("side_brush", new Reset(10, 1));
        m.put("filter", new Reset(11, 1));
        m.put("sensor", new Reset(16, 1));
        m.put("wheel", new Reset(30, 1));
        m.put("scale_inhibitor", new Reset(31, 1));
        m.put("washboard", new Reset(37, 1));
        return m;
    }

    private static Map<String, String> buildBaseCommands() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("wash_mop", "2,1");
        m.put("pause_wash", "1,0");
        m.put("resume_wash", "1,1");
        m.put("dry_mop", "3,1");
        m.put("stop_dry_mop", "3,0");
        m.put("dry_dust_bag", "14,1");
        m.put("stop_dry_dust_bag", "14,0");
        m.put("drain_dirty_water", "7,1");
        m.put("empty_clean_tank", "9,1");
        m.put("empty_dust", "");
        return m;
    }

    private static int[][] buildReadPairs() {
        List<int[]> pairs = new ArrayList<>();
        pairs.add(new int[] { SIID_WETNESS, PIID_WETNESS });
        pairs.add(new int[] { SIID_CHARGE_LIMIT, PIID_CHARGE_LIMIT });
        pairs.add(new int[] { SIID_OFF_PEAK, PIID_OFF_PEAK });
        for (Sensor s : SENSORS) {
            pairs.add(new int[] { s.siid, s.piid });
        }
        return pairs.toArray(new int[0][]);
    }

    // ---- Reinigungsmodus (Tasshack, mop_pad_lifting) ----

    /** Niedrigste 2 Bit von 4/23: 0 sweeping_and_mopping, 1 mopping, 2 sweeping, 3 mopping_after_sweeping. */
    public static String modeName(int raw) {
        return switch (raw & 3) {
            case 0 -> "sweeping_and_mopping";
            case 1 -> "mopping";
            case 2 -> "sweeping";
            default -> "mopping_after_sweeping";
        };
    }

    /** Liefert die 2 Bit zu einem Modusnamen oder -1. */
    public static int modeCode(String name) {
        return switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "sweeping_and_mopping" -> 0;
            case "mopping" -> 1;
            case "sweeping" -> 2;
            case "mopping_after_sweeping" -> 3;
            default -> -1;
        };
    }
}
