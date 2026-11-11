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

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * English labels of the channels the handler creates at runtime. The German label is given where the
 * channel is defined; {@link #label(String, String)} picks the one matching the openHAB language.
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public final class MovaLabels {
    private static final Map<String, String> EN = Map.ofEntries(Map.entry("suction", "Suction Level"),
            Map.entry("volume", "Volume"), Map.entry("dnd", "Do Not Disturb"),
            Map.entry("cleaning-mode-raw", "Cleaning Mode (raw)"), Map.entry("water-raw", "Water Level (raw)"),
            Map.entry("cleaning-time", "Cleaning Time"), Map.entry("cleaned-area", "Cleaned Area"),
            Map.entry("total-cleaning-time", "Total Cleaning Time"),
            Map.entry("total-cleaned-area", "Total Cleaned Area"), Map.entry("cleaning-count", "Cleaning Count"),
            Map.entry("main-brush-left", "Main Brush Left"), Map.entry("side-brush-left", "Side Brush Left"),
            Map.entry("filter-left", "Filter Left"), Map.entry("sensor-left", "Sensors Left"),
            Map.entry("mop-pad-left", "Mop Pad Left"), Map.entry("wheel-left", "Wheels Left"),
            Map.entry("wheel-time-left", "Wheels Time Left"), Map.entry("washboard-left", "Washboard Cleaning Left"),
            Map.entry("washboard-time-left", "Washboard Cleaning Time Left"),
            Map.entry("scale-inhibitor-left", "Scale Inhibitor Left"),
            Map.entry("scale-inhibitor-time-left", "Scale Inhibitor Time Left"),
            Map.entry("clean-rooms", "Clean Rooms"), Map.entry("clean-zone", "Clean Zones"),
            Map.entry("clean-spot", "Clean Spots"), Map.entry("go-to", "Go To Point"),
            Map.entry("room-edit", "Edit Rooms"), Map.entry("remote-control", "Remote Control"),
            Map.entry("shortcuts", "Shortcuts"), Map.entry("shortcut", "Shortcut Command"),
            Map.entry("room-zones", "Room Coordinates"), Map.entry("robot-position", "Robot Position"),
            Map.entry("map-command", "Map Command"), Map.entry("room-progress", "Room Progress"),
            Map.entry("current-room", "Current Room"), Map.entry("obstacles", "Detected Obstacles"),
            Map.entry("obstacle-ignore", "Ignore Obstacle"), Map.entry("rooms", "Rooms"),
            Map.entry("map-refresh", "Refresh Map"), Map.entry("cleaning-mode", "Cleaning Mode"),
            Map.entry("water-level", "Mop Water Level (1-32)"), Map.entry("base-command", "Base Station Command"),
            Map.entry("charge-limit", "Charge Limit"), Map.entry("off-peak-charging", "Off-Peak Charging"),
            Map.entry("off-peak-start", "Off-Peak Start"), Map.entry("off-peak-end", "Off-Peak End"),
            Map.entry("map-image", "Map"), Map.entry("map-image-dark", "Map (dark)"), Map.entry("room-info", "Room Settings"), Map.entry("charging", "Charging"),
            Map.entry("room-settings", "Change Room Settings"), Map.entry("error-text", "Error Text"),
            Map.entry("last-cleaning", "Last Cleaning"), Map.entry("cleaning-history", "Cleaning History"),
            Map.entry("reset-consumable", "Reset Consumable"),
            // settings
            Map.entry("carpet-cleaning", "Carpet Cleaning"), Map.entry("carpet-boost", "Carpet Boost"),
            Map.entry("carpets-first", "Clean Carpets First"),
            Map.entry("side-brush-carpet-rotate", "Side Brush Rotates on Carpet"),
            Map.entry("intensive-carpet-cleaning", "Intensive Carpet Cleaning"),
            Map.entry("auto-lds", "Auto Lift LDS"), Map.entry("low-lying-frequency", "Low-Lying Areas Frequency"),
            Map.entry("collision-avoidance", "Collision Avoidance Mode"),
            Map.entry("auto-mount-mop", "Auto Detach/Attach Mop Pads"),
            Map.entry("floor-direction-cleaning", "Clean Along Floor Direction"),
            Map.entry("dynamic-obstacle-cleaning", "Dynamic Cleaning Around Obstacles"),
            Map.entry("side-reach", "Side Brush Extend (Side Reach)"),
            Map.entry("mop-extend", "Mop Pad Extend (Mop Extend)"),
            Map.entry("mop-extend-frequency", "Mop Extend Frequency"),
            Map.entry("gap-cleaning-extension", "Gap Cleaning Extension"),
            Map.entry("mopping-under-furniture", "Intensive Cleaning Around Furniture Legs"),
            Map.entry("drying-time", "Drying Time"), Map.entry("child-lock", "Child Lock"),
            Map.entry("auto-empty-mode", "Auto Empty"),
            Map.entry("dnd-disable-auto-empty", "No Auto Empty During Do Not Disturb"),
            Map.entry("auto-drying", "Auto Drying"), Map.entry("smart-drying", "Smart Drying"),
            Map.entry("silent-drying", "Silent Drying"), Map.entry("dust-bag-drying", "Dust Bag Drying"),
            Map.entry("self-clean", "Station Self-Cleaning"), Map.entry("water-electrolysis", "Water Electrolysis"),
            Map.entry("mopping-with-detergent", "Mopping With Detergent"),
            Map.entry("smart-mop-washing", "Smart Mop Washing"), Map.entry("hot-washing", "Hot Washing"),
            Map.entry("uv-sterilization", "UV Sterilization"), Map.entry("ultra-clean-mode", "Ultra Clean Mode"),
            Map.entry("mop-wash-level", "Mop Wash Level"), Map.entry("scraper-frequency", "Scraper Frequency"),
            Map.entry("water-temperature", "Water Temperature"),
            Map.entry("self-clean-frequency", "Self-Clean Frequency"), Map.entry("mopping-type", "Mopping Type"),
            Map.entry("cleangenius", "CleanGenius"), Map.entry("cleangenius-mode", "CleanGenius Mode"),
            Map.entry("obstacle-crossing", "Synchronized Obstacle Crossing"),
            Map.entry("clearance-leg-crossing", "Hurdle Crossing"),
            Map.entry("active-suspension-crossing", "Active Suspension Crossing"),
            Map.entry("lift-chassis-on-carpet", "Lift Chassis on Carpet"),
            Map.entry("close-roller-cover-on-carpet", "Close Roller Cover on Carpet"),
            Map.entry("obstacle-avoidance", "Smart Obstacle Avoidance"),
            Map.entry("intelligent-stain-cleaning", "Smart Stain Cleaning"),
            Map.entry("pet-detection", "Pet Detection"), Map.entry("obstacle-pictures", "Obstacle Pictures"),
            Map.entry("fill-light", "Fill Light"), Map.entry("resume-cleaning", "Resume Cleaning"),
            Map.entry("mop-washing-with-detergent", "Mop Washing With Detergent"),
            Map.entry("large-particles", "Large Particle Detection"),
            // station sensors
            Map.entry("clean-water-tank", "Clean Water Tank"), Map.entry("dirty-water-tank", "Dirty Water Tank"),
            Map.entry("dust-bag", "Dust Bag"), Map.entry("detergent-status", "Detergent"),
            Map.entry("base-status", "Station Status"), Map.entry("auto-empty-status", "Auto Empty Status"),
            Map.entry("dust-collection", "Dust Collection Possible"),
            Map.entry("dust-bag-drying-status", "Dust Bag Drying"), Map.entry("drainage-status", "Draining"),
            Map.entry("drying-progress", "Drying Progress"), Map.entry("mop-in-station", "Mop in Station"));

    private MovaLabels() {
    }

    /** German label as given, or the English one if openHAB is not set to German. */
    public static String label(String channelId, String germanLabel) {
        return MovaI18n.isGerman() ? germanLabel : EN.getOrDefault(channelId, germanLabel);
    }
}
