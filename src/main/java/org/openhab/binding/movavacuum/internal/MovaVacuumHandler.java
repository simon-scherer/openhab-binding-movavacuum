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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import java.io.IOException;

import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.RawType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Handler für ein einzelnes Vacuum-Thing: fragt periodisch Zustand, Einstellungen, Statistik und
 * (seltener) Verbrauchsmaterial ab und setzt Befehle und Einstellungen über MIoT ab.
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public class MovaVacuumHandler extends BaseThingHandler {

    private static final int[][] POLL_PROPERTIES = {
            { MovaBindingConstants.SIID_DEVICE, MovaBindingConstants.PIID_STATE },
            { MovaBindingConstants.SIID_DEVICE, MovaBindingConstants.PIID_ERROR },
            { MovaBindingConstants.SIID_BATTERY, MovaBindingConstants.PIID_BATTERY_LEVEL },
            { MovaBindingConstants.SIID_BATTERY, MovaBindingConstants.PIID_CHARGING_STATUS },
            { MovaBindingConstants.SIID_VACUUM, MovaBindingConstants.PIID_STATUS },
            { MovaBindingConstants.SIID_VACUUM, MovaBindingConstants.PIID_CLEANING_TIME },
            { MovaBindingConstants.SIID_VACUUM, MovaBindingConstants.PIID_CLEANED_AREA },
            { MovaBindingConstants.SIID_VACUUM, MovaBindingConstants.PIID_SUCTION_LEVEL },
            { MovaBindingConstants.SIID_VACUUM, MovaBindingConstants.PIID_WATER_VOLUME },
            { MovaBindingConstants.SIID_VACUUM, MovaBindingConstants.PIID_CLEANING_MODE },
            { MovaBindingConstants.SIID_DND, MovaBindingConstants.PIID_DND },
            { MovaBindingConstants.SIID_VOLUME, MovaBindingConstants.PIID_VOLUME },
            { MovaBindingConstants.SIID_STATS, MovaBindingConstants.PIID_TOTAL_CLEANING_TIME },
            { MovaBindingConstants.SIID_STATS, MovaBindingConstants.PIID_CLEANING_COUNT },
            { MovaBindingConstants.SIID_STATS, MovaBindingConstants.PIID_TOTAL_CLEANED_AREA } };

    /** Verbrauchsmaterial wird nur jeden 10. Durchlauf abgefragt (ca. alle 3-4 Minuten). */
    private static final int[][] CONSUMABLE_PROPERTIES = { { MovaBindingConstants.SIID_MAIN_BRUSH, 2 },
            { MovaBindingConstants.SIID_SIDE_BRUSH, 2 }, { MovaBindingConstants.SIID_FILTER, 1 },
            { MovaBindingConstants.SIID_SENSOR, 1 }, { MovaBindingConstants.SIID_WHEEL, 1 },
            { MovaBindingConstants.SIID_WHEEL, 2 }, { MovaBindingConstants.SIID_SCALE_INHIBITOR, 1 },
            { MovaBindingConstants.SIID_SCALE_INHIBITOR, 2 }, { MovaBindingConstants.SIID_WASHBOARD, 1 },
            { MovaBindingConstants.SIID_WASHBOARD, 2 } };

    private static final int CONSUMABLE_EVERY_N_POLLS = 10;

    /** Kanäle, die ab Version 2 dazugekommen sind: {id, itemType, channelTypeId, label}. */
    private static final String[][] EXTRA_CHANNELS = {
            { MovaBindingConstants.CHANNEL_SUCTION, "String", "suction-channel", "Saugstufe" },
            { MovaBindingConstants.CHANNEL_VOLUME, "Number:Dimensionless", "volume-channel", "Lautstärke" },
            { MovaBindingConstants.CHANNEL_DND, "Switch", "dnd-channel", "Nicht stören" },
            { MovaBindingConstants.CHANNEL_CLEANING_MODE_RAW, "Number", "cleaning-mode-raw-channel", "Reinigungsmodus (roh)" },
            { MovaBindingConstants.CHANNEL_WATER_RAW, "Number", "water-raw-channel", "Wasserstufe (roh)" },
            { MovaBindingConstants.CHANNEL_CLEANING_TIME, "Number:Time", "cleaning-time-channel", "Reinigungsdauer" },
            { MovaBindingConstants.CHANNEL_CLEANED_AREA, "Number:Area", "cleaned-area-channel", "Gereinigte Fläche" },
            { MovaBindingConstants.CHANNEL_TOTAL_CLEANING_TIME, "Number:Time", "total-cleaning-time-channel", "Gesamte Reinigungsdauer" },
            { MovaBindingConstants.CHANNEL_TOTAL_CLEANED_AREA, "Number:Area", "total-cleaned-area-channel", "Gesamte gereinigte Fläche" },
            { MovaBindingConstants.CHANNEL_CLEANING_COUNT, "Number", "cleaning-count-channel", "Anzahl Reinigungen" },
            { MovaBindingConstants.CHANNEL_MAIN_BRUSH_LEFT, "Number:Dimensionless", "consumable-channel", "Hauptbürste Rest" },
            { MovaBindingConstants.CHANNEL_SIDE_BRUSH_LEFT, "Number:Dimensionless", "consumable-channel", "Seitenbürste Rest" },
            { MovaBindingConstants.CHANNEL_FILTER_LEFT, "Number:Dimensionless", "consumable-channel", "Filter Rest" },
            { MovaBindingConstants.CHANNEL_SENSOR_LEFT, "Number:Dimensionless", "consumable-channel", "Sensoren Rest" },
            { MovaBindingConstants.CHANNEL_WHEEL_LEFT, "Number:Dimensionless", "consumable-channel", "Räder Rest" },
            { MovaBindingConstants.CHANNEL_WHEEL_TIME_LEFT, "Number:Time", "consumable-time-channel", "Räder Restzeit" },
            { MovaBindingConstants.CHANNEL_SCALE_INHIBITOR_LEFT, "Number:Dimensionless", "consumable-channel", "Kalkschutz Rest" },
            { MovaBindingConstants.CHANNEL_SCALE_INHIBITOR_TIME_LEFT, "Number:Time", "consumable-time-channel", "Kalkschutz Restzeit" },
            { MovaBindingConstants.CHANNEL_CLEAN_ROOMS, "String", "clean-rooms-channel", "Räume reinigen" },
            { MovaBindingConstants.CHANNEL_CLEAN_ZONE, "String", "clean-zone-channel", "Zonen reinigen" },
            { MovaBindingConstants.CHANNEL_CLEAN_SPOT, "String", "clean-spot-channel", "Punkte reinigen" },
            { MovaBindingConstants.CHANNEL_GO_TO, "String", "go-to-channel", "Zu Punkt fahren" },
            { MovaBindingConstants.CHANNEL_ROOM_EDIT, "String", "room-edit-channel", "Räume bearbeiten" },
            { MovaBindingConstants.CHANNEL_REMOTE_CONTROL, "String", "remote-control-channel", "Fernsteuerung" },
            { MovaBindingConstants.CHANNEL_SHORTCUTS, "String", "shortcuts-channel", "Shortcuts" },
            { MovaBindingConstants.CHANNEL_SHORTCUT, "String", "shortcut-channel", "Shortcut-Befehl" },
            { MovaStation.CHANNEL_ROOM_ZONES, "String", "room-zones-channel", "Raum-Koordinaten" },
            { MovaStation.CHANNEL_ROBOT_POSITION, "String", "robot-position-channel", "Roboter-Position" },
            { MovaBindingConstants.CHANNEL_MAP_COMMAND, "String", "map-command-channel", "Karten-Befehl" },
            { MovaBindingConstants.CHANNEL_ROOM_PROGRESS, "String", "room-progress-channel", "Reinigungsfortschritt je Raum" },
            { MovaBindingConstants.CHANNEL_CURRENT_ROOM, "String", "current-room-channel", "Aktueller Raum" },
            { MovaBindingConstants.CHANNEL_OBSTACLES, "String", "obstacles-channel", "Erkannte Hindernisse" },
            { MovaBindingConstants.CHANNEL_OBSTACLE_IGNORE, "String", "obstacle-ignore-channel", "Hindernis ignorieren" },
            { MovaBindingConstants.CHANNEL_ROOMS, "String", "rooms-channel", "Räume" },
            { MovaBindingConstants.CHANNEL_MAP_REFRESH, "Switch", "map-refresh-channel", "Karte neu laden" },
            { MovaStation.CHANNEL_CLEANING_MODE, "String", "cleaning-mode-channel", "Reinigungsmodus" },
            { MovaStation.CHANNEL_WATER_LEVEL, "Number", "water-level-channel", "Wasserstufe Mopp (1-32)" },
            { MovaStation.CHANNEL_BASE_COMMAND, "String", "base-command-channel", "Basisstation Befehl" },
            { MovaStation.CHANNEL_CHARGE_LIMIT, "Number", "charge-limit-channel", "Ladelimit" },
            { MovaStation.CHANNEL_OFF_PEAK, "Switch", "setting-switch-channel", "Niedertarif-Laden" },
            { MovaStation.CHANNEL_OFF_PEAK_START, "String", "off-peak-time-channel", "Niedertarif Beginn" },
            { MovaStation.CHANNEL_OFF_PEAK_END, "String", "off-peak-time-channel", "Niedertarif Ende" },
            { MovaStation.CHANNEL_MAP_IMAGE, "Image", "map-image-channel", "Karte" },
            { MovaStation.CHANNEL_MAP_IMAGE_DARK, "Image", "map-image-dark-channel", "Karte (dunkel)" },
            { MovaStation.CHANNEL_ROOM_INFO, "String", "room-info-channel", "Raum-Einstellungen" },
            { MovaBindingConstants.CHANNEL_CHARGING, "Switch", "charging-channel", "Lädt" },
            { MovaStation.CHANNEL_ROOM_SETTINGS, "String", "room-settings-channel", "Raum-Einstellungen ändern" },
            { MovaStation.CHANNEL_ERROR_TEXT, "String", "error-text-channel", "Fehlertext" },
            { MovaStation.CHANNEL_LAST_CLEANING, "String", "last-cleaning-channel", "Letzte Reinigung" },
            { MovaStation.CHANNEL_CLEANING_HISTORY, "String", "cleaning-history-channel", "Reinigungsverlauf" },
            { MovaStation.CHANNEL_RESET, "String", "reset-consumable-channel", "Verbrauchsmaterial zurücksetzen" } };

    private final Logger logger = LoggerFactory.getLogger(MovaVacuumHandler.class);

    private @Nullable ScheduledFuture<?> pollJob;
    private String did = "";
    private boolean loggedRawResponseOnce = false;
    private boolean loggedConsumablesOnce = false;
    private boolean loggedSettingsOnce = false;
    private volatile boolean settingsDue = false;
    private long settingsIntervalMs = 20_000;
    private static final long MAP_INTERVAL_MS = 30 * 60_000L;
    private static final long MAP_AFTER_CLEANING_MS = 2 * 60_000L;
    /** Kartenabruf während Reinigung/Rückfahrt (für die Roboter-Position). */
    private static final long MAP_LIVE_MS = 60_000L;
    private static final long HISTORY_INTERVAL_MS = 6 * 3_600_000L;
    private static final long HISTORY_AFTER_CLEANING_MS = 60_000L;
    private static final int HISTORY_ENTRIES = 10;
    private static final long MAP_RETRY_MS = 5 * 60_000L;
    private final AtomicBoolean mapLoading = new AtomicBoolean(false);
    private final Map<String, Integer> roomIdsByName = new ConcurrentHashMap<>();
    private final Map<Integer, MovaMap.Room> roomsById = new ConcurrentHashMap<>();
    private final Map<String, Integer> shortcutIdsByName = new ConcurrentHashMap<>();
    private final Map<Integer, String> shortcutNamesById = new ConcurrentHashMap<>();
    private volatile MovaMap.@Nullable Result lastMap;
    private volatile long nextMapLoad = 0;
    private volatile boolean wasCleaning = false;
    private volatile long nextHistoryLoad = 0;
    private volatile @Nullable JsonObject rawCleanset;
    private final AtomicBoolean historyLoading = new AtomicBoolean(false);
    private boolean loggedHistoryOnce = false;
    private volatile @Nullable String lastCleaningText;
    private volatile @Nullable String historyText;
    private String mapKeyOverride = "";
    private int mapRotation = -1;
    private boolean mapFlip = true;
    private boolean showHiddenRooms = false;
    private int mapSize = 600;
    private String mapLabels = "name";
    private long lastSettingsPoll = 0;
    private final Map<String, JsonElement> settingsRaw = new ConcurrentHashMap<>();
    private final Object aiBitsLock = new Object();
    private final Map<String, Integer> autoSwitch = new ConcurrentHashMap<>();
    private int pollCounter = 0;
    private volatile @Nullable Integer lastCleaningModeRaw;
    private int lastSuction = 1;
    private int lastWater = 2;

    public MovaVacuumHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        MovaVacuumConfiguration config = getConfigAs(MovaVacuumConfiguration.class);
        did = config.did.trim();
        settingsIntervalMs = Math.max(10, config.settingsInterval) * 1000L;
        mapKeyOverride = config.mapKey.trim();
        mapRotation = config.mapRotation;
        mapFlip = config.mapFlip;
        showHiddenRooms = config.showHiddenRooms;
        mapSize = config.mapSize;
        mapLabels = config.mapLabels;
        if (did.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Device ID (did) missing - see the openHAB log when the account bridge starts");
            return;
        }
        ensureChannels();
        updateStatus(ThingStatus.UNKNOWN);

        int intervalSeconds = 20;
        Bridge bridge = getBridge();
        if (bridge != null) {
            MovaAccountConfiguration accountConfig = bridge.getConfiguration().as(MovaAccountConfiguration.class);
            intervalSeconds = Math.max(10, accountConfig.pollingInterval);
        }
        pollCounter = 0;
        pollJob = scheduler.scheduleWithFixedDelay(this::poll, 3, intervalSeconds, TimeUnit.SECONDS);
    }

    /** Ergänzt bei bestehenden Things die ab Version 2 neuen Kanäle, ohne dass das Thing neu angelegt werden muss. */
    private void ensureChannels() {
        ThingBuilder builder = editThing();
        boolean changed = false;
        for (String[] def : EXTRA_CHANNELS) {
            changed |= syncChannel(builder, def[0], def[1], def[2], def[3]);
        }
        for (MovaSettings.Def d : MovaSettings.DEFS) {
            changed |= syncChannel(builder, d.channel, d.itemType, d.channelTypeId, d.label);
        }
        for (MovaStation.Sensor sensor : MovaStation.SENSORS) {
            changed |= syncChannel(builder, sensor.channel, sensor.itemType, sensor.channelTypeId, sensor.label);
        }
        ChannelUID mopPad = new ChannelUID(thing.getUID(), MovaBindingConstants.CHANNEL_MOP_PAD_LEFT);
        if (thing.getChannel(MovaBindingConstants.CHANNEL_MOP_PAD_LEFT) != null) {
            builder.withoutChannel(mopPad);
            changed = true;
        }
        if (changed) {
            updateThing(builder.build());
        }
    }

    /** Legt einen fehlenden Kanal an bzw. passt die Beschriftung eines bestehenden Kanals an (Links bleiben erhalten). */
    private boolean syncChannel(ThingBuilder builder, String id, String itemType, String typeId, String germanLabel) {
        String label = MovaLabels.label(id, germanLabel);
        org.openhab.core.thing.Channel existing = thing.getChannel(id);
        if (existing != null && label.equals(existing.getLabel())) {
            return false;
        }
        ChannelUID uid = new ChannelUID(thing.getUID(), id);
        if (existing != null) {
            builder.withoutChannel(uid);
        }
        builder.withChannel(ChannelBuilder.create(uid, itemType)
                .withType(new ChannelTypeUID(MovaBindingConstants.BINDING_ID, typeId)).withLabel(label).build());
        return true;
    }

    private @Nullable MovaCloudApi getCloudApi() {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof MovaAccountHandler accountHandler) {
            return accountHandler.getCloudApi();
        }
        return null;
    }

    private void poll() {
        Bridge bridge = getBridge();
        if (bridge == null || bridge.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "Account bridge is not online");
            return;
        }
        MovaCloudApi api = getCloudApi();
        if (api == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
            return;
        }
        try {
            Map<String, JsonElement> values = fetch(api, POLL_PROPERTIES);
            if (values == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "No response from the device (property query)");
                return;
            }
            if (!loggedRawResponseOnce) {
                logger.debug("Mova get_properties raw values (once, for diagnostics): {}", values);
                loggedRawResponseOnce = true;
            }
            publishMain(values);
            updateStatus(ThingStatus.ONLINE);
            checkCleaningFinished(values);

            if (pollCounter % CONSUMABLE_EVERY_N_POLLS == 0) {
                Map<String, JsonElement> consumables = fetch(api, CONSUMABLE_PROPERTIES);
                if (consumables != null) {
                    if (!loggedConsumablesOnce) {
                        logger.debug("Mova consumables raw values (once, for diagnostics): {}", consumables);
                        loggedConsumablesOnce = true;
                    }
                    publishConsumables(consumables);
                }
            }
            long now = System.currentTimeMillis();
            if (settingsDue || now - lastSettingsPoll >= settingsIntervalMs - 1000) {
                settingsDue = false;
                lastSettingsPoll = now;
                pollSettings(api);
            }
            if (System.currentTimeMillis() >= nextMapLoad && mapLoading.compareAndSet(false, true)) {
                nextMapLoad = System.currentTimeMillis() + MAP_INTERVAL_MS;
                scheduler.execute(() -> loadMap(api));
            }
            if (System.currentTimeMillis() >= nextHistoryLoad && historyLoading.compareAndSet(false, true)) {
                nextHistoryLoad = System.currentTimeMillis() + HISTORY_INTERVAL_MS;
                scheduler.execute(() -> loadHistory(api));
            }
            pollCounter++;
        } catch (MovaCloudException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (RuntimeException e) {
            // darf den Abfrage-Job nicht beenden (ein ausgefallener Job läuft nicht weiter)
            logger.warn("Unexpected error while polling: {}", e.toString(), e);
        }
    }

    /** Lädt die Karte kurz nach Ende einer Reinigung neu, damit "zuletzt gereinigt" zeitnah stimmt. */
    private void checkCleaningFinished(Map<String, JsonElement> values) {
        Integer state = intValue(values, MovaBindingConstants.SIID_DEVICE, MovaBindingConstants.PIID_STATE);
        if (state == null) {
            return;
        }
        String name = MovaStates.name(state);
        boolean cleaning = name.startsWith("sweeping") || name.startsWith("mopping");
        if (wasCleaning && !cleaning) {
            nextMapLoad = Math.min(nextMapLoad, System.currentTimeMillis() + MAP_AFTER_CLEANING_MS);
            nextHistoryLoad = Math.min(nextHistoryLoad, System.currentTimeMillis() + HISTORY_AFTER_CLEANING_MS);
        }
        wasCleaning = cleaning;
        if (cleaning || name.startsWith("returning")) {
            nextMapLoad = Math.min(nextMapLoad, System.currentTimeMillis() + MAP_LIVE_MS);
        }
    }

    private void publishMain(Map<String, JsonElement> v) {
        int dev = MovaBindingConstants.SIID_DEVICE;
        int bat = MovaBindingConstants.SIID_BATTERY;
        int vac = MovaBindingConstants.SIID_VACUUM;
        int stats = MovaBindingConstants.SIID_STATS;

        push(MovaBindingConstants.CHANNEL_STATE, intValue(v, dev, MovaBindingConstants.PIID_STATE),
                x -> new StringType(MovaStates.name(x)));
        push(MovaBindingConstants.CHANNEL_ERROR, intValue(v, dev, MovaBindingConstants.PIID_ERROR),
                x -> new DecimalType(x));
        push(MovaStation.CHANNEL_ERROR_TEXT, intValue(v, dev, MovaBindingConstants.PIID_ERROR),
                x -> new StringType(MovaErrors.text(x)));
        push(MovaBindingConstants.CHANNEL_BATTERY, intValue(v, bat, MovaBindingConstants.PIID_BATTERY_LEVEL),
                x -> new QuantityType<>(x, Units.PERCENT));
        push(MovaBindingConstants.CHANNEL_CHARGING, intValue(v, bat, MovaBindingConstants.PIID_CHARGING_STATUS),
                x -> x != 0 ? OnOffType.ON : OnOffType.OFF);
        push(MovaBindingConstants.CHANNEL_STATUS, intValue(v, vac, MovaBindingConstants.PIID_STATUS),
                x -> new DecimalType(x));
        push(MovaBindingConstants.CHANNEL_CLEANING_TIME, intValue(v, vac, MovaBindingConstants.PIID_CLEANING_TIME),
                x -> new QuantityType<>(x, Units.MINUTE));
        push(MovaBindingConstants.CHANNEL_CLEANED_AREA, intValue(v, vac, MovaBindingConstants.PIID_CLEANED_AREA),
                x -> new QuantityType<>(x, SIUnits.SQUARE_METRE));

        Integer suction = intValue(v, vac, MovaBindingConstants.PIID_SUCTION_LEVEL);
        if (suction != null) {
            lastSuction = suction;
        }
        push(MovaBindingConstants.CHANNEL_SUCTION, suction, x -> new StringType(suctionToText(x)));

        Integer water = intValue(v, vac, MovaBindingConstants.PIID_WATER_VOLUME);
        if (water != null) {
            lastWater = water;
        }
        push(MovaBindingConstants.CHANNEL_WATER_RAW, water, x -> new DecimalType(x));
        Integer modeRaw = intValue(v, vac, MovaBindingConstants.PIID_CLEANING_MODE);
        if (modeRaw != null) {
            lastCleaningModeRaw = modeRaw;
        }
        push(MovaBindingConstants.CHANNEL_CLEANING_MODE_RAW, modeRaw, x -> new DecimalType(x));
        push(MovaStation.CHANNEL_CLEANING_MODE, modeRaw, x -> new StringType(MovaStation.modeName(x)));
        push(MovaBindingConstants.CHANNEL_DND,
                intValue(v, MovaBindingConstants.SIID_DND, MovaBindingConstants.PIID_DND),
                x -> x != 0 ? OnOffType.ON : OnOffType.OFF);
        push(MovaBindingConstants.CHANNEL_VOLUME,
                intValue(v, MovaBindingConstants.SIID_VOLUME, MovaBindingConstants.PIID_VOLUME),
                x -> new QuantityType<>(x, Units.PERCENT));
        push(MovaBindingConstants.CHANNEL_TOTAL_CLEANING_TIME,
                intValue(v, stats, MovaBindingConstants.PIID_TOTAL_CLEANING_TIME),
                x -> new QuantityType<>(x, Units.MINUTE));
        push(MovaBindingConstants.CHANNEL_CLEANING_COUNT, intValue(v, stats, MovaBindingConstants.PIID_CLEANING_COUNT),
                x -> new DecimalType(x));
        push(MovaBindingConstants.CHANNEL_TOTAL_CLEANED_AREA,
                intValue(v, stats, MovaBindingConstants.PIID_TOTAL_CLEANED_AREA),
                x -> new QuantityType<>(x, SIUnits.SQUARE_METRE));
    }

    private void publishConsumables(Map<String, JsonElement> v) {
        push(MovaBindingConstants.CHANNEL_MAIN_BRUSH_LEFT, intValue(v, MovaBindingConstants.SIID_MAIN_BRUSH, 2),
                x -> new QuantityType<>(x, Units.PERCENT));
        push(MovaBindingConstants.CHANNEL_SIDE_BRUSH_LEFT, intValue(v, MovaBindingConstants.SIID_SIDE_BRUSH, 2),
                x -> new QuantityType<>(x, Units.PERCENT));
        push(MovaBindingConstants.CHANNEL_FILTER_LEFT, intValue(v, MovaBindingConstants.SIID_FILTER, 1),
                x -> new QuantityType<>(x, Units.PERCENT));
        push(MovaBindingConstants.CHANNEL_SENSOR_LEFT, intValue(v, MovaBindingConstants.SIID_SENSOR, 1),
                x -> new QuantityType<>(x, Units.PERCENT));
        push(MovaBindingConstants.CHANNEL_WHEEL_TIME_LEFT, intValue(v, MovaBindingConstants.SIID_WHEEL, 1),
                x -> new QuantityType<>(x, Units.HOUR));
        push(MovaBindingConstants.CHANNEL_WHEEL_LEFT, intValue(v, MovaBindingConstants.SIID_WHEEL, 2),
                x -> new QuantityType<>(x, Units.PERCENT));
        push(MovaBindingConstants.CHANNEL_SCALE_INHIBITOR_TIME_LEFT,
                intValue(v, MovaBindingConstants.SIID_SCALE_INHIBITOR, 1), x -> new QuantityType<>(x, Units.HOUR));
        push(MovaBindingConstants.CHANNEL_SCALE_INHIBITOR_LEFT,
                intValue(v, MovaBindingConstants.SIID_SCALE_INHIBITOR, 2), x -> new QuantityType<>(x, Units.PERCENT));
        // Waschbrett-Reinigung (Tasshack 2.0.3, 37/1 Restzeit h, 37/2 Rest %): Kanäle nur anlegen, wenn das Gerät
        // die Werte liefert
        Integer wbTime = intValue(v, MovaBindingConstants.SIID_WASHBOARD, 1);
        Integer wbLeft = intValue(v, MovaBindingConstants.SIID_WASHBOARD, 2);
        if (wbTime != null || wbLeft != null) {
            ThingBuilder builder = editThing();
            boolean changed = syncChannel(builder, MovaBindingConstants.CHANNEL_WASHBOARD_LEFT, "Number:Dimensionless",
                    "consumable-channel", "Waschbrett-Reinigung Rest");
            changed |= syncChannel(builder, MovaBindingConstants.CHANNEL_WASHBOARD_TIME_LEFT, "Number:Time",
                    "consumable-time-channel", "Waschbrett-Reinigung Restzeit");
            if (changed) {
                updateThing(builder.build());
            }
            push(MovaBindingConstants.CHANNEL_WASHBOARD_TIME_LEFT, wbTime, x -> new QuantityType<>(x, Units.HOUR));
            push(MovaBindingConstants.CHANNEL_WASHBOARD_LEFT, wbLeft, x -> new QuantityType<>(x, Units.PERCENT));
        }
    }

    /** Aktualisiert einen Kanal; ein Fehler bei einem Kanal stoppt weder die anderen noch die Abfrage. */
    private void push(String channel, @Nullable Integer value, Function<Integer, State> toState) {
        if (value == null) {
            return;
        }
        try {
            updateState(channel, toState.apply(value));
        } catch (RuntimeException e) {
            logger.warn("Could not update channel {} with value {}: {}", channel, value,
                    e.toString(), e);
        }
    }

    /**
     * Fragt die Properties ab und liefert nur Einträge mit code == 0 als Map "siid.piid" -> Wert.
     * Gibt null zurück, wenn die Cloud gar nicht geantwortet hat.
     */
    private @Nullable Map<String, JsonElement> fetch(MovaCloudApi api, int[][] pairs) throws MovaCloudException {
        Map<String, JsonElement> result = new HashMap<>();
        boolean any = false;
        // In kleinen Paketen abfragen: große Pakete liefert die Cloud teils nur unvollständig zurück
        final int chunk = 8;
        for (int from = 0; from < pairs.length; from += chunk) {
            int to = Math.min(pairs.length, from + chunk);
            int[][] part = java.util.Arrays.copyOfRange(pairs, from, to);
            List<JsonObject> list = api.getProperties(did, part);
            if (list == null) {
                continue;
            }
            any = true;
            for (JsonObject entry : list) {
                try {
                    if (!entry.has("siid") || !entry.has("piid")) {
                        continue;
                    }
                    String key = entry.get("siid").getAsInt() + "." + entry.get("piid").getAsInt();
                    JsonElement code = entry.get("code");
                    if (code != null && !code.isJsonNull() && code.getAsInt() != 0) {
                        if (loggedRejected.add(key)) {
                            logger.debug("Mova property {} rejected: {}", key, entry);
                        }
                        continue;
                    }
                    JsonElement value = entry.get("value");
                    if (value == null || value.isJsonNull()) {
                        if (loggedRejected.add(key)) {
                            logger.debug("Mova property {} without value: {}", key, entry);
                        }
                        continue;
                    }
                    result.put(key, value);
                } catch (RuntimeException e) {
                    logger.debug("Entry does not match the expected schema: {}", entry);
                }
            }
        }
        return any ? result : null;
    }

    private @Nullable Integer intValue(Map<String, JsonElement> values, int siid, int piid) {
        JsonElement value = values.get(siid + "." + piid);
        if (value == null) {
            return null;
        }
        try {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
                return value.getAsBoolean() ? 1 : 0;
            }
            return value.getAsInt();
        } catch (RuntimeException e) {
            logger.debug("Value {}.{} is not a number: {}", siid, piid, value);
            return null;
        }
    }

    private String suctionToText(int level) {
        return switch (level) {
            case 0 -> "quiet";
            case 1 -> "standard";
            case 2 -> "strong";
            case 3 -> "turbo";
            default -> "unknown (" + level + ")";
        };
    }

    private int suctionToCode(String text) {
        return switch (text.toLowerCase()) {
            case "quiet" -> 0;
            case "standard" -> 1;
            case "strong" -> 2;
            case "turbo" -> 3;
            default -> -1;
        };
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            String id = channelUID.getId();
            if (MovaStation.CHANNEL_LAST_CLEANING.equals(id) || MovaStation.CHANNEL_CLEANING_HISTORY.equals(id)) {
                if (lastCleaningText != null) {
                    publishHistory();
                } else {
                    nextHistoryLoad = 0;
                }
                return;
            }
            if (MovaBindingConstants.CHANNEL_ROOMS.equals(id) || MovaStation.CHANNEL_ROOM_INFO.equals(id)
                    || MovaStation.CHANNEL_ROOM_ZONES.equals(id) || MovaStation.CHANNEL_ROBOT_POSITION.equals(id)
                    || MovaBindingConstants.CHANNEL_SHORTCUTS.equals(id) || MovaStation.CHANNEL_MAP_IMAGE.equals(id)
                    || MovaStation.CHANNEL_MAP_IMAGE_DARK.equals(id)) {
                MovaCloudApi mapApi = getCloudApi();
                if (mapApi != null) {
                    triggerMapLoad(mapApi);
                }
                return;
            }
            scheduler.execute(this::poll);
            return;
        }
        MovaCloudApi api = getCloudApi();
        if (api == null) {
            logger.warn("Cannot send command: account bridge not ready");
            return;
        }
        try {
            boolean ok;
            switch (channelUID.getId()) {
                case MovaBindingConstants.CHANNEL_COMMAND:
                    ok = handleSimpleCommand(api, command.toString());
                    break;
                case MovaBindingConstants.CHANNEL_SUCTION:
                    ok = handleSuction(api, command.toString());
                    break;
                case MovaBindingConstants.CHANNEL_VOLUME:
                    ok = handleVolume(api, command);
                    break;
                case MovaBindingConstants.CHANNEL_ROOM_EDIT:
                    ok = handleRoomEdit(api, command.toString());
                    break;
                case MovaBindingConstants.CHANNEL_OBSTACLES:
                case MovaBindingConstants.CHANNEL_ROOM_PROGRESS:
                case MovaBindingConstants.CHANNEL_CURRENT_ROOM:
                    return;
                case MovaBindingConstants.CHANNEL_MAP_COMMAND:
                    ok = handleMapCommand(api, command.toString());
                    break;
                case MovaBindingConstants.CHANNEL_OBSTACLE_IGNORE:
                    ok = handleObstacleIgnore(api, command.toString());
                    break;
                case MovaBindingConstants.CHANNEL_GO_TO:
                    ok = handleGoTo(api, command.toString());
                    break;
                case MovaBindingConstants.CHANNEL_REMOTE_CONTROL:
                    ok = handleRemoteControl(api, command.toString());
                    break;
                case MovaBindingConstants.CHANNEL_SHORTCUTS:
                    return;
                case MovaBindingConstants.CHANNEL_SHORTCUT:
                    ok = handleShortcut(api, command.toString());
                    break;
                case MovaBindingConstants.CHANNEL_CLEAN_SPOT:
                    ok = handleCleanSpot(api, command.toString());
                    break;
                case MovaBindingConstants.CHANNEL_CLEAN_ZONE:
                    ok = handleCleanZone(api, command.toString());
                    break;
                case MovaBindingConstants.CHANNEL_CLEAN_ROOMS:
                    ok = handleCleanRooms(api, command.toString());
                    break;
                case MovaStation.CHANNEL_ROOM_SETTINGS:
                    ok = handleRoomSettings(api, command.toString());
                    break;
                case MovaBindingConstants.CHANNEL_MAP_REFRESH:
                    if (command == OnOffType.ON) {
                        triggerMapLoad(api);
                    }
                    updateState(MovaBindingConstants.CHANNEL_MAP_REFRESH, OnOffType.OFF);
                    return;
                case MovaBindingConstants.CHANNEL_ROOMS:
                    return;
                case MovaStation.CHANNEL_CLEANING_MODE:
                    ok = handleCleaningMode(api, command.toString());
                    break;
                case MovaStation.CHANNEL_WATER_LEVEL:
                    ok = handleWaterLevel(api, command);
                    break;
                case MovaStation.CHANNEL_BASE_COMMAND:
                    ok = handleBaseCommand(api, command.toString());
                    break;
                case MovaStation.CHANNEL_CHARGE_LIMIT:
                    ok = handleChargeLimit(api, command);
                    break;
                case MovaStation.CHANNEL_OFF_PEAK:
                case MovaStation.CHANNEL_OFF_PEAK_START:
                case MovaStation.CHANNEL_OFF_PEAK_END:
                    ok = handleOffPeak(api, channelUID.getId(), command);
                    break;
                case MovaStation.CHANNEL_RESET:
                    ok = handleReset(api, command.toString());
                    break;
                default:
                    MovaSettings.Def setting = MovaSettings.find(channelUID.getId());
                    if (setting == null) {
                        return;
                    }
                    ok = handleSetting(api, setting, command);
                    break;
            }
            if (ok) {
                settingsDue = true;
                scheduler.schedule(this::poll, 3, TimeUnit.SECONDS);
            } else {
                logger.warn("Command '{}' for channel {} was not confirmed by the device/cloud", command,
                        channelUID.getId());
            }
        } catch (MovaCloudException e) {
            logger.warn("Command '{}' for channel {} failed: {}", command, channelUID.getId(), e.getMessage());
        }
    }


    private final java.util.Set<String> loggedRejected = new java.util.HashSet<>();

    private void pollSettings(MovaCloudApi api) throws MovaCloudException {
        Map<String, JsonElement> s = fetch(api, MovaSettings.READ_PAIRS);
        if (s == null) {
            return;
        }
        if (!loggedSettingsOnce) {
            logger.debug("Mova settings raw values (once, for diagnostics): {}", s);
            loggedSettingsOnce = true;
        }
        Map<String, JsonElement> previousRaw = new HashMap<>(settingsRaw);
        Map<String, Integer> previousAuto = new HashMap<>(autoSwitch);
        settingsRaw.clear();
        settingsRaw.putAll(s);
        parseAutoSwitch(s.get(MovaSettings.SIID_AUTO_SWITCH + "." + MovaSettings.PIID_AUTO_SWITCH));
        if (loggedSettingsOnce && !previousRaw.isEmpty()) {
            logSettingChanges(previousRaw, previousAuto);
        }
        publishSettings();
    }


    /** Schreibt jede geänderte Einstellung einzeln ins DEBUG-Log (hilft beim Zuordnen von App-Schaltern). */
    private void logSettingChanges(Map<String, JsonElement> previousRaw, Map<String, Integer> previousAuto) {
        String authKey = MovaSettings.SIID_AUTO_SWITCH + "." + MovaSettings.PIID_AUTO_SWITCH;
        for (Map.Entry<String, JsonElement> e : settingsRaw.entrySet()) {
            if (e.getKey().equals(authKey)) {
                continue;
            }
            JsonElement old = previousRaw.get(e.getKey());
            if (old == null || !old.equals(e.getValue())) {
                logger.debug("Mova setting changed: {} {} -> {}", e.getKey(), old, e.getValue());
            }
        }
        for (Map.Entry<String, Integer> e : autoSwitch.entrySet()) {
            Integer old = previousAuto.get(e.getKey());
            if (old != null && !old.equals(e.getValue())) {
                logger.debug("Mova auto setting changed: {} {} -> {}", e.getKey(), old, e.getValue());
            }
        }
    }

    /** Die Auto-Switch-Einstellungen kommen als JSON-Text, z. B. [{"k":"LessColl","v":1}, ...]. */
    private void parseAutoSwitch(@Nullable JsonElement raw) {
        if (raw == null || !raw.isJsonPrimitive()) {
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(raw.getAsString());
            List<JsonObject> entries = new ArrayList<>();
            if (parsed.isJsonArray()) {
                JsonArray array = parsed.getAsJsonArray();
                for (JsonElement e : array) {
                    if (e.isJsonObject()) {
                        entries.add(e.getAsJsonObject());
                    }
                }
            } else if (parsed.isJsonObject()) {
                entries.add(parsed.getAsJsonObject());
            }
            for (JsonObject o : entries) {
                JsonElement k = o.get("k");
                JsonElement v = o.get("v");
                if (k != null && v != null && k.isJsonPrimitive() && v.isJsonPrimitive()) {
                    autoSwitch.put(k.getAsString(), v.getAsInt());
                }
            }
        } catch (RuntimeException e) {
            logger.debug("Auto switch settings not readable: {} ({})", raw, e.toString());
        }
    }

    private void publishSettings() {
        Map<String, JsonElement> v = settingsRaw;
        push(MovaStation.CHANNEL_WATER_LEVEL, intValue(v, MovaStation.SIID_WETNESS, MovaStation.PIID_WETNESS),
                x -> new DecimalType(x));
        push(MovaStation.CHANNEL_CHARGE_LIMIT, intValue(v, MovaStation.SIID_CHARGE_LIMIT, MovaStation.PIID_CHARGE_LIMIT),
                x -> new DecimalType(x));
        publishOffPeak();
        for (MovaStation.Sensor sensor : MovaStation.SENSORS) {
            Integer sv = intValue(v, sensor.siid, sensor.piid);
            switch (sensor.kind) {
                case ENUM:
                    push(sensor.channel, sv, x -> new StringType(sensor.nameOf(x)));
                    break;
                case PERCENT:
                    push(sensor.channel, sv, x -> new QuantityType<>(x, Units.PERCENT));
                    break;
                default:
                    push(sensor.channel, sv, x -> x != 0 ? OnOffType.ON : OnOffType.OFF);
                    break;
            }
        }
        for (MovaSettings.Def d : MovaSettings.DEFS) {
            Integer value;
            switch (d.kind) {
                case AUTO:
                    value = autoSwitch.get(d.key);
                    break;
                case AI_BIT:
                    value = intValue(v, d.siid, d.piid);
                    break;
                default:
                    value = intValue(v, d.siid, d.piid);
                    break;
            }
            if (value == null) {
                continue;
            }
            if (d.channel.equals(MovaSettings.CHANNEL_CARPET_CLEANING)) {
                Integer recognition = intValue(v, 4, 33);
                if (recognition != null && recognition == 0) {
                    push(d.channel, 0, x -> new StringType("ignore"));
                } else {
                    push(d.channel, value, x -> new StringType(d.nameOf(x == 6 ? 2 : x)));
                }
            } else if (d.names != null) {
                push(d.channel, value, x -> new StringType(d.nameOf(d.kind == MovaSettings.Kind.AUTO ? Math.abs(x) : x)));
            } else if (d.kind == MovaSettings.Kind.AI_BIT) {
                push(d.channel, value, x -> (x & d.bit) != 0 ? OnOffType.ON : OnOffType.OFF);
            } else if (d.kind == MovaSettings.Kind.AUTO) {
                push(d.channel, value, x -> x > 0 ? OnOffType.ON : OnOffType.OFF);
            } else {
                push(d.channel, value, x -> x != 0 ? OnOffType.ON : OnOffType.OFF);
            }
        }
    }

    private boolean handleSetting(MovaCloudApi api, MovaSettings.Def d, Command command) throws MovaCloudException {
        if (d.channel.equals(MovaSettings.CHANNEL_CARPET_CLEANING)) {
            return handleCarpetCleaning(api, command.toString().trim());
        }
        if (d.names != null) {
            Integer code = d.codeOf(command.toString());
            if (code == null) {
                logger.warn("Unknown value '{}' for channel {}", command, d.channel);
                return false;
            }
            if (d.kind == MovaSettings.Kind.AUTO) {
                if (!autoSwitch.containsKey(d.key)) {
                    logger.warn("Setting {} was not reported by the device and is not written", d.key);
                    return false;
                }
                String json = "{\"k\":\"" + d.key + "\",\"v\":" + code + "}";
                return api.setProperty(did, MovaSettings.SIID_AUTO_SWITCH, MovaSettings.PIID_AUTO_SWITCH, json);
            }
            return api.setProperty(did, d.siid, d.piid, code);
        }
        if (!(command instanceof OnOffType)) {
            logger.warn("Channel {} expects ON or OFF, got: {}", d.channel, command);
            return false;
        }
        int on = command == OnOffType.ON ? 1 : 0;
        if (on == 1 && (d.channel.equals(MovaSettings.CHANNEL_STAIN_CLEANING)
                || d.channel.equals(MovaSettings.CHANNEL_DYNAMIC_OBSTACLE))) {
            Integer avoidance = intValue(settingsRaw, 4, 21);
            if (avoidance != null && avoidance == 0) {
                logger.warn("Channel {} can only be switched on while smart obstacle avoidance "
                        + "(channel {}) is active", d.channel, MovaSettings.CHANNEL_OBSTACLE_AVOIDANCE);
                return false;
            }
        }
        switch (d.kind) {
            case AUTO:
                if (!autoSwitch.containsKey(d.key)) {
                    logger.warn("Setting {} was not reported by the device and is not written", d.key);
                    return false;
                }
                String json = "{\"k\":\"" + d.key + "\",\"v\":" + on + "}";
                return api.setProperty(did, MovaSettings.SIID_AUTO_SWITCH, MovaSettings.PIID_AUTO_SWITCH, json);
            case AI_BIT:
                Integer current = intValue(settingsRaw, MovaSettings.SIID_AI, MovaSettings.PIID_AI);
                if (current == null) {
                    logger.warn("AI settings (4/22) were not reported as a number, channel {} not writable",
                            d.channel);
                    return false;
                }
                // Schreibzugriffe auf das gemeinsame Bitfeld nacheinander ausführen und den Wert sofort lokal
                // fortschreiben, sonst überschreiben sich schnell aufeinanderfolgende Befehle gegenseitig.
                synchronized (aiBitsLock) {
                    Integer latest = intValue(settingsRaw, MovaSettings.SIID_AI, MovaSettings.PIID_AI);
                    int base = latest != null ? latest : current;
                    int updated = on == 1 ? (base | d.bit) : (base & ~d.bit);
                    boolean written = api.setProperty(did, MovaSettings.SIID_AI, MovaSettings.PIID_AI, updated);
                    if (written) {
                        settingsRaw.put(MovaSettings.SIID_AI + "." + MovaSettings.PIID_AI,
                                new com.google.gson.JsonPrimitive(updated));
                    }
                    return written;
                }
            default:
                if (!settingsRaw.containsKey(d.siid + "." + d.piid)) {
                    logger.warn("Setting {}.{} was not reported by the device and is not written", d.siid,
                            d.piid);
                    return false;
                }
                return api.setProperty(did, d.siid, d.piid, on);
        }
    }

    /**
     * Teppichreinigung wie in der Referenz: "ignore" schaltet die Teppicherkennung aus, alle anderen Werte
     * schalten sie ein und setzen die Reinigungsart (Mopp-Pad abnehmen braucht zusätzlich die automatische
     * Demontage).
     */
    private boolean handleCarpetCleaning(MovaCloudApi api, String text) throws MovaCloudException {
        Integer recognition = intValue(settingsRaw, 4, 33);
        Integer boost = intValue(settingsRaw, 4, 12);
        if ("ignore".equalsIgnoreCase(text)) {
            if (recognition == null) {
                logger.warn("Carpet detection (4/33) was not reported by the device, 'ignore' not possible");
                return false;
            }
            return api.setProperty(did, 4, 33, boost != null && boost == 1 ? 3 : 0);
        }
        MovaSettings.Def def = MovaSettings.find(MovaSettings.CHANNEL_CARPET_CLEANING);
        Integer code = def == null ? null : def.codeOf(text);
        if (code == null) {
            logger.warn("Unknown carpet cleaning '{}' (allowed: avoidance, adaptation, remove_mop, "
                    + "adaptation_without_route, vacuum_and_mop, cross, ignore)", text);
            return false;
        }
        boolean ok = true;
        if (recognition != null && recognition != 1) {
            ok &= api.setProperty(did, 4, 33, 1);
            if (recognition == 3) {
                ok &= api.setProperty(did, 4, 12, 1);
            }
        }
        Integer autoMount = intValue(settingsRaw, 4, 45);
        if (code == 3 && autoMount != null && autoMount != 1) {
            ok &= api.setProperty(did, 4, 45, 1);
        }
        ok &= api.setProperty(did, 4, 36, code);
        return ok;
    }


    /** Lädt die Karte aus der Cloud und veröffentlicht die Raumliste. Läuft im Hintergrund. */
    private void loadMap(MovaCloudApi api) {
        try {
            String objectName = api.getObjectName(did);
            if (objectName == null) {
                mapFailed("No map object found in the cloud (property 6.3 empty)");
                return;
            }
            String name = objectName;
            String key = null;
            int comma = objectName.indexOf(',');
            if (comma >= 0) {
                name = objectName.substring(0, comma);
                key = objectName.substring(comma + 1);
            }
            logger.debug("Mova map: object {} (key {})", name, key != null ? "vorhanden" : "keiner");
            String url = api.getDownloadUrl(did, name);
            if (url == null) {
                mapFailed("No download URL received for the map");
                return;
            }
            String text = api.downloadText(url);
            if (text == null || text.isBlank()) {
                mapFailed("Map download returned no data");
                return;
            }
            String iv = mapKeyOverride.isEmpty() ? null : mapKeyOverride;
            if (iv == null) {
                String model = api.getModel(did);
                iv = model != null ? MovaMap.ivForModel(model) : null;
            }
            MovaMap.Result result = MovaMap.decode(text, key, iv);
            logger.debug("Mova map decoded: map {} image {}x{}, JSON fields {}", result.mapId, result.width,
                    result.height, result.json.keySet());
            publishRooms(result);
            loadShortcuts(api);
        } catch (MovaCloudException e) {
            mapFailed(e.getMessage());
        } catch (RuntimeException e) {
            logger.warn("Unexpected error while loading the map: {}", e.toString(), e);
            nextMapLoad = System.currentTimeMillis() + MAP_RETRY_MS;
        } finally {
            mapLoading.set(false);
        }
    }


    /** Reinigungsverlauf aus den Status-Ereignissen (4.1) der Cloud, wie in der Referenz (Tasshack). */
    private void loadHistory(MovaCloudApi api) {
        try {
            long from = System.currentTimeMillis() / 1000 - 90L * 86400L;
            List<JsonObject> events = api.getEventHistory(did, MovaBindingConstants.SIID_VACUUM,
                    MovaBindingConstants.PIID_STATUS, from, 50);
            if (events == null) {
                nextHistoryLoad = System.currentTimeMillis() + 10 * 60_000L;
                return;
            }
            if (!loggedHistoryOnce && !events.isEmpty()) {
                logger.debug("Mova history raw value (once, for diagnostics, {} entries): {}", events.size(),
                        events.get(0));
                loggedHistoryOnce = true;
            }
            List<String[]> rows = new ArrayList<>();
            java.util.Set<Long> seen = new java.util.HashSet<>();
            for (JsonObject ev : events) {
                String[] row = parseHistoryEntry(ev, seen);
                if (row != null) {
                    rows.add(row);
                }
            }
            rows.sort((a, b) -> Long.compare(Long.parseLong(b[0]), Long.parseLong(a[0])));
            StringBuilder all = new StringBuilder();
            int n = 0;
            for (String[] row : rows) {
                if (n >= HISTORY_ENTRIES) {
                    break;
                }
                if (n > 0) {
                    all.append('\n');
                }
                all.append(row[1]);
                n++;
            }
            lastCleaningText = n == 0 ? MovaI18n.t("keine Einträge", "no entries") : rows.get(0)[1];
            historyText = n == 0 ? MovaI18n.t("keine Einträge", "no entries") : all.toString();
            publishHistory();
            logger.debug("Mova cleaning history: {} entries read, {} shown", rows.size(), n);
        } catch (MovaCloudException e) {
            logger.debug("Could not load the cleaning history: {}", e.getMessage());
            nextHistoryLoad = System.currentTimeMillis() + 10 * 60_000L;
        } catch (RuntimeException e) {
            logger.warn("Unexpected error while loading the cleaning history: {}", e.toString(), e);
            nextHistoryLoad = System.currentTimeMillis() + 10 * 60_000L;
        } finally {
            historyLoading.set(false);
        }
    }

    private void publishHistory() {
        String last = lastCleaningText;
        String all = historyText;
        if (last != null) {
            updateState(MovaStation.CHANNEL_LAST_CLEANING, new StringType(last));
        }
        if (all != null) {
            updateState(MovaStation.CHANNEL_CLEANING_HISTORY, new StringType(all));
        }
    }

    /** Wandelt einen Verlaufseintrag in {Startzeit, Textzeile}; null wenn unbrauchbar oder doppelt. */
    private String @Nullable [] parseHistoryEntry(JsonObject ev, java.util.Set<Long> seen) {
        JsonElement raw = ev.has("history") ? ev.get("history") : ev.get("value");
        if (raw == null || raw.isJsonNull()) {
            return null;
        }
        JsonArray items;
        try {
            JsonElement parsed = raw.isJsonPrimitive() ? JsonParser.parseString(raw.getAsString()) : raw;
            if (!parsed.isJsonArray()) {
                return null;
            }
            items = parsed.getAsJsonArray();
        } catch (RuntimeException e) {
            return null;
        }
        long start = 0;
        int minutes = 0;
        int area = 0;
        int suction = -1;
        int logStatus = -1;
        String props = null;
        for (JsonElement it : items) {
            if (!it.isJsonObject()) {
                continue;
            }
            JsonObject o = it.getAsJsonObject();
            JsonElement piidEl = o.get("piid");
            JsonElement valEl = o.has("value") ? o.get("value") : o.get("val");
            if (piidEl == null || valEl == null || valEl.isJsonNull()) {
                continue;
            }
            try {
                switch (piidEl.getAsInt()) {
                    case 2 -> minutes = valEl.getAsInt();
                    case 3 -> area = valEl.getAsInt();
                    case 4 -> suction = valEl.getAsInt();
                    case 8 -> start = valEl.getAsLong();
                    case 10 -> props = valEl.getAsString();
                    case 13 -> logStatus = valEl.getAsInt();
                    default -> {
                    }
                }
            } catch (RuntimeException e) {
                // einzelner unbrauchbarer Wert: ignorieren
            }
        }
        if (start <= 0 || !seen.add(start)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(java.time.format.DateTimeFormatter.ofPattern("dd.MM. HH:mm").format(java.time.LocalDateTime
                .ofInstant(java.time.Instant.ofEpochSecond(start), java.time.ZoneId.systemDefault())));
        sb.append(" | ").append(minutes).append(" min | ").append(area).append(" m² | ");
        sb.append(switch (logStatus) {
            case 1 -> MovaI18n.t("abgeschlossen", "completed");
            case 0 -> MovaI18n.t("unterbrochen", "interrupted");
            case 2 -> MovaI18n.t("manuell beendet", "stopped manually");
            case 3 -> MovaI18n.t("fehlgeschlagen", "failed");
            default -> MovaI18n.t("Status unbekannt", "unknown status");
        });
        if (suction >= 0) {
            String sName = suctionName(suction);
            if (sName != null) {
                sb.append(MovaI18n.t(" | Saugstufe ", " | suction ")).append(sName);
            }
        }
        if (props != null) {
            try {
                JsonObject p = JsonParser.parseString(props).getAsJsonObject();
                if (p.has("abnormal_end")) {
                    JsonArray ab = JsonParser.parseString(p.get("abnormal_end").getAsString()).getAsJsonArray();
                    if (ab.size() > 0 && ab.get(0).getAsInt() != 0) {
                        sb.append(MovaI18n.t(" | Abbruchgrund ", " | interrupted: ")).append(MovaErrors.interruptReason(ab.get(0).getAsInt()));
                    }
                }
            } catch (RuntimeException e) {
                // Zusatzfelder sind optional
            }
        }
        return new String[] { Long.toString(start), sb.toString() };
    }

    private static @Nullable String suctionName(int code) {
        return switch (code) {
            case 0 -> "leise";
            case 1 -> "standard";
            case 2 -> "stark";
            case 3 -> "turbo";
            default -> null;
        };
    }

    private void mapFailed(@Nullable String reason) {
        logger.warn("Could not load the map: {} (next attempt in 5 minutes)", reason);
        nextMapLoad = System.currentTimeMillis() + MAP_RETRY_MS;
    }

    private void publishRooms(MovaMap.Result result) {
        roomIdsByName.clear();
        roomsById.clear();
        StringBuilder text = new StringBuilder();
        for (MovaMap.Room room : result.rooms) {
            roomIdsByName.put(room.name.toLowerCase(Locale.ROOT), room.id);
            roomsById.put(room.id, room);
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(room.id).append(": ").append(room.name);
        }
        if (result.rooms.isEmpty()) {
            logger.warn("The map contains no rooms (neither seg_inf nor saved map rism). JSON fields: {}",
                    result.json.keySet());
            return;
        }
        if (!result.unmapped.isEmpty()) {
            StringBuilder hidden = new StringBuilder();
            for (MovaMap.Room room : result.unmapped) {
                hidden.append(hidden.length() > 0 ? ", " : "").append(room.id).append(": ").append(room.name);
            }
            logger.debug("Mova rooms without area in the map image (hidden): {}", hidden);
        }
        rawCleanset = parseCleanset(result.json);
        logger.debug("Mova map diagnostics: {}", result.diagnostics);
        logger.debug("Mova rooms: {}", text);
        updateState(MovaBindingConstants.CHANNEL_ROOMS, new StringType(text.toString()));
        updateState(MovaStation.CHANNEL_ROOM_INFO, new StringType(roomInfoText(result.rooms)));
        lastMap = result;
        updateState(MovaStation.CHANNEL_ROOM_ZONES, new StringType(roomZonesText(result)));
        updateState(MovaStation.CHANNEL_ROBOT_POSITION, new StringType(robotPositionText(result)));
        int[] rp = result.robot;
        String cr = rp != null && rp.length >= 2 ? roomAt(result, rp[0], rp[1]) : null;
        updateState(MovaBindingConstants.CHANNEL_CURRENT_ROOM, new StringType(cr != null ? cr : MovaI18n.t("unbekannt", "unknown")));
        updateState(MovaBindingConstants.CHANNEL_OBSTACLES, new StringType(obstaclesText(result)));
        updateState(MovaBindingConstants.CHANNEL_ROOM_PROGRESS, new StringType(roomProgressText(result)));
        publishMapImage(result);
    }

    private void publishMapImage(MovaMap.Result result) {
        int rotation = mapRotation >= 0 ? mapRotation : result.rotation;
        List<int[]> obstacles = new ArrayList<>();
        for (JsonArray o : obstacleList(result)) {
            try {
                if (obstacleStatus(o) == 0) {
                    obstacles.add(new int[] { (int) Math.round(o.get(0).getAsDouble()),
                            (int) Math.round(o.get(1).getAsDouble()) });
                }
            } catch (RuntimeException e) {
                // unreadable entry
            }
        }
        for (boolean dark : new boolean[] { false, true }) {
            String channel = dark ? MovaStation.CHANNEL_MAP_IMAGE_DARK : MovaStation.CHANNEL_MAP_IMAGE;
            if (dark && !isLinked(channel)) {
                continue;
            }
            try {
                byte[] png = MovaMapRenderer.render(result, rotation, mapFlip, showHiddenRooms, mapSize, mapLabels,
                        dark, obstacles);
                updateState(channel, new RawType(png, "image/png"));
                logger.debug("Mova map image rendered ({}): {} bytes (rotation {}, flip {})", dark ? "dark" : "light",
                        png.length, rotation, mapFlip);
            } catch (IOException | RuntimeException | LinkageError e) {
                logger.warn("Could not render the map image: {}", e.toString());
            }
        }
    }

    private static final String[] MODE_TEXT = { "Saugen", "Wischen", "Saugen und Wischen", "Erst Saugen, dann Wischen" };
    private static final String[] MODE_TEXT_EN = { "Vacuum", "Mop", "Vacuum and mop", "Mop after vacuum" };
    private static final String[] WATER_TEXT = { "", "niedrig", "mittel", "hoch" };
    private static final String[] WATER_TEXT_EN = { "", "low", "medium", "high" };

    /** Eine Zeile je Raum, nach Reinigungsreihenfolge sortiert (Räume ohne Reihenfolge am Ende). */
    private String roomInfoText(List<MovaMap.Room> rooms) {
        List<MovaMap.Room> sorted = new ArrayList<>(rooms);
        sorted.sort((a, b) -> {
            int oa = a.order != null ? a.order : Integer.MAX_VALUE;
            int ob = b.order != null ? b.order : Integer.MAX_VALUE;
            return oa != ob ? Integer.compare(oa, ob) : Integer.compare(a.id, b.id);
        });
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM. HH:mm");
        StringBuilder sb = new StringBuilder();
        for (MovaMap.Room r : sorted) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(r.id).append(": ").append(r.name);
            Integer mode = r.mode;
            if (mode != null && mode >= 0 && mode < MODE_TEXT.length) {
                sb.append(" | ").append(MovaI18n.t(MODE_TEXT[mode], MODE_TEXT_EN[mode]));
            }
            Integer suction = r.suction;
            if (suction != null) {
                sb.append(MovaI18n.t(" | Saugstufe ", " | suction ")).append(suctionName(suction));
            }
            Integer wetness = r.wetnessLevel;
            Integer water = r.waterVolume;
            if (wetness != null) {
                sb.append(MovaI18n.t(" | Wasser Stufe ", " | water level ")).append(wetness);
            } else if (water != null && water >= 1 && water <= 3) {
                sb.append(MovaI18n.t(" | Wasser ", " | water ")).append(MovaI18n.t(WATER_TEXT[water], WATER_TEXT_EN[water]));
            }
            if (r.times != null) {
                sb.append(" | ").append(r.times).append("x");
            }
            if (r.order != null) {
                sb.append(MovaI18n.t(" | Reihenfolge ", " | order ")).append(r.order);
            }
            Long last = r.lastCleaned;
            if (last != null && last > 0) {
                sb.append(MovaI18n.t(" | zuletzt ", " | last ")).append(fmt.format(java.time.LocalDateTime
                        .ofInstant(java.time.Instant.ofEpochSecond(last), java.time.ZoneId.systemDefault())));
            }
        }
        return sb.toString();
    }

    private void triggerMapLoad(MovaCloudApi api) {
        if (mapLoading.compareAndSet(false, true)) {
            scheduler.execute(() -> loadMap(api));
        }
    }

    private boolean handleSimpleCommand(MovaCloudApi api, String value) throws MovaCloudException {
        return switch (value) {
            case "start" -> {
                String resume = resumeBaseParam(api, true);
                yield resume != null ? sendBaseParam(api, resume)
                        : api.sendAction(did, MovaBindingConstants.SIID_DEVICE, MovaBindingConstants.AIID_START);
            }
            case "pause" -> api.sendAction(did, MovaBindingConstants.SIID_DEVICE, MovaBindingConstants.AIID_PAUSE);
            case "stop" -> api.sendAction(did, MovaBindingConstants.SIID_VACUUM, MovaBindingConstants.AIID_STOP);
            case "dock" -> {
                String resume = resumeBaseParam(api, false);
                yield resume != null ? sendBaseParam(api, resume)
                        : api.sendAction(did, MovaBindingConstants.SIID_BATTERY, MovaBindingConstants.AIID_CHARGE);
            }
            case "locate" -> api.sendAction(did, MovaBindingConstants.SIID_LOCATE, MovaBindingConstants.AIID_LOCATE);
            default -> {
                logger.warn("Unknown command: {}", value);
                yield false;
            }
        };
    }

    private boolean handleSuction(MovaCloudApi api, String value) throws MovaCloudException {
        int code = suctionToCode(value.trim());
        if (code < 0) {
            logger.warn("Unknown suction level '{}' (allowed: quiet, standard, strong, turbo)", value);
            return false;
        }
        return api.setProperty(did, MovaBindingConstants.SIID_VACUUM, MovaBindingConstants.PIID_SUCTION_LEVEL, code);
    }

    private boolean handleCleaningMode(MovaCloudApi api, String value) throws MovaCloudException {
        int code = MovaStation.modeCode(value);
        Integer raw = lastCleaningModeRaw;
        if (code < 0) {
            logger.warn("Unknown cleaning mode '{}' (allowed: sweeping, mopping, sweeping_and_mopping, "
                    + "mopping_after_sweeping)", value);
            return false;
        }
        if (raw == null) {
            logger.warn("Cleaning mode (4/23) has not been reported by the device yet, nothing is written");
            return false;
        }
        int updated = (raw & ~3) | code;
        logger.debug("Cleaning mode {}: raw {} -> {}", value, raw, updated);
        return api.setProperty(did, MovaBindingConstants.SIID_VACUUM, MovaBindingConstants.PIID_CLEANING_MODE,
                updated);
    }

    private boolean handleWaterLevel(MovaCloudApi api, Command command) throws MovaCloudException {
        int level;
        if (command instanceof DecimalType d) {
            level = d.intValue();
        } else if (command instanceof QuantityType<?> q) {
            level = q.intValue();
        } else {
            logger.warn("Water level expects a number from {} to {}, got: {}", MovaStation.MIN_WETNESS,
                    MovaStation.MAX_WETNESS, command);
            return false;
        }
        if (level < MovaStation.MIN_WETNESS || level > MovaStation.MAX_WETNESS) {
            logger.warn("Water level must be between {} and {}: {}", MovaStation.MIN_WETNESS,
                    MovaStation.MAX_WETNESS, level);
            return false;
        }
        return api.setProperty(did, MovaStation.SIID_WETNESS, MovaStation.PIID_WETNESS, level);
    }

    private boolean handleChargeLimit(MovaCloudApi api, Command command) throws MovaCloudException {
        int limit;
        if (command instanceof DecimalType d) {
            limit = d.intValue();
        } else if (command instanceof QuantityType<?> q) {
            limit = q.intValue();
        } else {
            logger.warn("Charge limit expects a number from {} to {}, got: {}", MovaStation.MIN_CHARGE_LIMIT,
                    MovaStation.MAX_CHARGE_LIMIT, command);
            return false;
        }
        if (limit < MovaStation.MIN_CHARGE_LIMIT || limit > MovaStation.MAX_CHARGE_LIMIT) {
            logger.warn("Charge limit must be between {} and {}: {}", MovaStation.MIN_CHARGE_LIMIT,
                    MovaStation.MAX_CHARGE_LIMIT, limit);
            return false;
        }
        return api.setProperty(did, MovaStation.SIID_CHARGE_LIMIT, MovaStation.PIID_CHARGE_LIMIT, limit);
    }

    /** Liest die aktuelle Niedertarif-Konfiguration aus 3/3 (JSON-Text) oder null. */
    private @Nullable JsonObject offPeakConfig() {
        JsonElement raw = settingsRaw.get(MovaStation.SIID_OFF_PEAK + "." + MovaStation.PIID_OFF_PEAK);
        if (raw == null || !raw.isJsonPrimitive()) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(raw.getAsString());
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            logger.debug("Off-peak configuration not readable: {} ({})", raw, e.toString());
            return null;
        }
    }

    private void publishOffPeak() {
        JsonObject cfg = offPeakConfig();
        if (cfg == null) {
            return;
        }
        try {
            if (cfg.has("enable")) {
                updateState(MovaStation.CHANNEL_OFF_PEAK, (cfg.get("enable").getAsBoolean() ? OnOffType.ON : OnOffType.OFF));
            }
            if (cfg.has("startTime")) {
                updateState(MovaStation.CHANNEL_OFF_PEAK_START, new StringType(cfg.get("startTime").getAsString()));
            }
            if (cfg.has("endTime")) {
                updateState(MovaStation.CHANNEL_OFF_PEAK_END, new StringType(cfg.get("endTime").getAsString()));
            }
        } catch (RuntimeException e) {
            logger.debug("Off-peak configuration does not match the schema: {} ({})", cfg, e.toString());
        }
    }

    private boolean handleOffPeak(MovaCloudApi api, String channel, Command command) throws MovaCloudException {
        JsonObject cfg = offPeakConfig();
        boolean enable = cfg != null && cfg.has("enable") && cfg.get("enable").getAsBoolean();
        String start = cfg != null && cfg.has("startTime") ? cfg.get("startTime").getAsString() : "22:00";
        String end = cfg != null && cfg.has("endTime") ? cfg.get("endTime").getAsString() : "08:00";
        switch (channel) {
            case MovaStation.CHANNEL_OFF_PEAK:
                if (!(command instanceof OnOffType)) {
                    logger.warn("Off-peak charging expects ON or OFF, got: {}", command);
                    return false;
                }
                enable = command == OnOffType.ON;
                break;
            case MovaStation.CHANNEL_OFF_PEAK_START:
            case MovaStation.CHANNEL_OFF_PEAK_END:
                String time = command.toString().trim();
                if (!time.matches("([01][0-9]|2[0-3]):[0-5][0-9]")) {
                    logger.warn("Invalid time '{}' (expected HH:mm)", time);
                    return false;
                }
                if (MovaStation.CHANNEL_OFF_PEAK_START.equals(channel)) {
                    start = time;
                } else {
                    end = time;
                }
                break;
            default:
                return false;
        }
        if (start.equals(end)) {
            logger.warn("Start and end of off-peak period must differ: {}", start);
            return false;
        }
        String json = "{\"enable\":" + enable + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}";
        return api.setProperty(did, MovaStation.SIID_OFF_PEAK, MovaStation.PIID_OFF_PEAK, json);
    }

    /**
     * Tasshack 2.0.3: Fortsetzen nach einer Pause, die nicht in der Reinigung selbst lag.
     * Liefert den Parameter für Aktion 4/4 oder null, wenn der normale Start/Andock-Befehl passt.
     * <ul>
     * <li>Rückfahrt zum Absaugen pausiert (15/5 = 3, Status 4/1 = 1): "8,1" (bei start und dock)</li>
     * <li>Moppwäsche pausiert (4/25 = 4): "1,1" (nur start)</li>
     * <li>Rückfahrt zum Waschen/Wasserablassen/Trocknen pausiert (4/25 = 3, 6, 7 und pausiert): "2,1" (nur start)</li>
     * </ul>
     */
    private @Nullable String resumeBaseParam(MovaCloudApi api, boolean start) {
        try {
            Map<String, JsonElement> v = fetch(api, new int[][] { { MovaBindingConstants.SIID_DEVICE, 1 },
                    { MovaBindingConstants.SIID_VACUUM, 1 }, { MovaBindingConstants.SIID_VACUUM, 25 }, { 15, 5 } });
            if (v == null) {
                return null;
            }
            Integer state = intValue(v, MovaBindingConstants.SIID_DEVICE, 1);
            Integer status = intValue(v, MovaBindingConstants.SIID_VACUUM, 1);
            Integer base = intValue(v, MovaBindingConstants.SIID_VACUUM, 25);
            Integer autoEmpty = intValue(v, 15, 5);
            boolean paused = (state != null && state == 3) || (status != null && status == 1);
            if (autoEmpty != null && autoEmpty == 3 && status != null && status == 1) {
                logger.debug("Resuming return for auto empty (8,1)");
                return "8,1";
            }
            if (!start || base == null) {
                return null;
            }
            if (base == 4) {
                logger.debug("Resuming mop washing (1,1)");
                return "1,1";
            }
            if ((base == 3 || base == 6 || base == 7) && paused) {
                logger.debug("Resuming return to the station (2,1)");
                return "2,1";
            }
        } catch (MovaCloudException e) {
            logger.debug("Could not read the state before start/dock: {}", e.getMessage());
        }
        return null;
    }

    private boolean sendBaseParam(MovaCloudApi api, String param) throws MovaCloudException {
        List<Object> in = new ArrayList<>();
        in.add(piidValue(MovaBindingConstants.PIID_CLEANING_PROPERTIES, param));
        return api.sendAction(did, MovaBindingConstants.SIID_VACUUM, 4, in);
    }

    private boolean handleBaseCommand(MovaCloudApi api, String value) throws MovaCloudException {
        String name = value.trim().toLowerCase(Locale.ROOT);
        String param = MovaStation.BASE_COMMANDS.get(name);
        if (param == null) {
            logger.warn("Unknown base station command '{}' (allowed: {})", value,
                    String.join(", ", MovaStation.BASE_COMMANDS.keySet()));
            return false;
        }
        if (param.isEmpty()) {
            return api.sendAction(did, 15, 1);
        }
        List<Object> in = new ArrayList<>();
        in.add(piidValue(MovaBindingConstants.PIID_CLEANING_PROPERTIES, param));
        return api.sendAction(did, MovaBindingConstants.SIID_VACUUM, 4, in);
    }

    private boolean handleReset(MovaCloudApi api, String value) throws MovaCloudException {
        MovaStation.Reset reset = MovaStation.RESETS.get(value.trim().toLowerCase(Locale.ROOT));
        if (reset == null) {
            logger.warn("Unknown consumable '{}' (allowed: {})", value,
                    String.join(", ", MovaStation.RESETS.keySet()));
            return false;
        }
        logger.info("Resetting consumable '{}'", value);
        boolean ok = api.sendAction(did, reset.siid, reset.aiid);
        pollCounter = 0; // Verbrauchsmaterial beim nächsten Durchlauf neu lesen
        return ok;
    }

    private boolean handleVolume(MovaCloudApi api, Command command) throws MovaCloudException {
        int percent;
        if (command instanceof QuantityType<?> q) {
            percent = q.intValue();
        } else if (command instanceof DecimalType d) {
            percent = d.intValue();
        } else {
            logger.warn("Volume expects a number from 0 to 100, got: {}", command);
            return false;
        }
        if (percent < 0 || percent > 100) {
            logger.warn("Volume must be between 0 and 100: {}", percent);
            return false;
        }
        return api.setProperty(did, MovaBindingConstants.SIID_VOLUME, MovaBindingConstants.PIID_VOLUME, percent);
    }

    /**
     * Raumreinigung: Eingabe z. B. "1,2,3" (Raum-Nummern). Es wird mit der aktuell eingestellten Saug- und
     * Wasserstufe je einmal gereinigt. Aufbau wie clean_segment in der Referenz (START_CUSTOM, Status 18).
     */
    /** Position des Roboters und der Station (mm) samt Raum und Zeitstempel der Karte. */
    private int @Nullable [] lastCharger;

    private String robotPositionText(MovaMap.Result r) {
        int[] pos = r.robot;
        if (pos == null || pos.length < 2) {
            return MovaI18n.t("unbekannt", "unknown");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(pos[0]).append(',').append(pos[1]);
        String room = roomAt(r, pos[0], pos[1]);
        if (room != null) {
            sb.append(" | ").append(room);
        }
        int[] ch = r.charger;
        // 32767 = Platzhalter des Roboters für "unbekannt" (während der Fahrt): letzte gültige Position verwenden
        if (ch != null && ch.length >= 2 && ch[0] != 32767 && ch[1] != 32767) {
            lastCharger = ch;
        } else {
            ch = lastCharger;
        }
        if (ch != null && ch.length >= 2) {
            sb.append(" | Station ").append(ch[0]).append(',').append(ch[1]);
        }
        JsonElement ts = r.json.get("timestamp_ms");
        if (ts != null && ts.isJsonPrimitive()) {
            try {
                long ms = ts.getAsLong();
                sb.append(MovaI18n.t(" | Stand ", " | as of ")).append(java.time.format.DateTimeFormatter.ofPattern("dd.MM. HH:mm:ss")
                        .format(java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())));
            } catch (RuntimeException e) {
                // Zeitstempel nicht lesbar: weglassen
            }
        }
        return sb.toString();
    }

    private @Nullable String roomAt(MovaMap.Result r, int xMm, int yMm) {
        if (r.grid <= 0 || r.imgWidth <= 0 || r.image.length < r.imgWidth * r.imgHeight) {
            return null;
        }
        int px = Math.floorDiv(xMm - r.left, r.grid);
        int py = Math.floorDiv(yMm - r.top, r.grid);
        if (px < 0 || py < 0 || px >= r.imgWidth || py >= r.imgHeight) {
            return MovaI18n.t("außerhalb der Karte", "outside the map");
        }
        int v = r.image[py * r.imgWidth + px] & 0xff;
        int id = v & 0x3f;
        if (v == 0 || id == 0) {
            return MovaI18n.t("kein Raum", "no room");
        }
        for (MovaMap.Room room : r.rooms) {
            if (room.id == id) {
                return MovaI18n.t("Raum ", "Room ") + id + ": " + room.name;
            }
        }
        return MovaI18n.t("Raum ", "Room ") + id;
    }

    /** Umschließendes Rechteck je Raum in Karten-Koordinaten (mm), berechnet aus dem Kartenbild. */
    private String roomZonesText(MovaMap.Result r) {
        int w = r.imgWidth;
        int h = r.imgHeight;
        if (w <= 0 || h <= 0 || r.grid <= 0 || r.image.length < w * h) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (MovaMap.Room room : r.rooms) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int px = r.image[y * w + x] & 0xff;
                    if (px == 0 || (px & 0x80) != 0 || (px & 0x3f) != room.id) {
                        continue;
                    }
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
            if (maxX < 0) {
                continue;
            }
            int x1 = r.left + minX * r.grid;
            int y1 = r.top + minY * r.grid;
            int x2 = r.left + (maxX + 1) * r.grid;
            int y2 = r.top + (maxY + 1) * r.grid;
            int[] c = roomCenterPixel(r, room.id, minX, minY, maxX, maxY);
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(room.id).append(": ").append(room.name).append(" | Zone ").append(x1).append(',').append(y1)
                    .append(',').append(x2).append(',').append(y2).append(MovaI18n.t(" | Mitte ", " | centre "))
                    .append(r.left + c[0] * r.grid + r.grid / 2).append(',')
                    .append(r.top + c[1] * r.grid + r.grid / 2);
        }
        return sb.toString();
    }

    static boolean isRoomPixel(MovaMap.Result r, int x, int y, int roomId) {
        if (x < 0 || y < 0 || x >= r.imgWidth || y >= r.imgHeight) {
            return false;
        }
        int px = r.image[y * r.imgWidth + x] & 0xff;
        return px != 0 && (px & 0x80) == 0 && (px & 0x3f) == roomId;
    }

    /**
     * Liefert einen Punkt, der sicher im Raum liegt: den Raum-Pixel, der dem Schwerpunkt am nächsten ist und rundherum
     * (ca. 40 cm) nur Boden desselben Raums hat. Bei L- oder U-förmigen Räumen oder Löchern (z. B. Möbel) liegt die
     * Mitte des umschließenden Rechtecks sonst außerhalb der befahrbaren Fläche.
     */
    static int[] roomCenterPixel(MovaMap.Result r, int roomId, int minX, int minY, int maxX, int maxY) {
        long sx = 0;
        long sy = 0;
        long n = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (isRoomPixel(r, x, y, roomId)) {
                    sx += x;
                    sy += y;
                    n++;
                }
            }
        }
        if (n == 0) {
            return new int[] { (minX + maxX) / 2, (minY + maxY) / 2 };
        }
        double cx = (double) sx / n;
        double cy = (double) sy / n;
        int margin = Math.max(1, 400 / Math.max(1, r.grid));
        for (int m = margin; m >= 0; m--) {
            int bestX = -1;
            int bestY = -1;
            double best = Double.MAX_VALUE;
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    if (!isRoomPixel(r, x, y, roomId)) {
                        continue;
                    }
                    double d = (x - cx) * (x - cx) + (y - cy) * (y - cy);
                    if (d >= best) {
                        continue;
                    }
                    boolean inner = true;
                    for (int dy = -m; dy <= m && inner; dy++) {
                        for (int dx = -m; dx <= m; dx++) {
                            if (!isRoomPixel(r, x + dx, y + dy, roomId)) {
                                inner = false;
                                break;
                            }
                        }
                    }
                    if (inner) {
                        best = d;
                        bestX = x;
                        bestY = y;
                    }
                }
            }
            if (bestX >= 0) {
                return new int[] { bestX, bestY };
            }
        }
        return new int[] { (int) Math.round(cx), (int) Math.round(cy) };
    }

    /** Liest die Shortcuts des Roboters (Property 4.48: JSON-Liste mit id, Base64-Name und Zustand). */
    private void loadShortcuts(MovaCloudApi api) {
        try {
            List<JsonObject> props = api.getProperties(did, new int[][] { { MovaBindingConstants.SIID_VACUUM,
                    MovaBindingConstants.PIID_SHORTCUTS } });
            if (props == null || props.isEmpty()) {
                return;
            }
            JsonElement value = props.get(0).get("value");
            if (value == null || !value.isJsonPrimitive() || value.getAsString().isBlank()) {
                shortcutIdsByName.clear();
                shortcutNamesById.clear();
                updateState(MovaBindingConstants.CHANNEL_SHORTCUTS, new StringType(""));
                return;
            }
            JsonArray list = JsonParser.parseString(value.getAsString()).getAsJsonArray();
            shortcutIdsByName.clear();
            shortcutNamesById.clear();
            StringBuilder sb = new StringBuilder();
            for (JsonElement e : list) {
                JsonObject o = e.getAsJsonObject();
                int id = o.get("id").getAsInt();
                String name = o.has("name")
                        ? new String(Base64.getDecoder().decode(o.get("name").getAsString()), StandardCharsets.UTF_8)
                        : String.valueOf(id);
                String state = o.has("state") ? o.get("state").getAsString() : "";
                boolean running = "0".equals(state) || "1".equals(state);
                shortcutIdsByName.put(name.toLowerCase(Locale.ROOT), id);
                shortcutNamesById.put(id, name);
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(id).append(": ").append(name);
                if (running) {
                    sb.append(MovaI18n.t(" (läuft)", " (running)"));
                }
            }
            updateState(MovaBindingConstants.CHANNEL_SHORTCUTS, new StringType(sb.toString()));
        } catch (MovaCloudException | RuntimeException e) {
            logger.debug("Could not read the shortcuts: {}", e.toString());
        }
    }

    /**
     * Shortcut-Befehl. Syntax: {@code start:<Nr oder Name>}, {@code rename:<Nr>=<neuer Name>} oder
     * {@code delete:<Nr>}. Ohne Präfix wird der Shortcut gestartet.
     */
    private boolean handleShortcut(MovaCloudApi api, String value) throws MovaCloudException {
        String text = value.trim();
        String cmd = "start";
        int colon = text.indexOf(':');
        if (colon > 0) {
            String prefix = text.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            if (prefix.equals("start") || prefix.equals("rename") || prefix.equals("delete")) {
                cmd = prefix;
                text = text.substring(colon + 1).trim();
            }
        }
        if (wasCleaning && !cmd.equals("start")) {
            logger.warn("Shortcuts are not changed during cleaning");
            return false;
        }
        String idText = text;
        String newName = "";
        if (cmd.equals("rename")) {
            int eq = text.indexOf('=');
            if (eq <= 0 || eq == text.length() - 1) {
                logger.warn("Rename: expected rename:<no>=<new name>");
                return false;
            }
            idText = text.substring(0, eq).trim();
            newName = text.substring(eq + 1).trim();
        }
        Integer id = shortcutId(idText);
        if (id == null) {
            logger.warn("Unknown shortcut '{}' (known: {})", idText, shortcutNamesById);
            return false;
        }
        if (id < 25 || id > 128) {
            logger.warn("Invalid shortcut number {}", id);
            return false;
        }
        List<Object> in = new ArrayList<>();
        switch (cmd) {
            case "start" -> {
                in.add(piidValue(MovaBindingConstants.PIID_STATUS, MovaBindingConstants.STATUS_SHORTCUT));
                in.add(piidValue(MovaBindingConstants.PIID_CLEANING_PROPERTIES, String.valueOf(id)));
                logger.debug("Start shortcut: {}", id);
                return api.sendAction(did, MovaBindingConstants.SIID_VACUUM, MovaBindingConstants.AIID_START_CUSTOM,
                        in);
            }
            case "rename" -> {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("id", id);
                params.put("name", Base64.getEncoder().encodeToString(newName.getBytes(StandardCharsets.UTF_8)));
                params.put("type", 3);
                return sendShortcutEdit(api, params, true);
            }
            default -> {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("id", id);
                params.put("type", 4);
                return sendShortcutEdit(api, params, true);
            }
        }
    }

    private boolean sendShortcutEdit(MovaCloudApi api, Map<String, Object> params, boolean reload)
            throws MovaCloudException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cmd", "EDIT_COMMAND");
        data.put("params", params);
        String json = MovaSigner.toJson(data, false);
        List<Object> in = new ArrayList<>();
        in.add(piidValue(MovaBindingConstants.PIID_CLEANING_PROPERTIES, json));
        logger.debug("Edit shortcut: {}", json);
        boolean ok = api.sendAction(did, MovaBindingConstants.SIID_VACUUM, MovaBindingConstants.AIID_SHORTCUTS, in);
        if (ok && reload) {
            scheduler.schedule(() -> loadShortcuts(api), 3, TimeUnit.SECONDS);
        }
        return ok;
    }

    private @Nullable Integer shortcutId(String text) {
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return shortcutIdsByName.get(text.trim().toLowerCase(Locale.ROOT));
        }
    }

    /** Zu einem Punkt fahren und dort Bilder aufnehmen. Syntax: {@code x,y} in Karteneinheiten (mm). */
    private boolean handleGoTo(MovaCloudApi api, String value) throws MovaCloudException {
        String[] xy = value.trim().split("[,\\s;]+");
        if (xy.length != 2) {
            logger.warn("Invalid target point '{}': expected x,y", value);
            return false;
        }
        int x;
        int y;
        try {
            x = (int) Math.round(Double.parseDouble(xy[0]));
            y = (int) Math.round(Double.parseDouble(xy[1]));
        } catch (NumberFormatException e) {
            logger.warn("Invalid coordinates in target point '{}'", value);
            return false;
        }
        if (!pointInMap(x, y)) {
            logger.warn("Target point {},{} is not on the map", x, y);
            return false;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tpoint", List.of(Arrays.asList(x, y, 0, 0)));
        String json = MovaSigner.toJson(data, false);
        List<Object> in = new ArrayList<>();
        in.add(piidValue(MovaBindingConstants.PIID_STATUS, MovaBindingConstants.STATUS_CRUISING_POINT));
        in.add(piidValue(MovaBindingConstants.PIID_CLEANING_PROPERTIES, json));
        logger.debug("Go to point: {}", json);
        return api.sendAction(did, MovaBindingConstants.SIID_VACUUM, MovaBindingConstants.AIID_START_CUSTOM, in);
    }

    /**
     * Fernsteuerung: ein Schritt. Syntax: forward, backward, left, right, stop oder {@code Drehung,Geschwindigkeit}
     * (Drehung -360..360, Geschwindigkeit -600..600).
     */
    private boolean handleRemoteControl(MovaCloudApi api, String value) throws MovaCloudException {
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
                    logger.warn("Invalid remote control '{}' (allowed: forward, backward, left, right, stop or rotation,velocity)",
                            value);
                    return false;
                }
                try {
                    rotation = Math.max(-360, Math.min(360, Integer.parseInt(parts[0])));
                    velocity = Math.max(-600, Math.min(600, Integer.parseInt(parts[1])));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid numbers in remote control '{}'", value);
                    return false;
                }
            }
        }
        String json = "{\"spdv\":" + velocity + ",\"spdw\":" + rotation + ",\"audio\":\"false\",\"random\":"
                + (int) (Math.random() * 65535) + "}";
        logger.debug("Remote control: {}", json);
        return api.setProperty(did, MovaBindingConstants.SIID_VACUUM, MovaBindingConstants.PIID_REMOTE_CONTROL, json);
    }

    /** Prüft, ob ein Punkt (mm) auf einer befahrbaren Fläche der Karte liegt; true, wenn keine Karte bekannt ist. */
    private boolean pointInMap(int xMm, int yMm) {
        MovaMap.Result r = lastMap;
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
     * Punktreinigung (ca. 1,5 m² um den Punkt). Syntax: {@code x,y[@times=1-3/suction=quiet..turbo/water=1-3]},
     * mehrere Punkte mit ';' getrennt. Koordinaten in Karteneinheiten (mm).
     */
    private boolean handleCleanSpot(MovaCloudApi api, String value) throws MovaCloudException {
        List<Object> points = new ArrayList<>();
        for (String part : value.split(";+")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            String options = "";
            int at = p.indexOf('@');
            if (at >= 0) {
                options = p.substring(at + 1).trim();
                p = p.substring(0, at).trim();
            }
            String[] xy = p.split("[,\\s]+");
            if (xy.length != 2) {
                logger.warn("Invalid point '{}': expected x,y", p);
                return false;
            }
            int x;
            int y;
            try {
                x = (int) Math.round(Double.parseDouble(xy[0]));
                y = (int) Math.round(Double.parseDouble(xy[1]));
            } catch (NumberFormatException e) {
                logger.warn("Invalid coordinates in point '{}'", p);
                return false;
            }
            if (!pointInMap(x, y)) {
                logger.warn("Point {},{} is not on the map", x, y);
                return false;
            }
            int times = 1;
            int suction = lastSuction;
            int water = lastWater;
            for (String opt : options.split("/")) {
                String o = opt.trim().toLowerCase(Locale.ROOT);
                if (o.isEmpty()) {
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
                        default -> {
                            logger.warn("Unknown point option '{}' (allowed: times, suction, water)", opt);
                            return false;
                        }
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Invalid value in point option '{}'", opt);
                    return false;
                }
            }
            points.add(Arrays.asList(x, y, times, suction, water));
        }
        if (points.isEmpty()) {
            logger.warn("No point given");
            return false;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("points", points);
        String json = MovaSigner.toJson(data, false);
        List<Object> in = new ArrayList<>();
        in.add(piidValue(MovaBindingConstants.PIID_STATUS, MovaBindingConstants.STATUS_SPOT_CLEANING));
        in.add(piidValue(MovaBindingConstants.PIID_CLEANING_PROPERTIES, json));
        logger.debug("Spot cleaning: {}", json);
        return api.sendAction(did, MovaBindingConstants.SIID_VACUUM, MovaBindingConstants.AIID_START_CUSTOM, in);
    }

    /**
     * Zonenreinigung. Syntax: {@code x1,y1,x2,y2[@times=1-3/suction=quiet..turbo/water=1-3]}, mehrere Zonen mit ';'
     * getrennt. Koordinaten in Karteneinheiten (wie in der Karte, Millimeter).
     */
    private boolean handleCleanZone(MovaCloudApi api, String value) throws MovaCloudException {
        List<Object> areas = new ArrayList<>();
        for (String part : value.split(";+")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            String options = "";
            int at = p.indexOf('@');
            if (at >= 0) {
                options = p.substring(at + 1).trim();
                p = p.substring(0, at).trim();
            }
            String[] xy = p.split("[,\\s]+");
            if (xy.length != 4) {
                logger.warn("Invalid zone '{}': expected x1,y1,x2,y2", p);
                return false;
            }
            int[] c = new int[4];
            try {
                for (int i = 0; i < 4; i++) {
                    c[i] = (int) Math.round(Double.parseDouble(xy[i]));
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid coordinates in zone '{}'", p);
                return false;
            }
            int times = 1;
            int suction = lastSuction;
            int water = lastWater;
            for (String opt : options.split("/")) {
                String o = opt.trim().toLowerCase(Locale.ROOT);
                if (o.isEmpty()) {
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
                        default -> {
                            logger.warn("Unknown zone option '{}' (allowed: times, suction, water)", opt);
                            return false;
                        }
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Invalid value in zone option '{}'", opt);
                    return false;
                }
            }
            areas.add(Arrays.asList(Math.min(c[0], c[2]), Math.min(c[1], c[3]), Math.max(c[0], c[2]),
                    Math.max(c[1], c[3]), times, suction, water));
        }
        if (areas.isEmpty()) {
            logger.warn("No zone given");
            return false;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("areas", areas);
        String json = MovaSigner.toJson(data, false);
        List<Object> in = new ArrayList<>();
        in.add(piidValue(MovaBindingConstants.PIID_STATUS, MovaBindingConstants.STATUS_ZONE_CLEANING));
        in.add(piidValue(MovaBindingConstants.PIID_CLEANING_PROPERTIES, json));
        logger.debug("Zone cleaning: {}", json);
        return api.sendAction(did, MovaBindingConstants.SIID_VACUUM, MovaBindingConstants.AIID_START_CUSTOM, in);
    }

    private boolean handleCleanRooms(MovaCloudApi api, String value) throws MovaCloudException {
        List<Object> selects = new ArrayList<>();
        for (String part : value.split("[,;]+")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            String options = "";
            int at = p.indexOf('@');
            if (at >= 0) {
                options = p.substring(at + 1).trim();
                p = p.substring(0, at).trim();
            }
            int id;
            try {
                id = Integer.parseInt(p);
            } catch (NumberFormatException e) {
                Integer byName = roomIdsByName.get(p.toLowerCase(Locale.ROOT));
                if (byName == null) {
                    logger.warn("Invalid room number or unknown room name '{}' in '{}'", p, value);
                    return false;
                }
                id = byName;
            }
            int times = 1;
            int suction = lastSuction;
            int water = lastWater;
            for (String opt : options.split("/")) {
                String o = opt.trim().toLowerCase(Locale.ROOT);
                if (o.isEmpty()) {
                    continue;
                }
                if (o.equals("room")) {
                    MovaMap.Room room = roomsById.get(id);
                    if (room != null) {
                        times = room.times != null ? Math.max(1, room.times) : times;
                        suction = room.suction != null ? room.suction : suction;
                        water = room.waterVolume != null ? room.waterVolume : water;
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
                        default -> {
                            logger.warn("Unknown room option '{}' (allowed: times, suction, water, room)", opt);
                            return false;
                        }
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Invalid value in room option '{}'", opt);
                    return false;
                }
            }
            selects.add(Arrays.asList(id, times, suction, water, 1));
        }
        if (selects.isEmpty()) {
            logger.warn("No room numbers given");
            return false;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("selects", selects);
        String json = MovaSigner.toJson(data, false);

        List<Object> in = new ArrayList<>();
        in.add(piidValue(MovaBindingConstants.PIID_STATUS, MovaBindingConstants.STATUS_SEGMENT_CLEANING));
        in.add(piidValue(MovaBindingConstants.PIID_CLEANING_PROPERTIES, json));
        logger.debug("Room cleaning: {}", json);
        return api.sendAction(did, MovaBindingConstants.SIID_VACUUM, MovaBindingConstants.AIID_START_CUSTOM, in);
    }


    /** Liest "cleanset" (Raum-Nummer -> [Saugstufe, Wasser, Wiederholungen, Reihenfolge, Modus, Mopp-Einstellung, ...]). */
    private static @Nullable JsonObject parseCleanset(JsonObject json) {
        try {
            JsonElement cs = json.get("cleanset");
            if (cs != null && cs.isJsonPrimitive()) {
                cs = JsonParser.parseString(cs.getAsString());
            }
            return cs != null && cs.isJsonObject() ? cs.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Raum-Einstellungen aendern (Aufbau wie set_cleanset / set_segment_* der Referenz): Eingabe z. B.
     * "Kueche@times=2/suction=strong/water=2/mode=sweeping_and_mopping/order=1, 9@times=1". Gelesene Werte aller
     * Raeume werden mit der Aenderung zurueckgeschrieben (Lesen, Aendern, Schreiben).
     */
    private boolean handleRoomSettings(MovaCloudApi api, String value) throws MovaCloudException {
        if (wasCleaning) {
            logger.warn("Room settings cannot be changed while the robot is cleaning");
            return false;
        }
        JsonObject cs = rawCleanset;
        if (cs == null || roomsById.isEmpty()) {
            logger.warn("Room settings: the map is not loaded yet (cleanset missing)");
            return false;
        }
        Map<Integer, int[]> sets = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : cs.entrySet()) {
            try {
                if (!e.getValue().isJsonArray()) {
                    continue;
                }
                JsonArray a = e.getValue().getAsJsonArray();
                int[] arr = new int[a.size()];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = a.get(i).getAsInt();
                }
                sets.put(Integer.parseInt(e.getKey()), arr);
            } catch (RuntimeException ex) {
                logger.warn("Room settings: unexpected cleanset format for room {}", e.getKey());
                return false;
            }
        }
        Map<Integer, Integer> wantedOrder = new java.util.LinkedHashMap<>();
        for (String part : value.split("[,;]+")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            String options = "";
            int at = p.indexOf('@');
            if (at >= 0) {
                options = p.substring(at + 1).trim();
                p = p.substring(0, at).trim();
            }
            Integer id;
            try {
                id = Integer.valueOf(p);
            } catch (NumberFormatException ex) {
                id = roomIdsByName.get(p.toLowerCase(Locale.ROOT));
            }
            int[] arr = id != null ? sets.get(id) : null;
            if (id == null || arr == null) {
                logger.warn("Room settings: room '{}' not found", p);
                return false;
            }
            for (String opt : options.split("/")) {
                String o = opt.trim().toLowerCase(Locale.ROOT);
                if (o.isEmpty()) {
                    continue;
                }
                int eq = o.indexOf('=');
                String key = eq >= 0 ? o.substring(0, eq).trim() : o;
                String val = eq >= 0 ? o.substring(eq + 1).trim() : "";
                try {
                    switch (key) {
                        case "times" -> {
                            int n = Integer.parseInt(val);
                            if (n < 1 || n > 3 || arr.length < 3) {
                                throw new NumberFormatException();
                            }
                            arr[2] = n;
                        }
                        case "suction" -> {
                            int code = suctionToCode(val);
                            code = code >= 0 ? code : Integer.parseInt(val);
                            if (code < 0 || code > 3 || arr.length < 1) {
                                throw new NumberFormatException();
                            }
                            arr[0] = code;
                        }
                        case "mode" -> {
                            int m = switch (val) {
                                case "sweeping" -> 0;
                                case "mopping" -> 1;
                                case "sweeping_and_mopping" -> 2;
                                default -> Integer.parseInt(val);
                            };
                            if (m < 0 || m > 2 || arr.length < 5) {
                                throw new NumberFormatException();
                            }
                            arr[4] = m;
                        }
                        case "water" -> {
                            int wv = Integer.parseInt(val);
                            if (wv < 1 || wv > 3 || arr.length < 2) {
                                throw new NumberFormatException();
                            }
                            arr[1] = wv + 1;
                            if (arr.length > 5 && arr[5] > 0) {
                                int v0 = arr[5] & 0x0f;
                                int v1 = (arr[5] >> 4) & 0x0f;
                                int v2 = (arr[5] >> 8) & 0x0f;
                                if (v2 == 0) {
                                    v1 = wv;
                                } else {
                                    v2 = wv;
                                }
                                arr[5] = (v2 << 8) | (v1 << 4) | v0;
                            }
                        }
                        case "order" -> {
                            int n = Integer.parseInt(val);
                            if (n < 1 || n > sets.size()) {
                                throw new NumberFormatException();
                            }
                            wantedOrder.put(id, n);
                        }
                        default -> {
                            logger.warn("Unknown room setting '{}' (allowed: times, suction, mode, water, order)",
                                    opt);
                            return false;
                        }
                    }
                } catch (NumberFormatException ex) {
                    logger.warn("Invalid value in room setting '{}' for room {}", opt, id);
                    return false;
                }
            }
        }
        boolean ok = true;
        if (!wantedOrder.isEmpty()) {
            List<Integer> seq = new ArrayList<>(sets.keySet());
            seq.sort((a, b) -> {
                MovaMap.Room ra = roomsById.get(a);
                MovaMap.Room rb = roomsById.get(b);
                Integer oa = ra != null ? ra.order : null;
                Integer ob = rb != null ? rb.order : null;
                return Integer.compare(oa != null ? oa : 1000 + a, ob != null ? ob : 1000 + b);
            });
            for (Map.Entry<Integer, Integer> w : wantedOrder.entrySet()) {
                seq.remove(w.getKey());
                seq.add(Math.min(w.getValue() - 1, seq.size()), w.getKey());
            }
            List<Object> orderList = new ArrayList<>();
            int pos = 1;
            for (Integer rid : seq) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put(String.valueOf(rid), pos++);
                orderList.add(m);
            }
            ok = sendMapData(api, "cleanareaorder", orderList);
        }
        if (ok && !sets.isEmpty()) {
            List<Object> list = new ArrayList<>();
            for (Map.Entry<Integer, int[]> e : sets.entrySet()) {
                int[] a = e.getValue();
                List<Object> item = new ArrayList<>();
                item.add(e.getKey());
                for (int i = 0; i < a.length; i++) {
                    if (i == 3) {
                        continue; // Reihenfolge steht nicht in der gesendeten Liste
                    }
                    item.add(a[i]);
                }
                list.add(item);
            }
            ok = sendMapData(api, "customeClean", list);
        }
        if (ok) {
            scheduler.schedule(() -> triggerMapLoad(api), 8, TimeUnit.SECONDS);
        }
        return ok;
    }

    /**
     * Räume bearbeiten. Syntax: {@code Raum@name=Neuer Name/visible=on|off/floor=none|wood|tile/mop=A|B|C}, mehrere
     * Räume mit ';' getrennt. Der Raum wird per Name oder Nummer angegeben.
     */
    private boolean handleRoomEdit(MovaCloudApi api, String value) throws MovaCloudException {
        if (wasCleaning) {
            logger.warn("Rooms cannot be edited while the robot is cleaning");
            return false;
        }
        MovaMap.Result map = lastMap;
        if (map == null || !(map.json.get("seg_inf") instanceof JsonObject segInf)) {
            logger.warn("Edit rooms: the map is not loaded yet (seg_inf missing)");
            return false;
        }
        Map<Integer, String> names = new LinkedHashMap<>(); // nur geänderte Namen ("" = Namen entfernen)
        Map<Integer, Integer> floors = new LinkedHashMap<>();
        Map<Integer, Boolean> visibility = new LinkedHashMap<>();
        Map<Integer, String> mops = new LinkedHashMap<>();
        for (String part : value.split(";+")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            int at = p.indexOf('@');
            if (at <= 0) {
                logger.warn("Edit rooms: expected room@option, got '{}'", p);
                return false;
            }
            String room = p.substring(0, at).trim();
            int id;
            try {
                id = Integer.parseInt(room);
            } catch (NumberFormatException e) {
                Integer byName = roomIdsByName.get(room.toLowerCase(Locale.ROOT));
                if (byName == null) {
                    logger.warn("Unknown room '{}'", room);
                    return false;
                }
                id = byName;
            }
            if (!segInf.has(String.valueOf(id))) {
                logger.warn("Room {} does not exist in the map", id);
                return false;
            }
            for (String opt : p.substring(at + 1).split("/")) {
                String o = opt.trim();
                int eq = o.indexOf('=');
                if (eq <= 0) {
                    logger.warn("Invalid option '{}' (expected key=value)", o);
                    return false;
                }
                String key = o.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String val = o.substring(eq + 1).trim();
                switch (key) {
                    case "name" -> names.put(id, val);
                    case "visible" -> {
                        String v = val.toLowerCase(Locale.ROOT);
                        if (!(v.equals("on") || v.equals("off") || v.equals("true") || v.equals("false")
                                || v.equals("1") || v.equals("0"))) {
                            logger.warn("Invalid value for visible: '{}' (on/off)", val);
                            return false;
                        }
                        visibility.put(id, v.equals("on") || v.equals("true") || v.equals("1"));
                    }
                    case "floor" -> {
                        Integer m = switch (val.toLowerCase(Locale.ROOT)) {
                            case "none", "0" -> 0;
                            case "wood", "1" -> 1;
                            case "tile", "2" -> 2;
                            default -> null;
                        };
                        if (m == null) {
                            logger.warn("Invalid floor '{}' (none, wood, tile)", val);
                            return false;
                        }
                        floors.put(id, m);
                    }
                    case "mop" -> {
                        String v = val.toUpperCase(Locale.ROOT);
                        if (!(v.equals("A") || v.equals("B") || v.equals("C"))) {
                            logger.warn("Invalid mop type '{}' (A, B, C)", val);
                            return false;
                        }
                        mops.put(id, v);
                    }
                    default -> {
                        logger.warn("Unknown option '{}' (allowed: name, visible, floor, mop)", key);
                        return false;
                    }
                }
            }
        }
        if (names.isEmpty() && floors.isEmpty() && visibility.isEmpty() && mops.isEmpty()) {
            logger.warn("Edit rooms: nothing to change");
            return false;
        }
        int mapId = 0;
        JsonElement cur = map.json.get("curid");
        if (cur != null && cur.isJsonPrimitive()) {
            try {
                mapId = Integer.parseInt(cur.getAsString().trim());
            } catch (NumberFormatException e) {
                mapId = map.mapId;
            }
        }
        boolean ok = true;
        // Wie in Tasshack v2.0.2: nur tatsächlich geänderte Räume senden (nicht die ganze Raumliste).
        if (!names.isEmpty()) {
            Map<String, Object> nsr = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> e : names.entrySet()) {
                String key = String.valueOf(e.getKey());
                String newName = e.getValue();
                if (newName.isEmpty()) {
                    logger.warn("Room {}: the name must not be empty (removing names is not supported)", key);
                    ok = false;
                    continue;
                }
                JsonObject o = segInf.get(key).getAsJsonObject();
                String oldName = null;
                if (o.has("name") && o.get("name").isJsonPrimitive()) {
                    try {
                        oldName = new String(Base64.getDecoder().decode(o.get("name").getAsString()),
                                StandardCharsets.UTF_8);
                    } catch (IllegalArgumentException ex) {
                        oldName = null;
                    }
                }
                if (newName.equals(oldName)) {
                    continue;
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", Base64.getEncoder().encodeToString(newName.getBytes(StandardCharsets.UTF_8)));
                entry.put("type", 0);
                entry.put("index", 0);
                nsr.put(key, entry);
            }
            if (!nsr.isEmpty()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("nsr", nsr);
                data.put("mapid", mapId);
                ok &= sendMapDataMap(api, data);
            }
        }
        // Boden: ein Schritt je geändertem Raum (nicht jedes Gerät nimmt mehrere Räume auf einmal an).
        for (Map.Entry<Integer, Integer> e : floors.entrySet()) {
            String key = String.valueOf(e.getKey());
            JsonObject o = segInf.get(key).getAsJsonObject();
            int oldMaterial = o.has("material") ? o.get("material").getAsInt() : 0;
            if (oldMaterial == e.getValue()) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("material", e.getValue());
            Map<String, Object> nsm = new LinkedHashMap<>();
            nsm.put(key, entry);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("nsm", nsm);
            data.put("mapid", mapId);
            ok &= sendMapDataMap(api, data);
        }
        if (!visibility.isEmpty()) {
            java.util.TreeSet<Integer> hidden = new java.util.TreeSet<>();
            JsonElement del = map.json.get("delsr");
            if (del != null && del.isJsonArray()) {
                for (JsonElement e : del.getAsJsonArray()) {
                    hidden.add(e.getAsInt());
                }
            }
            for (Map.Entry<Integer, Boolean> e : visibility.entrySet()) {
                if (e.getValue()) {
                    hidden.remove(e.getKey());
                } else {
                    hidden.add(e.getKey());
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("delsr", new ArrayList<>(hidden));
            data.put("mapid", mapId);
            ok &= sendMapDataMap(api, data);
        }
        if (!mops.isEmpty()) {
            JsonElement mt = map.json.get("mop_type_with_order");
            if (mt == null || !mt.isJsonArray()) {
                logger.warn("Mop type per room is not supported by this map (mop_type_with_order missing)");
                return false;
            }
            Map<Integer, String> current = new LinkedHashMap<>();
            for (JsonElement g : mt.getAsJsonArray()) {
                for (Map.Entry<String, JsonElement> e : g.getAsJsonObject().entrySet()) {
                    String type = e.getKey().substring(e.getKey().length() - 1).toUpperCase(Locale.ROOT);
                    if (e.getValue().isJsonArray()) {
                        for (JsonElement id : e.getValue().getAsJsonArray()) {
                            current.put(id.getAsInt(), type);
                        }
                    }
                }
            }
            current.putAll(mops);
            Map<String, Object> types = new LinkedHashMap<>();
            for (String key : new java.util.TreeSet<>(segInf.keySet())) {
                String t = current.getOrDefault(Integer.parseInt(key), "A");
                types.put(key, t.equals("C") ? 1 : t.equals("B") ? 2 : 3);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("RoomMopType", types);
            ok &= sendMapDataMap(api, data);
        }
        scheduler.schedule(() -> triggerMapLoad(api), 8, TimeUnit.SECONDS);
        return ok;
    }

    /** Erledigte, übersprungene und offene Räume des laufenden Auftrags (Karten-Felder aus Tasshack v2.0.2). */
    private String roomProgressText(MovaMap.Result r) {
        String[][] fields = { { "combinecleanedarea", MovaI18n.t("erledigt", "done") },
                { "combineskipedarea", MovaI18n.t("übersprungen", "skipped") },
                { "combinenotcleanedarea", MovaI18n.t("offen", "pending") } };
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (String[] f : fields) {
            JsonElement el = r.json.get(f[0]);
            if (el == null || !el.isJsonArray()) {
                continue;
            }
            any = true;
            StringBuilder names = new StringBuilder();
            for (JsonElement item : el.getAsJsonArray()) {
                if (!item.isJsonArray() || item.getAsJsonArray().size() != 1) {
                    continue; // Rechtecke (Bereiche) nicht als Raum ausgeben
                }
                try {
                    int id = item.getAsJsonArray().get(0).getAsInt();
                    MovaMap.Room room = roomsById.get(id);
                    names.append(names.length() > 0 ? ", " : "").append(room != null ? room.name : MovaI18n.t("Raum ", "Room ") + id);
                } catch (RuntimeException ex) {
                    // unlesbarer Eintrag: überspringen
                }
            }
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(f[1]).append(": ").append(names.length() > 0 ? names : "-");
        }
        return any ? sb.toString() : MovaI18n.t("keine Daten", "no data");
    }

    private static final Map<Integer, String> OBSTACLE_NAMES = Map.ofEntries(Map.entry(128, "Station"),
            Map.entry(129, "Waage"), Map.entry(130, "Kabel/Faden"), Map.entry(131, "Kabel"), Map.entry(132, "Spielzeug"),
            Map.entry(133, "Schuhe"), Map.entry(134, "Socke"), Map.entry(135, "Kot"), Map.entry(136, "Mülleimer"),
            Map.entry(137, "Stoff"), Map.entry(138, "Steckdosenleiste"), Map.entry(139, "Flüssigkeitsfleck"),
            Map.entry(142, "Hindernis"), Map.entry(158, "Haustier"), Map.entry(163, "Reinigungsgeräte"),
            Map.entry(169, "Fleck"), Map.entry(200, "Blockierter Raum"), Map.entry(201, "Möbel (Festfahrgefahr)"),
            Map.entry(202, "Nicht reinigbarer Fleck"), Map.entry(204, "Möbelbein"), Map.entry(205, "Große Partikel"),
            Map.entry(206, "Getrockneter Fleck"), Map.entry(209, "Futternapf"), Map.entry(210, "Tierbett"),
            Map.entry(225, "Haare"), Map.entry(226, "Papier"), Map.entry(229, "Sonstiges"));

    private static final Map<Integer, String> OBSTACLE_NAMES_EN = Map.ofEntries(Map.entry(128, "Base station"),
            Map.entry(129, "Scale"), Map.entry(130, "Thread/cord"), Map.entry(131, "Wire"), Map.entry(132, "Toy"),
            Map.entry(133, "Shoes"), Map.entry(134, "Sock"), Map.entry(135, "Poop"), Map.entry(136, "Trash can"),
            Map.entry(137, "Fabric"), Map.entry(138, "Power strip"), Map.entry(139, "Liquid stain"),
            Map.entry(142, "Obstacle"), Map.entry(158, "Pet"), Map.entry(163, "Cleaning tools"),
            Map.entry(169, "Stain"), Map.entry(200, "Blocked room"), Map.entry(201, "Furniture (risk of getting stuck)"),
            Map.entry(202, "Uncleanable stain"), Map.entry(204, "Furniture leg"), Map.entry(205, "Large particles"),
            Map.entry(206, "Dried stain"), Map.entry(209, "Pet bowl"), Map.entry(210, "Pet bed"),
            Map.entry(225, "Hair"), Map.entry(226, "Paper"), Map.entry(229, "Other"));

    /** Hindernisse aus ai_obstacle der Karte, nummeriert wie in der Referenz (ab 1). */
    private List<JsonArray> obstacleList(MovaMap.Result r) {
        List<JsonArray> out = new ArrayList<>();
        JsonElement el = r.json.get("ai_obstacle");
        if (el == null || !el.isJsonArray()) {
            return out;
        }
        for (JsonElement e : el.getAsJsonArray()) {
            if (!e.isJsonArray() || e.getAsJsonArray().size() < 4) {
                continue;
            }
            JsonArray o = e.getAsJsonArray();
            try {
                int type = o.get(2).getAsInt();
                boolean small = o.size() > 17 && "1".equals(o.get(17).getAsString())
                        && ((type >= 128 && type <= 138) || type == 142);
                if (!OBSTACLE_NAMES.containsKey(type) && (type == 160 || type == 166 || type == 207 || small)) {
                    continue;
                }
                int status = obstacleStatus(o);
                if (status == 3 || status == 4) {
                    continue;
                }
                out.add(o);
            } catch (RuntimeException ex) {
                // unlesbarer Eintrag: überspringen
            }
        }
        return out;
    }

    private int obstacleStatus(JsonArray o) {
        if (o.size() > 18) {
            return o.get(18).getAsInt();
        }
        String last = o.get(o.size() - 1).getAsString();
        return last.length() == 1 && last.charAt(0) >= '0' && last.charAt(0) <= '4' ? Integer.parseInt(last) : 0;
    }

    private String obstaclesText(MovaMap.Result r) {
        List<JsonArray> list = obstacleList(r);
        if (list.isEmpty()) {
            return MovaI18n.t("keine", "none");
        }
        StringBuilder sb = new StringBuilder();
        int n = 1;
        for (JsonArray o : list) {
            int type = o.get(2).getAsInt();
            int status = obstacleStatus(o);
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(n++).append(": ")
                    .append(MovaI18n.isGerman() ? OBSTACLE_NAMES.getOrDefault(type, "Hindernis")
                            : OBSTACLE_NAMES_EN.getOrDefault(type, "Obstacle"))
                    .append(MovaI18n.t(" bei ", " at "))
                    .append((int) o.get(0).getAsDouble()).append(',').append((int) o.get(1).getAsDouble());
            if (status == 1) {
                sb.append(MovaI18n.t(" (ignoriert)", " (ignored)"));
            } else if (status == 2) {
                sb.append(MovaI18n.t(" (automatisch ignoriert)", " (ignored automatically)"));
            }
        }
        return sb.toString();
    }

    /** Hindernis ignorieren: "Nummer=ignore" oder "Nummer=restore" (Nummer aus dem Channel obstacles). */
    private boolean handleObstacleIgnore(MovaCloudApi api, String value) throws MovaCloudException {
        if (wasCleaning) {
            logger.warn("Obstacles cannot be changed while the robot is cleaning");
            return false;
        }
        MovaMap.Result map = lastMap;
        if (map == null) {
            logger.warn("Ignore obstacle: the map is not loaded yet");
            return false;
        }
        String[] parts = value.trim().split("=", 2);
        if (parts.length != 2) {
            logger.warn("Ignore obstacle: expected number=ignore|restore, got '{}'", value);
            return false;
        }
        int index;
        try {
            index = Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException e) {
            logger.warn("Ignore obstacle: invalid number '{}'", parts[0]);
            return false;
        }
        String action = parts[1].trim().toLowerCase(Locale.ROOT);
        boolean ignore;
        if ("ignore".equals(action) || "on".equals(action)) {
            ignore = true;
        } else if ("restore".equals(action) || "off".equals(action)) {
            ignore = false;
        } else {
            logger.warn("Ignore obstacle: unknown action '{}' (ignore or restore)", parts[1]);
            return false;
        }
        List<JsonArray> list = obstacleList(map);
        if (index < 1 || index > list.size()) {
            logger.warn("Ignore obstacle: number {} does not exist ({} obstacles)", index, list.size());
            return false;
        }
        JsonArray o = list.get(index - 1);
        if (obstacleStatus(o) == 2) {
            logger.warn("Obstacle {} was ignored automatically and cannot be changed", index);
            return false;
        }
        String key = o.size() > 6 ? o.get(6).getAsString().trim() : "";
        List<Object> payload = new ArrayList<>();
        if (!key.isEmpty()) {
            payload.add(key);
            payload.add(ignore ? 1 : 0);
        } else {
            payload.add((int) o.get(0).getAsDouble());
            payload.add((int) o.get(1).getAsDouble());
            payload.add(o.get(2).getAsInt());
            payload.add(ignore ? 1 : 0);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("obstacleignore", payload);
        boolean ok = sendMapDataMap(api, data);
        scheduler.schedule(() -> triggerMapLoad(api), 8, TimeUnit.SECONDS);
        return ok;
    }

    /** Karten-Befehle: "rename:Neuer Name" (aktuelle Karte) oder "backup" (aktuelle Karte in der Cloud sichern). */
    private boolean handleMapCommand(MovaCloudApi api, String value) throws MovaCloudException {
        if (wasCleaning) {
            logger.warn("Map commands are not possible during cleaning");
            return false;
        }
        MovaMap.Result map = lastMap;
        if (map == null) {
            logger.warn("Map command: the map is not loaded yet");
            return false;
        }
        String v = value.trim();
        String lower = v.toLowerCase(Locale.ROOT);
        if (lower.startsWith("rename:")) {
            String name = v.substring(7).trim();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name.isEmpty() ? null : name);
            Map<String, Object> maps = new LinkedHashMap<>();
            maps.put(String.valueOf(map.mapId), entry);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("nrism", maps);
            boolean ok = sendMapDataMap(api, data);
            scheduler.schedule(() -> triggerMapLoad(api), 8, TimeUnit.SECONDS);
            return ok;
        }
        if ("backup".equals(lower)) {
            List<Object> in = new ArrayList<>();
            in.add(piidValue(MovaStation.PIID_MAP_EXTEND_DATA, String.valueOf(map.mapId)));
            logger.debug("Backing up map {}", map.mapId);
            return api.sendAction(did, MovaStation.SIID_MAP, 3, in);
        }
        logger.warn("Unknown map command '{}' (rename:name or backup)", value);
        return false;
    }

    private boolean sendMapDataMap(MovaCloudApi api, Map<String, Object> data) throws MovaCloudException {
        String json = MovaSigner.toJson(data, false);
        logger.debug("Sending map data: {}", json);
        List<Object> in = new ArrayList<>();
        in.add(piidValue(MovaStation.PIID_MAP_EXTEND_DATA, json));
        return api.sendAction(did, MovaStation.SIID_MAP, MovaStation.AIID_UPDATE_MAP_DATA, in);
    }

    private boolean sendMapData(MovaCloudApi api, String key, Object payload) throws MovaCloudException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(key, payload);
        String json = MovaSigner.toJson(data, false);
        logger.debug("Sending room settings: {}", json);
        List<Object> in = new ArrayList<>();
        in.add(piidValue(MovaStation.PIID_MAP_EXTEND_DATA, json));
        return api.sendAction(did, MovaStation.SIID_MAP, MovaStation.AIID_UPDATE_MAP_DATA, in);
    }

    private static Map<String, Object> piidValue(int piid, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("piid", piid);
        m.put("value", value);
        return m;
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> job = pollJob;
        if (job != null) {
            job.cancel(true);
            pollJob = null;
        }
        super.dispose();
    }
}
