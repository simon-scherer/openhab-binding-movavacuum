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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Textnamen der Zustands-Codes (DreameVacuumState, Tasshack v2.0.1).
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public final class MovaStates {

    private static final Map<Integer, String> NAMES = new HashMap<>();

    static {
        Map<Integer, String> m = NAMES;
        m.put(1, "sweeping");
        m.put(2, "idle");
        m.put(3, "paused");
        m.put(4, "error");
        m.put(5, "returning");
        m.put(6, "charging");
        m.put(7, "mopping");
        m.put(8, "drying");
        m.put(9, "washing");
        m.put(10, "returning_to_wash");
        m.put(11, "building");
        m.put(12, "sweeping_and_mopping");
        m.put(13, "charging_completed");
        m.put(14, "upgrading");
        m.put(15, "clean_summon");
        m.put(16, "station_reset");
        m.put(17, "returning_install_mop");
        m.put(18, "returning_remove_mop");
        m.put(19, "water_check");
        m.put(20, "clean_add_water");
        m.put(21, "washing_paused");
        m.put(22, "auto_emptying");
        m.put(23, "remote_control");
        m.put(24, "smart_charging");
        m.put(25, "second_cleaning");
        m.put(26, "human_following");
        m.put(27, "spot_cleaning");
        m.put(28, "returning_auto_empty");
        m.put(29, "waiting_for_task");
        m.put(30, "station_cleaning");
        m.put(31, "returning_to_drain");
        m.put(32, "draining");
        m.put(33, "auto_water_draining");
        m.put(34, "emptying");
        m.put(35, "dust_bag_drying");
        m.put(36, "dust_bag_drying_paused");
        m.put(37, "heading_to_extra_cleaning");
        m.put(38, "extra_cleaning");
        m.put(95, "finding_pet_paused");
        m.put(96, "finding_pet");
        m.put(97, "shortcut");
        m.put(98, "monitoring");
        m.put(99, "monitoring_paused");
        m.put(101, "initial_deep_cleaning");
        m.put(102, "initial_deep_cleaning_paused");
        m.put(103, "sanitizing");
        m.put(104, "sanitizing_with_dry");
        m.put(105, "changing_mop");
        m.put(106, "changing_mop_paused");
        m.put(107, "floor_maintaining");
        m.put(108, "floor_maintaining_paused");
        m.put(109, "remote_pickup");
        m.put(113, "arranging_items");
        m.put(114, "pet_guarding");
        m.put(115, "pet_guarding_paused");
        m.put(116, "installing_mop");
        m.put(117, "uninstalling_mop");
        m.put(118, "intelligent_recharging");
        m.put(120, "assisted_cleaning");
        m.put(121, "entering_dock");
        m.put(122, "leaving_dock");
        m.put(140, "navigating_to_climber");
        m.put(141, "docking_to_climber");
        m.put(142, "climber_docked");
        m.put(143, "climber_navigating");
        m.put(144, "climbing_stairs");
        m.put(145, "climbing_stairs_completed");
        m.put(146, "climber_at_dock");
        m.put(147, "climber_leaving_dock");
    }

    private MovaStates() {
    }

    public static String name(int code) {
        String n = NAMES.get(code);
        return n != null ? n : "unknown (" + code + ")";
    }

    /** true, wenn der Sauger gerade saugt oder wischt (Reinigung läuft). */
    public static boolean isCleaning(int code) {
        return code == 1 || code == 7 || code == 12 || code == 25 || code == 27 || code == 38;
    }
}
