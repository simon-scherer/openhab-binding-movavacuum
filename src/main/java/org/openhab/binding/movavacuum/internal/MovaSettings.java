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
 * Teppich- und Bodeneinstellungen. Kennungen (siid/piid, Schlüssel der "Auto-Switch"-Einstellungen,
 * Bit der KI-Einstellung) stammen aus Tasshack/dreame-vacuum v2.0.1. Welche davon ein bestimmtes Modell
 * wirklich unterstützt, zeigt erst der Betrieb: nicht unterstützte Einstellungen bleiben leer (NULL).
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public final class MovaSettings {

    /** PROP = eigene Property (siid/piid), AUTO = Eintrag in der JSON-Liste 4/50, AI_BIT = Bit in 4/22. */
    public enum Kind {
        PROP,
        AUTO,
        AI_BIT
    }

    public static final int SIID_AUTO_SWITCH = 4;
    public static final int PIID_AUTO_SWITCH = 50;
    public static final int SIID_AI = 4;
    public static final int PIID_AI = 22;

    public static final String CHANNEL_CARPET_CLEANING = "carpet-cleaning";
    public static final String CHANNEL_OBSTACLE_AVOIDANCE = "obstacle-avoidance";
    public static final String CHANNEL_STAIN_CLEANING = "intelligent-stain-cleaning";
    public static final String CHANNEL_DYNAMIC_OBSTACLE = "dynamic-obstacle-cleaning";

    /** Beschreibung einer Einstellung. */
    public static final class Def {
        public final String channel;
        public final String itemType;
        public final String channelTypeId;
        public final String label;
        public final Kind kind;
        public final int siid;
        public final int piid;
        public final String key;
        public final int bit;
        public final @Nullable Map<Integer, String> names;

        Def(String channel, String itemType, String channelTypeId, String label, Kind kind, int siid, int piid,
                String key, int bit, @Nullable Map<Integer, String> names) {
            this.channel = channel;
            this.itemType = itemType;
            this.channelTypeId = channelTypeId;
            this.label = label;
            this.kind = kind;
            this.siid = siid;
            this.piid = piid;
            this.key = key;
            this.bit = bit;
            this.names = names;
        }

        /** Liefert den Code zu einem Namen oder null. */
        public @Nullable Integer codeOf(String text) {
            Map<Integer, String> n = names;
            if (n == null) {
                return null;
            }
            for (Map.Entry<Integer, String> e : n.entrySet()) {
                if (e.getValue().equalsIgnoreCase(text.trim())) {
                    return e.getKey();
                }
            }
            return null;
        }

        public String nameOf(int code) {
            Map<Integer, String> n = names;
            String name = n == null ? null : n.get(code);
            return name != null ? name : "unknown (" + code + ")";
        }
    }

    public static final List<Def> DEFS = buildDefs();

    /** Zusätzlich nur zur Diagnose gelesen (Rohwerte im Log). */
    private static final int[][] EXTRA_READ = { { 28, 58 }, { 28, 106 }, { 28, 99 }, { 15, 2 }, { 15, 3 }, { 28, 95 }, { 28, 66 },
            { 28, 67 }, { 28, 68 }, { 27, 17 }, { 27, 18 }, { 27, 21 }, { 4, 37 }, { 4, 25 }, { 4, 52 },
            { 4, 11 }, { 4, 59 }, { 27, 43 }, { 27, 44 }, { 28, 109 }, { 28, 14 } };

    public static final int[][] READ_PAIRS = buildReadPairs();

    private MovaSettings() {
    }

    private static List<Def> buildDefs() {
        Map<Integer, String> carpet = new LinkedHashMap<>();
        carpet.put(1, "avoidance");
        carpet.put(2, "adaptation");
        carpet.put(3, "remove_mop");
        carpet.put(4, "adaptation_without_route");
        carpet.put(5, "vacuum_and_mop");
        carpet.put(7, "cross");
        Map<Integer, String> lowLying = new LinkedHashMap<>();
        lowLying.put(0, "weekly");
        lowLying.put(1, "daily");
        Map<Integer, String> lds = new LinkedHashMap<>();
        lds.put(0, "off");
        lds.put(1, "security");
        lds.put(2, "extreme");

        List<Def> l = new ArrayList<>();
        // Teppich
        l.add(new Def(CHANNEL_CARPET_CLEANING, "String", "carpet-cleaning-channel", "Teppichreinigung", Kind.PROP, 4,
                36, "", 0, carpet));
        l.add(sw("carpet-boost", "Teppich-Boost", Kind.PROP, 4, 12, "", 0));
        l.add(sw("carpets-first", "Zuerst Teppiche reinigen", Kind.PROP, 28, 2, "", 0));
        l.add(sw("side-brush-carpet-rotate", "Seitenbürste rotiert auf Teppich", Kind.PROP, 28, 29, "", 0));
        l.add(sw("intensive-carpet-cleaning", "Intensive Teppichreinigung", Kind.AUTO, 4, 50, "CarpetFineClean", 0));
        // Boden
        l.add(new Def("auto-lds", "String", "auto-lds-channel", "LDS automatisch anheben", Kind.PROP, 28, 3, "", 0,
                lds));
        l.add(new Def("low-lying-frequency", "String", "low-lying-frequency-channel",
                "Reinigungshäufigkeit niedrige Bereiche", Kind.PROP, 28, 51, "", 0, lowLying));
        l.add(sw("collision-avoidance", "Kollisionsvermeidungsmodus", Kind.AUTO, 4, 50, "LessColl", 0));
        l.add(sw("auto-mount-mop", "Mopp-Pads automatisch demontieren/montieren", Kind.PROP, 4, 45, "", 0));
        l.add(sw("floor-direction-cleaning", "Reinigung in Bodenrichtung", Kind.AUTO, 4, 50, "MaterialDirectionClean",
                0));
        l.add(sw("dynamic-obstacle-cleaning", "Dynamische Reinigung bei Hindernissen", Kind.PROP, 28, 18, "", 0));
        // Wischpad-Erweiterung (KI)
        Map<Integer, String> extend = new LinkedHashMap<>();
        extend.put(7, "standard");
        extend.put(1, "intelligent");
        extend.put(2, "high");
        l.add(sw("side-reach", "Seitenbürste ausfahren (Side Reach)", Kind.AUTO, 4, 50, "SbrushExtrSwitch", 0));
        l.add(sw("mop-extend", "Mopp-Pad ausfahren (Mop Extend)", Kind.AUTO, 4, 50, "MopExtrSwitch", 0));
        l.add(new Def("mop-extend-frequency", "String", "mop-extend-frequency-channel", "Häufigkeit Mop Extend",
                Kind.AUTO, 4, 50, "ExtrFreq", 0, extend));
        l.add(sw("gap-cleaning-extension", "Spalten-Reinigung erweitern", Kind.AUTO, 4, 50, "LacuneMopScalable", 0));
        l.add(sw("mopping-under-furniture", "Intensive Reinigung für Möbelfüße", Kind.AUTO, 4, 50, "MopScalable2", 0));
        // Basisstation
        Map<Integer, String> autoEmpty = new LinkedHashMap<>();
        autoEmpty.put(0, "off");
        autoEmpty.put(1, "standard");
        autoEmpty.put(2, "intelligent");
        autoEmpty.put(3, "low_frequency");
        autoEmpty.put(4, "custom_frequency");
        autoEmpty.put(5, "high_frequency");
        Map<Integer, String> washLevel = new LinkedHashMap<>();
        washLevel.put(0, "water_saving");
        washLevel.put(1, "daily");
        washLevel.put(2, "deep");
        Map<Integer, String> scraper = new LinkedHashMap<>();
        scraper.put(0, "off");
        scraper.put(1, "weekly");
        scraper.put(2, "daily");
        Map<Integer, String> temperature = new LinkedHashMap<>();
        temperature.put(0, "normal");
        temperature.put(1, "mild");
        temperature.put(2, "warm");
        temperature.put(3, "hot");
        temperature.put(4, "max");
        Map<Integer, String> selfClean = new LinkedHashMap<>();
        selfClean.put(1, "by_area");
        selfClean.put(2, "by_time");
        selfClean.put(3, "by_room");
        selfClean.put(4, "intelligent");
        Map<Integer, String> moppingType = new LinkedHashMap<>();
        moppingType.put(0, "daily");
        moppingType.put(1, "accurate");
        moppingType.put(2, "deep");
        Map<Integer, String> dryingTime = new LinkedHashMap<>();
        dryingTime.put(1, "1h");
        dryingTime.put(2, "2h");
        dryingTime.put(3, "3h");
        dryingTime.put(4, "4h");
        l.add(new Def("drying-time", "String", "drying-time-channel", "Trocknungsdauer", Kind.PROP, 4, 40, "", 0,
                dryingTime));
        l.add(sw("child-lock", "Kindersicherung", Kind.PROP, 4, 27, "", 0));
        l.add(new Def("auto-empty-mode", "String", "auto-empty-mode-channel", "Automatisches Entleeren", Kind.PROP, 15,
                1, "", 0, autoEmpty));
        l.add(sw("dnd-disable-auto-empty", "Entleeren im Nicht-stören-Modus aus", Kind.PROP, 28, 15, "", 0));
        l.add(sw("auto-drying", "Automatisches Trocknen", Kind.AUTO, 4, 50, "AutoDry", 0));
        l.add(sw("smart-drying", "Intelligentes Trocknen", Kind.AUTO, 4, 50, "SmartDrying", 0));
        l.add(sw("silent-drying", "Leises Trocknen", Kind.PROP, 28, 27, "", 0));
        l.add(sw("dust-bag-drying", "Staubbeutel trocknen", Kind.PROP, 28, 48, "", 0));
        l.add(sw("self-clean", "Selbstreinigung der Station", Kind.PROP, 4, 34, "", 0));
        l.add(sw("water-electrolysis", "Wasser-Elektrolyse", Kind.PROP, 4, 32, "", 0));
        l.add(sw("mopping-with-detergent", "Wischen mit Reinigungsmittel", Kind.PROP, 28, 52, "", 0));
        l.add(sw("smart-mop-washing", "Intelligentes Wischpad-Waschen", Kind.PROP, 28, 22, "", 0));
        l.add(sw("hot-washing", "Heißwaschen", Kind.AUTO, 4, 50, "HotWash", 0));
        l.add(sw("uv-sterilization", "UV-Desinfektion", Kind.AUTO, 4, 50, "UVLight", 0));
        l.add(sw("ultra-clean-mode", "Ultra-Reinigungsmodus", Kind.AUTO, 4, 50, "SuperWash", 0));
        l.add(new Def("mop-wash-level", "String", "mop-wash-level-channel", "Wischpad-Waschstufe", Kind.PROP, 4, 46, "",
                0, washLevel));
        l.add(new Def("scraper-frequency", "String", "scraper-frequency-channel", "Häufigkeit Abstreifer", Kind.PROP,
                28, 54, "", 0, scraper));
        l.add(new Def("water-temperature", "String", "water-temperature-channel", "Wassertemperatur", Kind.PROP, 28, 8,
                "", 0, temperature));
        l.add(new Def("self-clean-frequency", "String", "self-clean-frequency-channel", "Selbstreinigung Häufigkeit",
                Kind.AUTO, 4, 50, "BackWashType", 0, selfClean));
        l.add(new Def("mopping-type", "String", "mopping-type-channel", "Wischart", Kind.AUTO, 4, 50, "CleanType", 0,
                moppingType));
        // CleanGenius (Auto-Switch "SmartHost", Modus in 28/5)
        Map<Integer, String> genius = new LinkedHashMap<>();
        genius.put(0, "off");
        genius.put(1, "routine_cleaning");
        genius.put(2, "deep_cleaning");
        Map<Integer, String> geniusMode = new LinkedHashMap<>();
        geniusMode.put(2, "vacuum_and_mop");
        geniusMode.put(3, "mop_after_vacuum");
        l.add(new Def("cleangenius", "String", "cleangenius-channel", "CleanGenius", Kind.AUTO, 4, 50, "SmartHost", 0,
                genius));
        l.add(new Def("cleangenius-mode", "String", "cleangenius-mode-channel", "CleanGenius Modus", Kind.PROP, 28, 5,
                "", 0, geniusMode));
        // Hindernisse überwinden
        l.add(sw("obstacle-crossing", "Synchronisierte Hindernisüberquerung", Kind.PROP, 28, 38, "", 0));
        l.add(sw("clearance-leg-crossing", "Hürdenartige Hindernisüberquerung", Kind.PROP, 28, 98, "", 0));
        l.add(sw("active-suspension-crossing", "Aktive Federung beim Überwinden", Kind.PROP, 28, 84, "", 0));
        l.add(sw("lift-chassis-on-carpet", "Chassis auf Teppich anheben", Kind.PROP, 28, 60, "", 0));
        l.add(sw("close-roller-cover-on-carpet", "Rollenabdeckung auf Teppich schließen", Kind.PROP, 28, 69, "", 0));
        l.add(sw(CHANNEL_OBSTACLE_AVOIDANCE, "Intelligente Hindernisvermeidung", Kind.PROP, 4, 21, "", 0));
        l.add(sw(CHANNEL_STAIN_CLEANING, "Intelligente Fleckenentfernung", Kind.PROP, 28, 83, "", 0));
        l.add(sw("pet-detection", "Erkennung von Haustieren", Kind.AI_BIT, 4, 22, "", 16));
        l.add(sw("obstacle-pictures", "Bilder der Hindernisse", Kind.AI_BIT, 4, 22, "", 4));
        l.add(sw("fill-light", "Fülllicht", Kind.AUTO, 4, 50, "FillinLight", 0));
        l.add(sw("resume-cleaning", "Reinigung fortsetzen", Kind.PROP, 4, 11, "", 0));
        l.add(sw("mop-washing-with-detergent", "Moppwaschen mit Reinigungsmittel", Kind.PROP, 4, 37, "", 0));
        l.add(sw("large-particles", "Erkennung großer Schmutzpartikel", Kind.AI_BIT, 4, 22, "", 2048));
        return List.copyOf(l);
    }

    private static Def sw(String channel, String label, Kind kind, int siid, int piid, String key, int bit) {
        return new Def(channel, "Switch", "setting-switch-channel", label, kind, siid, piid, key, bit, null);
    }

    private static int[][] buildReadPairs() {
        List<int[]> pairs = new ArrayList<>();
        pairs.add(new int[] { 4, 33 }); // Teppicherkennung (für Teppichreinigung/Ignorieren)
        pairs.add(new int[] { SIID_AUTO_SWITCH, PIID_AUTO_SWITCH });
        pairs.add(new int[] { SIID_AI, PIID_AI });
        for (Def d : DEFS) {
            if (d.kind == Kind.PROP) {
                pairs.add(new int[] { d.siid, d.piid });
            }
        }
        for (int[] p : EXTRA_READ) {
            pairs.add(p);
        }
        for (int[] p : MovaStation.READ_PAIRS) {
            pairs.add(p);
        }
        return pairs.toArray(new int[0][]);
    }

    public static @Nullable Def find(String channel) {
        for (Def d : DEFS) {
            if (d.channel.equals(channel)) {
                return d;
            }
        }
        return null;
    }
}
