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
package org.openhab.binding.movavacuum.internal.dto;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Ein einzelner Geräte-Eintrag aus der device/listV2 Antwort (data.page.records[]).
 * Gson befüllt die Felder anhand der JSON-Namen; unbekannte Felder werden ignoriert.
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public class DeviceRecord {
    public @Nullable String did;
    public @Nullable String model;
    public @Nullable String mac;
    public @Nullable String customName;
    /** Host-Anteil des Geräts, z.B. "awsde0.iot.dreame.tech" - das erste Label wird für sendCommand gebraucht. */
    public @Nullable String bindDomain;
    /** Benutzer-ID des Geräte-Besitzers (für Verlaufs-Abfragen). */
    public @Nullable String masterUid;
    public @Nullable DeviceInfo deviceInfo;

    public static class DeviceInfo {
        public @Nullable String displayName;
    }

    public String getDisplayName() {
        String custom = customName;
        if (custom != null && !custom.isBlank()) {
            return custom;
        }
        DeviceInfo info = deviceInfo;
        if (info != null) {
            String name = info.displayName;
            if (name != null) {
                return name;
            }
        }
        String id = did;
        return id != null ? id : "unknown";
    }
}
