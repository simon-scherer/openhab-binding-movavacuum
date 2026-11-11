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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * Konstanten für das Mova/Dreame-Vacuum-Binding.
 *
 * Alle Cloud-Werte (Hosts, Kennungen, Header, siid/piid/aiid) stammen aus dem
 * quelloffenen "Tasshack/dreame-vacuum" Home-Assistant-Plugin, Stand Tag v2.0.1
 * (DreameVacuumDreameHomeCloudProtocol). Sie sind nicht offiziell von
 * Dreame/Mova dokumentiert und können sich jederzeit ändern.
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public class MovaBindingConstants {

    public static final String BINDING_ID = "movavacuum";

    public static final ThingTypeUID THING_TYPE_ACCOUNT = new ThingTypeUID(BINDING_ID, "account");
    public static final ThingTypeUID THING_TYPE_VACUUM = new ThingTypeUID(BINDING_ID, "vacuum");

    // Channel-IDs
    public static final String CHANNEL_STATE = "state";
    public static final String CHANNEL_STATUS = "status";
    public static final String CHANNEL_BATTERY = "battery";
    public static final String CHANNEL_CHARGING = "charging";
    public static final String CHANNEL_ERROR = "error";
    public static final String CHANNEL_COMMAND = "command";
    public static final String CHANNEL_SUCTION = "suction";
    public static final String CHANNEL_VOLUME = "volume";
    public static final String CHANNEL_DND = "dnd";
    public static final String CHANNEL_CLEANING_MODE_RAW = "cleaning-mode-raw";
    public static final String CHANNEL_WATER_RAW = "water-raw";
    public static final String CHANNEL_CLEANING_TIME = "cleaning-time";
    public static final String CHANNEL_CLEANED_AREA = "cleaned-area";
    public static final String CHANNEL_TOTAL_CLEANING_TIME = "total-cleaning-time";
    public static final String CHANNEL_TOTAL_CLEANED_AREA = "total-cleaned-area";
    public static final String CHANNEL_CLEANING_COUNT = "cleaning-count";
    public static final String CHANNEL_MAIN_BRUSH_LEFT = "main-brush-left";
    public static final String CHANNEL_SIDE_BRUSH_LEFT = "side-brush-left";
    public static final String CHANNEL_FILTER_LEFT = "filter-left";
    public static final String CHANNEL_SENSOR_LEFT = "sensor-left";
    public static final String CHANNEL_MOP_PAD_LEFT = "mop-pad-left"; // entfernt, nur für Aufräumen alter Things
    public static final String CHANNEL_WHEEL_LEFT = "wheel-left";
    public static final String CHANNEL_WHEEL_TIME_LEFT = "wheel-time-left";
    public static final String CHANNEL_WASHBOARD_LEFT = "washboard-left";
    public static final String CHANNEL_WASHBOARD_TIME_LEFT = "washboard-time-left";
    public static final String CHANNEL_SCALE_INHIBITOR_LEFT = "scale-inhibitor-left";
    public static final String CHANNEL_SCALE_INHIBITOR_TIME_LEFT = "scale-inhibitor-time-left";
    public static final String CHANNEL_CLEAN_ROOMS = "clean-rooms";
    public static final String CHANNEL_CLEAN_ZONE = "clean-zone";
    public static final String CHANNEL_CLEAN_SPOT = "clean-spot";
    public static final String CHANNEL_GO_TO = "go-to";
    public static final String CHANNEL_ROOM_EDIT = "room-edit";
    public static final String CHANNEL_MAP_COMMAND = "map-command";
    public static final String CHANNEL_ROOM_PROGRESS = "room-progress";
    public static final String CHANNEL_CURRENT_ROOM = "current-room";
    public static final String CHANNEL_OBSTACLES = "obstacles";
    public static final String CHANNEL_OBSTACLE_IGNORE = "obstacle-ignore";
    public static final String CHANNEL_REMOTE_CONTROL = "remote-control";
    public static final String CHANNEL_SHORTCUTS = "shortcuts";
    public static final String CHANNEL_SHORTCUT = "shortcut";
    public static final String CHANNEL_ROOMS = "rooms";
    public static final String CHANNEL_MAP_REFRESH = "map-refresh";

    // Account-Typen
    public static final String ACCOUNT_MOVA = "mova";
    public static final String ACCOUNT_DREAME = "dreame";
    public static final String ACCOUNT_TROUVER = "trouver";

    // Pfade (relativ zur API-Basis-URL https://<country><host-suffix>:<port>)
    public static final String API_PORT = "13267";
    public static final String LOGIN_PATH = "/dreame-auth/oauth/token";
    public static final String DEVICE_LIST_PATH = "dreame-user-iot/iotuserbind/device/listV2";
    public static final String DEVICE_INFO_PATH = "dreame-user-iot/iotuserbind/device/info";
    /** Der Host-Anteil des Gerats wird als "-<erstes Label von bindDomain>" angehängt. */
    public static final String SEND_COMMAND_SERVICE = "dreame-iot-com";
    public static final String SEND_COMMAND_PATH = "device/sendCommand";

    // Hosts je Account-Typ
    public static final String HOST_SUFFIX_DREAME = ".iot.dreame.tech";
    public static final String HOST_SUFFIX_MOVA = ".iot.mova-tech.com";
    public static final String HOST_SUFFIX_TROUVER = ".iot.trouver-tech.com";

    // Salz für das Passwort-MD5 (gilt für alle Marken)
    public static final String PASSWORD_SALT = "RAylYC%fmSKp7%Tq";

    // Je Marke: Basic-Auth-Kennung (Base64), Client-Geheimnis (sign + AES-Schlüssel),
    // Versions-ID, User-Agent, Standard-Tenant-Id
    public static final String BASIC_DREAME = "ZHJlYW1lX2FwcHYxOkFQXmR2QHpAU1FZVnhOODg=";
    public static final String BASIC_MOVA = "bW92YV9hcHA6VjdLb0NoTFc4dkhBQ3FHYg==";
    public static final String BASIC_TROUVER = "dHJvdXZlcl9hcHA6NXB0MGV6d0w1TzRHMG96aw==";

    public static final String CLIENT_SECRET_DREAME = "EETjszu*XI5znHsI";
    public static final String CLIENT_SECRET_MOVA = "gigxlmqwZ]7oWZUF";
    public static final String CLIENT_SECRET_TROUVER = "MMd02jyRqzuf1QBS";

    public static final String VERSION_ID_DREAME = "102060300";
    public static final String VERSION_ID_MOVA = "10605530";
    public static final String VERSION_ID_TROUVER = "10300532";

    public static final String USER_AGENT_DREAME = "Dart/3.9 (dart:io)";
    public static final String USER_AGENT_MOVA = "Dart/3.10 (dart:io)";
    public static final String USER_AGENT_TROUVER = "Dart/3.6 (dart:io)";

    public static final String TENANT_ID_DREAME = "000000";
    public static final String TENANT_ID_MOVA = "000002";
    public static final String TENANT_ID_TROUVER = "000005";

    // MIoT siid/piid für die Basisfunktionen (DreameVacuumPropertyMapping, unverändert v2.0.1)
    public static final int SIID_DEVICE = 2;
    public static final int PIID_STATE = 1;
    public static final int PIID_ERROR = 2;

    public static final int SIID_BATTERY = 3;
    public static final int PIID_BATTERY_LEVEL = 1;
    public static final int PIID_CHARGING_STATUS = 2;

    public static final int SIID_VACUUM = 4;
    public static final int PIID_STATUS = 1;

    // MIoT siid/aiid für Befehle (DreameVacuumActionMapping, unverändert v2.0.1)
    public static final int AIID_START = 1; // siid 2
    public static final int AIID_PAUSE = 2; // siid 2
    public static final int AIID_CHARGE = 1; // siid 3 (= andocken)
    public static final int AIID_STOP = 2; // siid 4
    public static final int SIID_LOCATE = 7;
    public static final int AIID_LOCATE = 1; // siid 7

    // DreameVacuumState Codes
    public static final int STATE_SWEEPING = 1;
    public static final int STATE_IDLE = 2;
    public static final int STATE_PAUSED = 3;
    public static final int STATE_ERROR = 4;
    public static final int STATE_RETURNING = 5;
    public static final int STATE_CHARGING = 6;
    public static final int STATE_MOPPING = 7;
    public static final int STATE_DRYING = 8;
    public static final int STATE_WASHING = 9;
    public static final int STATE_SWEEPING_AND_MOPPING = 12;

    // Weitere MIoT-IDs (DreameVacuumPropertyMapping, v2.0.1)
    public static final int PIID_CLEANING_TIME = 2; // siid 4, Minuten
    public static final int PIID_CLEANED_AREA = 3; // siid 4, m2
    public static final int PIID_SUCTION_LEVEL = 4; // siid 4
    public static final int PIID_WATER_VOLUME = 5; // siid 4
    public static final int PIID_CLEANING_PROPERTIES = 10; // siid 4 (Parameter für START_CUSTOM)
    public static final int PIID_CLEANING_MODE = 23; // siid 4 (gepackter Wert)
    public static final int SIID_DND = 5;
    public static final int PIID_DND = 1;
    public static final int SIID_VOLUME = 7;
    public static final int PIID_VOLUME = 1;
    public static final int SIID_STATS = 12;
    public static final int PIID_TOTAL_CLEANING_TIME = 2;
    public static final int PIID_CLEANING_COUNT = 3;
    public static final int PIID_TOTAL_CLEANED_AREA = 4;
    public static final int SIID_MAIN_BRUSH = 9; // piid 2 = Rest in %
    public static final int SIID_SIDE_BRUSH = 10; // piid 2 = Rest in %
    public static final int SIID_FILTER = 11; // piid 1 = Rest in %
    public static final int SIID_SENSOR = 16; // piid 1 = Rest in %
    public static final int SIID_WHEEL = 30; // piid 1 = Restzeit in Stunden, piid 2 = Rest in %
    public static final int SIID_SCALE_INHIBITOR = 31; // piid 1 = Restzeit in Stunden, piid 2 = Rest in %
    public static final int SIID_WASHBOARD = 37; // piid 1 = Restzeit in Stunden, piid 2 = Rest in % (Tasshack 2.0.3)

    // Action START_CUSTOM (siid 4 / aiid 1) mit Status-Wert 18 = Raumreinigung
    public static final int AIID_START_CUSTOM = 1;
    public static final int STATUS_SEGMENT_CLEANING = 18;
    public static final int STATUS_ZONE_CLEANING = 19;
    public static final int STATUS_CRUISING_POINT = 23;
    public static final int STATUS_SHORTCUT = 25;
    public static final int PIID_REMOTE_CONTROL = 15; // siid 4
    public static final int PIID_SHORTCUTS = 48; // siid 4 (Liste der Shortcuts)
    public static final int AIID_SHORTCUTS = 8; // siid 4
    public static final int STATUS_SPOT_CLEANING = 20;
}
