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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Entschlüsselt und liest die Kartendaten der Cloud (Aufbau nach Tasshack/dreame-vacuum v2.0.1):
 * Base64 (URL-sicher) -> optional AES-CBC -> zlib -> 27 Byte Kopf + Bild (Breite x Höhe Bytes) + JSON.
 * Aktuell werden nur die Räume (JSON-Teil "seg_inf") ausgewertet.
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public final class MovaMap {

    private static final int HEADER_SIZE = 27;

    /** Je Modell hinterlegter AES-Anfangswert (IV) aus der Referenz-Integration. */
    private static final Map<String, String> IV_BY_MODEL = Map.of("r2582a", "NRwnBj5FsNPgBNbT");

    private static final String[] TYPE_NAMES = { "Raum", "Wohnzimmer", "Hauptschlafzimmer", "Arbeitszimmer",
            "Küche", "Esszimmer", "Badezimmer", "Balkon", "Flur", "Hauswirtschaftsraum", "Ankleidezimmer",
            "Besprechungsraum", "Büro", "Fitnessbereich", "Freizeitbereich", "Zweites Schlafzimmer" };
    private static final String[] TYPE_NAMES_EN = { "Room", "Living Room", "Primary Bedroom", "Study", "Kitchen",
            "Dining Hall", "Bathroom", "Balcony", "Corridor", "Utility Room", "Closet", "Meeting Room", "Office",
            "Fitness Area", "Recreation Area", "Secondary Bedroom" };

    /** Ein Raum der Karte. */
    public static final class Room {
        public final int id;
        public final String name;
        /** Einstellungen aus "cleanset" (null = nicht vorhanden). */
        public @Nullable Integer suction;
        public @Nullable Integer waterVolume; // 1 niedrig, 2 mittel, 3 hoch
        public @Nullable Integer wetnessLevel; // genaue Wasserstufe, falls vorhanden
        public @Nullable Integer times;
        public @Nullable Integer mode; // 0 saugen, 1 wischen, 2 beides, 3 erst saugen dann wischen
        public @Nullable Integer order; // Reinigungsreihenfolge (1 = zuerst), null = keine
        public @Nullable Long lastCleaned; // Unix-Zeit in Sekunden

        Room(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** Ergebnis der Auswertung. */
    public static final class Result {
        public final int mapId;
        public final int frameId;
        public final int width;
        public final int height;
        public final JsonObject json;
        public final List<Room> rooms;
        /** Einträge der Karte ohne Fläche im Bild (z. B. nach dem Zusammenlegen von Räumen), nicht in rooms. */
        public final List<Room> unmapped;
        /** Diagnose: Bildgröße, Pixelwert-Häufigkeit und seg_inf der Raumkarte. */
        public String diagnostics = "";
        /** Kartenbild der Raumkarte (Breite x Höhe Bytes) und Umrechnung Karten-Koordinaten -> Pixel. */
        public byte[] image = new byte[0];
        public int imgWidth;
        public int imgHeight;
        public int left;
        public int top;
        public int grid;
        public int @Nullable [] robot;
        public int @Nullable [] charger;
        /** Drehung der Karte in Grad (Kartenfeld "mra"), 0 wenn unbekannt. */
        public int rotation;

        Result(int mapId, int frameId, int width, int height, JsonObject json, List<Room> rooms,
                List<Room> unmapped) {
            this.unmapped = unmapped;
            this.mapId = mapId;
            this.frameId = frameId;
            this.width = width;
            this.height = height;
            this.json = json;
            this.rooms = rooms;
        }
    }

    private MovaMap() {
    }

    /** Eingebauter IV für ein Modell (Teil nach dem letzten Punkt, z. B. "r2582a") oder null. */
    public static @Nullable String ivForModel(String model) {
        String shortModel = model.substring(model.lastIndexOf('.') + 1);
        return IV_BY_MODEL.get(shortModel);
    }

    /**
     * Dekodiert eine Karte.
     *
     * @param rawIn Inhalt der heruntergeladenen Datei
     * @param keyIn Schlüssel aus dem Objektnamen ("Pfad,Schlüssel") oder null
     * @param iv AES-Anfangswert (16 Zeichen), nur nötig wenn verschlüsselt wird
     */
    public static Result decode(String rawIn, @Nullable String keyIn, @Nullable String iv)
            throws MovaCloudException {
        String raw = rawIn.trim().replace('_', '/').replace('-', '+');
        if (raw.length() < 3) {
            throw new MovaCloudException("Map data too short", null);
        }
        String key = keyIn;
        if (key == null && raw.contains(",")) {
            String[] parts = raw.split(",");
            raw = parts[0];
            key = parts.length > 1 ? parts[1] : null;
        }
        byte[] data = Base64.getMimeDecoder().decode(raw.getBytes(StandardCharsets.UTF_8));

        if (key != null) {
            if (iv == null || iv.getBytes(StandardCharsets.UTF_8).length != 16) {
                throw new MovaCloudException(
                        "Map is encrypted, but no map key (IV, 16 characters) is known for this model",
                        null);
            }
            data = decrypt(data, key, iv);
        }
        byte[] plain = inflate(data);
        if (plain.length < HEADER_SIZE) {
            throw new MovaCloudException("Map header too short (" + plain.length + " Byte)", null);
        }
        int mapId = le16(plain, 0);
        int frameId = le16(plain, 2);
        int width = le16(plain, 19);
        int height = le16(plain, 21);
        int imageSize = HEADER_SIZE + width * height;
        if (plain.length <= imageSize) {
            throw new MovaCloudException("Map contains no JSON part (image " + width + "x" + height + ")", null);
        }
        String jsonText = new String(plain, imageSize, plain.length - imageSize, StandardCharsets.UTF_8).trim();
        JsonObject json;
        try {
            JsonElement parsed = JsonParser.parseString(jsonText);
            if (!parsed.isJsonObject()) {
                throw new MovaCloudException("Map JSON is not an object", null);
            }
            json = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new MovaCloudException("Map JSON not readable: " + e.getMessage(), e);
        }
        List<Room> found = rooms(json);
        List<Room> unmapped = new ArrayList<>();
        String diagnostics = "";
        if (!found.isEmpty()) {
            java.util.TreeMap<Integer, Integer> hist = new java.util.TreeMap<>();
            for (int i = 0; i < width * height; i++) {
                hist.merge(plain[HEADER_SIZE + i] & 0xff, 1, Integer::sum);
            }
            diagnostics = "Bild " + width + "x" + height + ", Pixelwerte (Wert=Anzahl): " + hist + ", seg_inf: "
                    + json.get("seg_inf") + ", Felder: " + summary(json);
        }
        if (!found.isEmpty()) {
            // Nur Räume mit Fläche im Bild (Pixel: Bit 7 = Wand, sonst unteren 6 Bit = Raum-Nummer)
            java.util.Set<Integer> present = new java.util.HashSet<>();
            for (int i = 0; i < width * height; i++) {
                int px = plain[HEADER_SIZE + i] & 0xff;
                if (px > 0 && (px >> 7) == 0 && (px & 0x3f) > 0) {
                    present.add(px & 0x3f);
                }
            }
            List<Room> kept = new ArrayList<>();
            for (Room r : found) {
                (present.contains(r.id) ? kept : unmapped).add(r);
            }
            if (kept.isEmpty()) {
                unmapped.clear(); // Bildformat unklar: lieber alles anzeigen als nichts
            } else {
                found = kept;
            }
        }
        Result nestedResult = null;
        JsonElement rism = json.get("rism");
        if (found.isEmpty() && rism != null && rism.isJsonPrimitive() && !rism.getAsString().isEmpty()) {
            // Die laufende Karte enthält keine Räume; sie stecken in der gespeicherten Karte ("rism").
            try {
                Result nested = decode(rism.getAsString(), null, iv);
                nestedResult = nested;
                found = nested.rooms;
                unmapped = nested.unmapped;
                diagnostics = nested.diagnostics + " | Felder der laufenden Karte: " + summary(json);
            } catch (MovaCloudException | RuntimeException e) {
                throw new MovaCloudException("Saved map (rism) not readable: " + e.getMessage(), e);
            }
        }
        applyRoomSettings(json, found);
        // In der App ausgeblendete Räume stehen im Kartenfeld "delsr" (Liste von Raum-Nummern)
        java.util.Set<Integer> hidden = hiddenIds(json);
        if (!hidden.isEmpty()) {
            List<Room> visible = new ArrayList<>();
            for (Room r : found) {
                (hidden.contains(r.id) ? unmapped : visible).add(r);
            }
            found = visible;
        }
        Result result = new Result(mapId, frameId, width, height, json, found, unmapped);
        result.diagnostics = diagnostics;
        result.image = java.util.Arrays.copyOfRange(plain, HEADER_SIZE, imageSize);
        result.imgWidth = width;
        result.imgHeight = height;
        result.grid = le16(plain, 17);
        result.left = (short) le16(plain, 23);
        result.top = (short) le16(plain, 25);
        result.robot = point(json.get("robot"));
        result.charger = point(json.get("charger"));
        result.rotation = (int) longOf(json.get("mra"), 0);
        if (nestedResult != null) {
            // Felder der gespeicherten Karte (seg_inf, delsr, mop_type_with_order, ...) übernehmen, soweit die
            // laufende Karte sie nicht selbst enthält; sonst schlagen z. B. Räume bearbeiten oder Raumfortschritt fehl
            for (Map.Entry<String, JsonElement> e : nestedResult.json.entrySet()) {
                if (!json.has(e.getKey())) {
                    json.add(e.getKey(), e.getValue());
                }
            }
            result.image = nestedResult.image;
            result.imgWidth = nestedResult.imgWidth;
            result.imgHeight = nestedResult.imgHeight;
            result.grid = nestedResult.grid;
            result.left = nestedResult.left;
            result.top = nestedResult.top;
            if (result.charger == null) {
                result.charger = nestedResult.charger;
            }
            if (result.robot == null) {
                result.robot = nestedResult.robot;
            }
        }
        return result;
    }

    private static byte[] decrypt(byte[] data, String key, String iv) throws MovaCloudException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            }
            byte[] aesKey = hex.substring(0, 32).getBytes(StandardCharsets.US_ASCII);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                    new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8)));
            int usable = data.length - (data.length % 16);
            return cipher.doFinal(data, 0, usable);
        } catch (GeneralSecurityException e) {
            throw new MovaCloudException("Could not decrypt the map: " + e.getMessage(), e);
        }
    }

    private static byte[] inflate(byte[] data) throws MovaCloudException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 4);
            byte[] buffer = new byte[16384];
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                out.write(buffer, 0, n);
            }
            if (!inflater.finished()) {
                throw new MovaCloudException("Map data incomplete or decrypted incorrectly", null);
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new MovaCloudException(
                    "Could not decompress the map data (wrong key/IV?): " + e.getMessage(), e);
        } finally {
            inflater.end();
        }
    }

    /** Liest cleanset, cleanareaorder und area_complete_clean_time (falls in dieser Karte vorhanden). */
    private static void applyRoomSettings(JsonObject json, List<Room> rooms) {
        JsonObject cleanset = null;
        JsonElement cs = json.get("cleanset");
        try {
            if (cs != null && cs.isJsonPrimitive()) {
                cs = JsonParser.parseString(cs.getAsString());
            }
            if (cs != null && cs.isJsonObject()) {
                cleanset = cs.getAsJsonObject();
            }
        } catch (RuntimeException e) {
            cleanset = null;
        }
        Map<Integer, Integer> order = idMap(json.get("cleanareaorder"));
        Map<Integer, Integer> lastTimes = idMap(json.get("area_complete_clean_time"));
        for (Room r : rooms) {
            if (cleanset != null && cleanset.has(String.valueOf(r.id)) && cleanset.get(String.valueOf(r.id)).isJsonArray()) {
                com.google.gson.JsonArray a = cleanset.getAsJsonArray(String.valueOf(r.id));
                r.suction = arrInt(a, 0);
                Integer water = arrInt(a, 1);
                r.times = arrInt(a, 2);
                r.mode = arrInt(a, 4);
                Integer mop = arrInt(a, 5);
                r.waterVolume = water != null && water > 1 && water < 5 ? water - 1 : 1;
                if (mop != null && mop > 0) {
                    int v1 = (mop >> 4) & 0x0f;
                    int v2 = (mop >> 8) & 0x0f;
                    if (v2 >= 1 && v2 <= 3) {
                        r.waterVolume = v2;
                    } else if (v2 == 0 && v1 == 0 && water != null && water > 0) {
                        r.wetnessLevel = water;
                    }
                }
            }
            Integer o = order.get(r.id);
            if (o != null) {
                r.order = o;
            }
            Integer t = lastTimes.get(r.id);
            if (t != null) {
                r.lastCleaned = t.longValue();
            }
        }
    }

    private static @Nullable Integer arrInt(com.google.gson.JsonArray a, int i) {
        try {
            return i < a.size() ? Integer.valueOf(a.get(i).getAsInt()) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Liste der Form [{"1":4},{"2":2}] als Map Raum-Nummer -> Wert. */
    private static Map<Integer, Integer> idMap(@Nullable JsonElement el) {
        Map<Integer, Integer> m = new java.util.HashMap<>();
        if (el == null || !el.isJsonArray()) {
            return m;
        }
        for (JsonElement item : el.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            for (Map.Entry<String, JsonElement> e : item.getAsJsonObject().entrySet()) {
                try {
                    m.put(Integer.parseInt(e.getKey()), e.getValue().getAsInt());
                } catch (RuntimeException ex) {
                    // ungültiger Eintrag wird ignoriert
                }
            }
        }
        return m;
    }

    /** Liest [x,y] als Array oder als Text "[x,y]". */
    private static int @Nullable [] point(@Nullable JsonElement el) {
        try {
            JsonElement arr = el;
            if (arr != null && arr.isJsonPrimitive()) {
                arr = JsonParser.parseString(arr.getAsString());
            }
            if (arr != null && arr.isJsonArray() && arr.getAsJsonArray().size() >= 2) {
                return new int[] { arr.getAsJsonArray().get(0).getAsInt(), arr.getAsJsonArray().get(1).getAsInt() };
            }
        } catch (RuntimeException e) {
            // nicht lesbar
        }
        return null;
    }

    private static long longOf(@Nullable JsonElement el, long fallback) {
        try {
            return el != null && el.isJsonPrimitive() ? Long.parseLong(el.getAsString().trim()) : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static java.util.Set<Integer> hiddenIds(JsonObject json) {
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        JsonElement delsr = json.get("delsr");
        if (delsr != null && delsr.isJsonArray()) {
            for (JsonElement e : delsr.getAsJsonArray()) {
                try {
                    ids.add(e.getAsInt());
                } catch (RuntimeException ex) {
                    // ungültiger Eintrag wird ignoriert
                }
            }
        }
        return ids;
    }

    /** Alle Felder außer Karten-Blobs, je auf 300 Zeichen gekürzt (nur für die Diagnose). */
    private static String summary(JsonObject json) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            if (e.getKey().equals("rism") || e.getKey().equals("seg_inf")) {
                continue;
            }
            String v = e.getValue().toString();
            sb.append(sb.length() > 0 ? "; " : "").append(e.getKey()).append('=')
                    .append(v.length() > 300 ? v.substring(0, 300) + "..." : v);
        }
        return sb.toString();
    }

    private static int le16(byte[] d, int offset) {
        return (d[offset] & 0xff) | ((d[offset + 1] & 0xff) << 8);
    }

    private static List<Room> rooms(JsonObject json) {
        List<Room> rooms = new ArrayList<>();
        JsonElement segInf = json.get("seg_inf");
        if (segInf == null || !segInf.isJsonObject()) {
            return rooms;
        }
        for (Map.Entry<String, JsonElement> e : segInf.getAsJsonObject().entrySet()) {
            int id;
            try {
                id = Integer.parseInt(e.getKey());
            } catch (NumberFormatException ex) {
                continue;
            }
            JsonObject v = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject() : new JsonObject();
            int type = intOf(v, "type", 0);
            int index = intOf(v, "index", 0);
            String name = null;
            JsonElement nameEl = v.get("name");
            if (nameEl != null && nameEl.isJsonPrimitive() && !nameEl.getAsString().isEmpty()) {
                try {
                    name = new String(Base64.getMimeDecoder().decode(nameEl.getAsString()), StandardCharsets.UTF_8);
                } catch (IllegalArgumentException ex) {
                    name = null;
                }
            }
            if (name == null || name.isBlank()) {
                if (type > 0 && type < TYPE_NAMES.length) {
                    String tn = MovaI18n.t(TYPE_NAMES[type], TYPE_NAMES_EN[type]);
                    name = index > 0 ? tn + " " + (index + 1) : tn;
                } else {
                    name = MovaI18n.t("Raum ", "Room ") + id;
                }
            }
            rooms.add(new Room(id, name));
        }
        rooms.sort((a, b) -> Integer.compare(a.id, b.id));
        return rooms;
    }

    private static int intOf(JsonObject o, String key, int fallback) {
        JsonElement el = o.get(key);
        if (el == null || !el.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return el.getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
