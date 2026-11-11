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
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.binding.movavacuum.internal.dto.DeviceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Client für die DreameHome/Mova-Cloud-API.
 *
 * Ablauf und Werte entsprechen Tasshack/dreame-vacuum v2.0.1
 * (DreameVacuumDreameHomeCloudProtocol):
 * <ul>
 * <li>Login per OAuth2-Password-Grant (Passwort = MD5(Passwort + Salz)), später Refresh-Token.</li>
 * <li>Alle Aufrufe mit Parametern werden signiert (sign + timestamp, siehe {@link MovaSigner}).</li>
 * <li>Zustand und Befehle laufen über "sendCommand" an einen gerätespezifischen Host
 * (dreame-iot-com-&lt;bindDomain-Präfix&gt;), Methode "get_properties" bzw. "action".</li>
 * </ul>
 *
 * WICHTIG: Die Python-Vorlage baut ihre TLS-Verbindung selbst auf und sendet einen gezielt
 * zusammengestellten Client-Hello. Ob die Cloud Verbindungen mit dem Standard-TLS von Java
 * akzeptiert, ist nicht geprüft. Schlägt schon der Login mit einem TLS-/Verbindungsfehler
 * oder einem HTTP-Fehler vor der Authentifizierung fehl, liegt es vermutlich daran.
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public class MovaCloudApi {

    private static final int TIMEOUT_SECONDS = 10;

    private final Logger logger = LoggerFactory.getLogger(MovaCloudApi.class);
    private final Gson gson = new Gson();
    private final HttpClient httpClient;

    private final String username;
    private final String password;
    private final String accountType;
    private final String country;

    // je Marke
    private final String hostSuffix;
    private final String basicAuth;
    private final String clientSecret;
    private final String versionId;
    private final String userAgent;
    private final String defaultTenantId;
    private final boolean withMetaHashes;

    private final String visitorHash;
    private final Map<String, String> bindDomainByDid = new HashMap<>();
    private final Map<String, String> modelByDid = new HashMap<>();
    private final Map<String, String> uidByDid = new HashMap<>();
    private int requestId = new SecureRandom().nextInt(100) + 1;

    // Sitzung
    private @Nullable String accessToken;
    private @Nullable String refreshToken;
    private @Nullable String tenantId;
    private @Nullable String region;
    private @Nullable String lang;
    private @Nullable String countryCode;
    private long expiryEpochSeconds;

    public MovaCloudApi(HttpClient httpClient, String username, String password, String accountType,
            String country) {
        this.httpClient = httpClient;
        this.username = username;
        this.password = password;
        this.accountType = accountType.toLowerCase(Locale.ROOT);
        this.country = country.toLowerCase(Locale.ROOT);

        switch (this.accountType) {
            case MovaBindingConstants.ACCOUNT_MOVA -> {
                hostSuffix = MovaBindingConstants.HOST_SUFFIX_MOVA;
                basicAuth = MovaBindingConstants.BASIC_MOVA;
                clientSecret = MovaBindingConstants.CLIENT_SECRET_MOVA;
                versionId = MovaBindingConstants.VERSION_ID_MOVA;
                userAgent = MovaBindingConstants.USER_AGENT_MOVA;
                defaultTenantId = MovaBindingConstants.TENANT_ID_MOVA;
                withMetaHashes = true;
            }
            case MovaBindingConstants.ACCOUNT_TROUVER -> {
                hostSuffix = MovaBindingConstants.HOST_SUFFIX_TROUVER;
                basicAuth = MovaBindingConstants.BASIC_TROUVER;
                clientSecret = MovaBindingConstants.CLIENT_SECRET_TROUVER;
                versionId = MovaBindingConstants.VERSION_ID_TROUVER;
                userAgent = MovaBindingConstants.USER_AGENT_TROUVER;
                defaultTenantId = MovaBindingConstants.TENANT_ID_TROUVER;
                withMetaHashes = false;
            }
            default -> {
                hostSuffix = MovaBindingConstants.HOST_SUFFIX_DREAME;
                basicAuth = MovaBindingConstants.BASIC_DREAME;
                clientSecret = MovaBindingConstants.CLIENT_SECRET_DREAME;
                versionId = MovaBindingConstants.VERSION_ID_DREAME;
                userAgent = MovaBindingConstants.USER_AGENT_DREAME;
                defaultTenantId = MovaBindingConstants.TENANT_ID_DREAME;
                withMetaHashes = true;
            }
        }
        byte[] rnd = new byte[16];
        new SecureRandom().nextBytes(rnd);
        this.visitorHash = MovaSigner.md5Hex(new String(rnd, StandardCharsets.ISO_8859_1));
    }

    private String apiBase() {
        return "https://" + country + hostSuffix + ":" + MovaBindingConstants.API_PORT;
    }

    /** Wie Python urllib.parse.quote(value, safe=''). */
    private static String percentEncode(String value) {
        StringBuilder sb = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.'
                    || c == '-' || c == '~') {
                sb.append((char) c);
            } else {
                sb.append('%').append(String.format(Locale.ROOT, "%02X", c));
            }
        }
        return sb.toString();
    }

    private void applyBaseHeaders(Request request) {
        // Dreame-Konten in Korea senden weder dreame-meta noch dreame-rlc (wie in der Vorlage v2.0.1)
        boolean kr = "kr".equals(country) && MovaBindingConstants.ACCOUNT_DREAME.equals(accountType);
        request.header("user-agent", userAgent);
        if (!kr) {
            request.header("dreame-meta",
                    MovaSigner.metaHeader(versionId, withMetaHashes, username, visitorHash));
        }
        request.header("accept-encoding", "gzip");
        String r = region;
        String l = lang;
        String c = countryCode;
        if (!kr && r != null && l != null && c != null) {
            try {
                request.header("dreame-rlc", MovaSigner.rlcHeader(r, l, c, clientSecret));
            } catch (GeneralSecurityException e) {
                logger.debug("Could not create the dreame-rlc header: {}", e.getMessage());
            }
        }
        String t = tenantId;
        request.header("tenant-id", t != null ? t : defaultTenantId);
        request.header("authorization", "Basic " + basicAuth);
        if (MovaBindingConstants.ACCOUNT_MOVA.equals(accountType)) {
            request.header("dreame-psd", "new");
        }
    }

    /**
     * Meldet sich an (oder erneuert mit dem Refresh-Token). Wirft bei Verbindungsfehlern eine Exception.
     *
     * @return true bei erfolgreichem Login, false bei abgelehnten Zugangsdaten
     */
    public synchronized boolean login() throws MovaCloudException {
        return login(true);
    }

    private boolean login(boolean allowRetryWithPassword) throws MovaCloudException {
        boolean usedRefresh = refreshToken != null;
        String body;
        if (usedRefresh) {
            body = "grant_type=refresh_token&scope=all&platform=ANDROID&type=account&refresh_token=" + refreshToken;
        } else {
            String pw = MovaSigner.md5Hex(password + MovaBindingConstants.PASSWORD_SALT);
            body = "grant_type=password&scope=all&platform=ANDROID&type=account&username=" + percentEncode(username)
                    + "&password=" + pw;
            String c = countryCode;
            String l = lang;
            if (c != null && l != null) {
                body += "&country=" + c + "&lang=" + l;
            }
        }

        try {
            Request request = httpClient.newRequest(apiBase() + MovaBindingConstants.LOGIN_PATH)
                    .method(HttpMethod.POST).timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            applyBaseHeaders(request);
            request.content(new StringContentProvider("application/x-www-form-urlencoded", body,
                    StandardCharsets.UTF_8));
            ContentResponse response = request.send();
            int status = response.getStatus();
            String text = response.getContentAsString();

            if (status == 200) {
                JsonObject json = gson.fromJson(text, JsonObject.class);
                if (json != null && json.has("access_token")) {
                    accessToken = json.get("access_token").getAsString();
                    refreshToken = optString(json, "refresh_token");
                    long expiresIn = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600;
                    expiryEpochSeconds = System.currentTimeMillis() / 1000 + expiresIn;
                    region = optString(json, "region") != null ? optString(json, "region") : region;
                    lang = optString(json, "lang") != null ? optString(json, "lang") : lang;
                    countryCode = optString(json, "country") != null ? optString(json, "country") : countryCode;
                    String ti = optString(json, "tenant_id");
                    if (ti != null) {
                        tenantId = ti;
                    }
                    logger.debug("Cloud login successful for {} (region {}, country {})", accountType, region,
                            countryCode);
                    return true;
                }
                logger.warn("Login response without access_token: {}", text);
                return false;
            }
            if (status == 400 || status == 401 || status == 403) {
                if (usedRefresh && allowRetryWithPassword) {
                    // Refresh-Token abgelehnt: einmal mit Passwort neu versuchen
                    refreshToken = null;
                    return login(false);
                }
                logger.warn("Login rejected (HTTP {}): {}", status, text);
                return false;
            }
            throw new MovaCloudException("Login failed: HTTP " + status + " - " + text, null);
        } catch (MovaCloudException e) {
            throw e;
        } catch (Exception e) {
            throw new MovaCloudException("Login failed: " + e.getClass().getSimpleName() + ": "
                    + e.getMessage(), e);
        }
    }

    private static @Nullable String optString(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e != null && !e.isJsonNull() ? e.getAsString() : null;
    }

    private synchronized void ensureLoggedIn() throws MovaCloudException {
        long remaining = expiryEpochSeconds - System.currentTimeMillis() / 1000;
        if (accessToken == null || remaining <= 600) {
            if (!login()) {
                throw new MovaCloudException("Login rejected - check credentials/account type/region", null);
            }
        }
    }

    /**
     * Führt einen Cloud-Aufruf aus. Parameter werden signiert; null sendet einen leeren Body.
     *
     * @return geparste JSON-Antwort oder null bei HTTP-Fehler
     */
    private @Nullable JsonObject call(String path, @Nullable Map<String, Object> params, boolean retried)
            throws MovaCloudException {
        return call(path, params, retried, 0);
    }

    /** Wie oben; bei "zu viele Anfragen" (429) oder "nicht verfügbar" (503) bis zu zwei Wiederholungen (Tasshack v2.0.2). */
    private @Nullable JsonObject call(String path, @Nullable Map<String, Object> params, boolean retried, int attempt)
            throws MovaCloudException {
        ensureLoggedIn();
        try {
            Request request = httpClient.newRequest(apiBase() + "/" + path).method(HttpMethod.POST)
                    .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            applyBaseHeaders(request);
            String t = accessToken;
            if (t != null) {
                request.header("dreame-auth", t);
            }
            if (params != null) {
                String body = MovaSigner.toJson(MovaSigner.signed(params, System.currentTimeMillis(), clientSecret),
                        false);
                request.content(new StringContentProvider("application/json", body, StandardCharsets.UTF_8));
            } else {
                request.header("content-type", "application/json");
            }
            ContentResponse response = request.send();
            int status = response.getStatus();
            String text = response.getContentAsString();
            if (status == 200) {
                try {
                    return gson.fromJson(text, JsonObject.class);
                } catch (com.google.gson.JsonSyntaxException e) {
                    logger.debug("Cloud call {}: invalid response: {}", path,
                            text.length() > 200 ? text.substring(0, 200) : text);
                    return null;
                }
            }
            if ((status == 429 || status == 503) && attempt < 2) {
                logger.debug("Cloud call {}: HTTP {}, retry {}", path, status, attempt + 1);
                try {
                    Thread.sleep(500L << attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
                return call(path, params, retried, attempt + 1);
            }
            if (status == 401 && !retried) {
                synchronized (this) {
                    accessToken = null;
                }
                return call(path, params, true, attempt);
            }
            logger.debug("Cloud call {} failed: HTTP {} - {}", path, status, text);
            return null;
        } catch (MovaCloudException e) {
            throw e;
        } catch (Exception e) {
            throw new MovaCloudException("Cloud call failed: " + e.getClass().getSimpleName() + ": "
                    + e.getMessage(), e);
        }
    }

    /** Liefert die mit dem Konto verknüpften Geräte und merkt sich deren bindDomain (Host-Anteil). */
    public synchronized List<DeviceRecord> getDevices() throws MovaCloudException {
        JsonObject resp = call(MovaBindingConstants.DEVICE_LIST_PATH, null, false);
        List<DeviceRecord> result = new ArrayList<>();
        if (resp == null || !resp.has("code") || resp.get("code").getAsInt() != 0 || !resp.has("data")
                || !resp.get("data").isJsonObject()) {
            logger.warn("Could not load the device list: {}", resp);
            return result;
        }
        JsonObject data = resp.getAsJsonObject("data");
        JsonObject page = data.has("page") && data.get("page").isJsonObject() ? data.getAsJsonObject("page") : null;
        JsonArray records = page != null && page.has("records") && page.get("records").isJsonArray()
                ? page.getAsJsonArray("records")
                : new JsonArray();
        for (JsonElement e : records) {
            DeviceRecord rec = gson.fromJson(e, DeviceRecord.class);
            if (rec == null) {
                continue;
            }
            String recDid = rec.did;
            String recDomain = rec.bindDomain;
            if (recDid != null) {
                result.add(rec);
                if (recDomain != null) {
                    bindDomainByDid.put(recDid, recDomain);
                }
                String recUid = rec.masterUid;
                if (recUid != null) {
                    uidByDid.put(recDid, recUid);
                }
                String recModel = rec.model;
                if (recModel != null) {
                    modelByDid.put(recDid, recModel);
                }
            }
        }
        return result;
    }


    /** Benutzer-ID des Geräte-Besitzers; lädt bei Bedarf die Geräteliste. */
    public @Nullable String getUid(String did) throws MovaCloudException {
        String uid = uidByDid.get(did);
        if (uid == null) {
            getDevices();
            uid = uidByDid.get(did);
        }
        return uid;
    }

    /**
     * Liest Ereignis-Verlaufseinträge (z. B. Reinigungsverlauf aus den Status-Ereignissen 4.1) aus der Cloud.
     *
     * @return Einträge (neueste zuerst, wie von der Cloud geliefert) oder null bei Fehler
     */
    public @Nullable List<JsonObject> getEventHistory(String did, int siid, int eiid, long fromEpochSeconds, int limit)
            throws MovaCloudException {
        String uid = getUid(did);
        if (uid == null) {
            throw new MovaCloudException("User ID of device " + did + " unknown", null);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("uid", uid);
        params.put("did", did);
        params.put("from", fromEpochSeconds);
        params.put("limit", limit);
        params.put("siid", String.valueOf(siid));
        params.put("region", country);
        params.put("type", 3);
        params.put("eiid", String.valueOf(eiid));
        JsonObject resp = call("dreame-user-iot/iotstatus/history", params, false);
        if (resp == null || !resp.has("data") || !resp.get("data").isJsonObject()) {
            logger.debug("History: no data in the response: {}", resp);
            return null;
        }
        JsonObject data = resp.getAsJsonObject("data");
        List<JsonObject> out = new ArrayList<>();
        if (data.has("list") && data.get("list").isJsonArray()) {
            for (JsonElement e : data.getAsJsonArray("list")) {
                if (e.isJsonObject()) {
                    out.add(e.getAsJsonObject());
                }
            }
        }
        return out;
    }

    /** Modellname des Geräts (z. B. "mova.vacuum.r2582a"); lädt bei Bedarf die Geräteliste. */
    public @Nullable String getModel(String did) throws MovaCloudException {
        String model = modelByDid.get(did);
        if (model == null) {
            getDevices();
            model = modelByDid.get(did);
        }
        return model;
    }

    /**
     * Liefert den Objektnamen der aktuellen Karte (Property 6.3), ggf. mit angehängtem Schlüssel
     * ("pfad,schlüssel").
     */
    public @Nullable String getObjectName(String did) throws MovaCloudException {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("did", did);
        params.put("keys", "6.3");
        JsonObject resp = call("dreame-user-iot/iotstatus/props", params, false);
        if (resp == null || !resp.has("data")) {
            logger.debug("Object name: no data in the response: {}", resp);
            return null;
        }
        JsonElement data = resp.get("data");
        JsonElement value = null;
        if (data.isJsonArray() && data.getAsJsonArray().size() > 0 && data.getAsJsonArray().get(0).isJsonObject()) {
            value = data.getAsJsonArray().get(0).getAsJsonObject().get("value");
        } else if (data.isJsonObject()) {
            value = data.getAsJsonObject().get("value");
        }
        if (value == null || value.isJsonNull()) {
            logger.debug("Object name: no value in the response: {}", data);
            return null;
        }
        return firstString(value);
    }

    private @Nullable String firstString(JsonElement value) {
        if (value.isJsonArray()) {
            return value.getAsJsonArray().size() > 0 ? firstString(value.getAsJsonArray().get(0)) : null;
        }
        if (!value.isJsonPrimitive()) {
            return null;
        }
        String text = value.getAsString().trim();
        if (text.startsWith("[")) {
            try {
                return firstString(JsonParser.parseString(text));
            } catch (RuntimeException e) {
                return null;
            }
        }
        return text.isEmpty() ? null : text;
    }

    /** Fordert eine zeitlich begrenzte Download-Adresse für eine Datei (Karte) an. */
    public @Nullable String getDownloadUrl(String did, String objectName) throws MovaCloudException {
        String model = getModel(did);
        if (model == null) {
            throw new MovaCloudException("Model of device " + did + " unknown", null);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("did", did);
        params.put("model", model);
        params.put("filename", objectName);
        params.put("region", country);
        JsonObject resp = call("dreame-user-iot/iotfile/getDownloadUrl", params, false);
        if (resp == null || !resp.has("data") || !resp.get("data").isJsonPrimitive()) {
            logger.debug("No download URL received: {}", resp);
            return null;
        }
        return resp.get("data").getAsString();
    }

    /** Lädt eine Datei von einer Download-Adresse als Text. */
    public @Nullable String downloadText(String url) throws MovaCloudException {
        try {
            Request request = httpClient.newRequest(url).timeout(15, TimeUnit.SECONDS);
            request.header("user-agent", "okhttp/4.12.0");
            request.header("accept-encoding", "gzip");
            ContentResponse response = request.send();
            if (response.getStatus() == 200) {
                return response.getContentAsString();
            }
            logger.debug("Download failed: HTTP {}", response.getStatus());
            return null;
        } catch (Exception e) {
            throw new MovaCloudException("Download failed: " + e.getClass().getSimpleName() + ": "
                    + e.getMessage(), e);
        }
    }

    private String commandPath(String did) throws MovaCloudException {
        String domain = bindDomainByDid.get(did);
        if (domain == null) {
            getDevices();
            domain = bindDomainByDid.get(did);
        }
        if (domain == null) {
            throw new MovaCloudException("Device " + did + " not found in the device list of the account", null);
        }
        String prefix = domain.split("\\.")[0];
        return MovaBindingConstants.SEND_COMMAND_SERVICE + "-" + prefix + "/" + MovaBindingConstants.SEND_COMMAND_PATH;
    }

    /**
     * Sendet eine MIoT-Methode ("get_properties" / "action") an das Gerät.
     *
     * @return das "result"-Element der Antwort oder null
     */
    private @Nullable JsonElement sendCommand(String did, String method, Object params, boolean retried)
            throws MovaCloudException {
        String path = commandPath(did);
        int id;
        synchronized (this) {
            id = ++requestId;
        }
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("did", did);
        inner.put("id", id);
        inner.put("method", method);
        inner.put("params", params);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("did", did);
        body.put("id", id);
        body.put("data", inner);

        JsonObject resp = call(path, body, false);
        if (resp != null && resp.has("data") && resp.get("data").isJsonObject()
                && resp.getAsJsonObject("data").has("result")) {
            return resp.getAsJsonObject("data").get("result");
        }
        if (resp != null && resp.has("success") && resp.get("success").getAsBoolean() && !retried) {
            return sendCommand(did, method, params, true);
        }
        logger.debug("sendCommand {} without result: {}", method, resp);
        return null;
    }

    /**
     * Fragt MIoT-Properties ab.
     *
     * @return Liste der Einzelergebnisse (je {did, siid, piid, code, value}) oder null
     */
    public @Nullable List<JsonObject> getProperties(String did, int[][] siidPiidPairs) throws MovaCloudException {
        List<Object> params = new ArrayList<>();
        for (int[] p : siidPiidPairs) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("did", String.valueOf(p[0] * 100 + p[1]));
            entry.put("siid", p[0]);
            entry.put("piid", p[1]);
            params.add(entry);
        }
        JsonElement result = sendCommand(did, "get_properties", params, false);
        if (result == null || !result.isJsonArray()) {
            return null;
        }
        List<JsonObject> list = new ArrayList<>();
        for (JsonElement e : result.getAsJsonArray()) {
            if (e.isJsonObject()) {
                list.add(e.getAsJsonObject());
            }
        }
        return list;
    }

    /** Führt eine MIoT-Action aus (Start/Pause/Stopp/Andocken/Suchen). */
    public boolean sendAction(String did, int siid, int aiid) throws MovaCloudException {
        return sendAction(did, siid, aiid, new ArrayList<>());
    }

    /**
     * Führt eine MIoT-Action mit Eingabeparametern aus.
     *
     * @param in Liste von {piid, value}-Einträgen (Map mit "piid" und "value")
     */
    public boolean sendAction(String did, int siid, int aiid, List<Object> in) throws MovaCloudException {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("did", did);
        params.put("siid", siid);
        params.put("aiid", aiid);
        params.put("in", in);
        JsonElement result = sendCommand(did, "action", params, false);
        if (result == null) {
            return false;
        }
        if (result.isJsonObject() && result.getAsJsonObject().has("code")) {
            int code = result.getAsJsonObject().get("code").getAsInt();
            if (code != 0) {
                logger.debug("Action {}.{} rejected: {}", siid, aiid, result);
            }
            return code == 0;
        }
        return true;
    }

    /** Schreibt eine MIoT-Property (set_properties). Wert: Integer, Boolean oder String. */
    public boolean setProperty(String did, int siid, int piid, Object value) throws MovaCloudException {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("did", did);
        entry.put("siid", siid);
        entry.put("piid", piid);
        entry.put("value", value);
        List<Object> params = new ArrayList<>();
        params.add(entry);
        JsonElement result = sendCommand(did, "set_properties", params, false);
        if (result == null) {
            return false;
        }
        if (result.isJsonArray()) {
            for (JsonElement e : result.getAsJsonArray()) {
                if (e.isJsonObject() && e.getAsJsonObject().has("code")
                        && e.getAsJsonObject().get("code").getAsInt() != 0) {
                    logger.debug("Property {}.{} not set: {}", siid, piid, result);
                    return false;
                }
            }
        }
        return true;
    }
}
