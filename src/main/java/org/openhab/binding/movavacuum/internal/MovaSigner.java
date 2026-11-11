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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Signatur- und Header-Logik der DreameHome/Mova-Cloud.
 *
 * Reine JDK-Klasse (keine externen Bibliotheken), 1:1 nach den Funktionen
 * _spliced, _signed, _base_headers (dreame-rlc / dreame-meta) aus
 * Tasshack/dreame-vacuum v2.0.1 (protocol.py). Die Klasse ist isoliert gegen
 * die Python-Referenz getestet (siehe Testvektoren in der Anleitung).
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public final class MovaSigner {

    private MovaSigner() {
    }

    public static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    /**
     * Entspricht Python json.dumps(value, separators=(",", ":"), ensure_ascii=False),
     * optional mit sort_keys=True. Unterstützt String, Integer/Long, Boolean, null, Map, List.
     */
    public static String toJson(@Nullable Object value, boolean sortKeys) {
        StringBuilder sb = new StringBuilder();
        writeJson(sb, value, sortKeys);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeJson(StringBuilder sb, @Nullable Object v, boolean sortKeys) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            writeString(sb, s);
        } else if (v instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte) {
            sb.append(v.toString());
        } else if (v instanceof Map<?, ?> m) {
            Map<String, Object> map = (Map<String, Object>) m;
            Map<String, Object> ordered = sortKeys ? new TreeMap<>(map) : map;
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ordered.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, e.getKey());
                sb.append(':');
                writeJson(sb, e.getValue(), sortKeys);
            }
            sb.append('}');
        } else if (v instanceof List<?> l) {
            sb.append('[');
            boolean first = true;
            for (Object o : l) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeJson(sb, o, sortKeys);
            }
            sb.append(']');
        } else {
            throw new IllegalArgumentException("Unsupported JSON type: " + v.getClass());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /** Entspricht DreameVacuumDreameHomeCloudProtocol._spliced(obj, top). */
    @SuppressWarnings("unchecked")
    public static String spliced(Map<String, Object> obj, boolean top) {
        List<String> parts = new ArrayList<>();
        for (String k : new TreeMap<>(obj).keySet()) {
            Object v = obj.get(k);
            if (v instanceof Map<?, ?> m) {
                String inner = spliced((Map<String, Object>) m, false);
                parts.add(inner.isEmpty() ? k + "=]" : k + "=[" + inner + "]");
            } else if (v instanceof List<?>) {
                if (top) {
                    parts.add(k + "=" + toJson(v, true));
                }
            } else if (v instanceof Boolean b) {
                parts.add(k + "=" + (b ? "true" : "false"));
            } else if (v == null) {
                parts.add(k + "=null");
            } else if (top) {
                parts.add(k + "=" + v);
            } else {
                parts.add(k + "=" + toJson(v, false));
            }
        }
        return String.join("&", parts);
    }

    /** Entspricht _signed(params): hängt "sign" und "timestamp" an (Einfügereihenfolge bleibt erhalten). */
    public static Map<String, Object> signed(Map<String, Object> params, long timestampMs, String clientSecret) {
        String base = spliced(params, true) + timestampMs + clientSecret;
        Map<String, Object> result = new LinkedHashMap<>(params);
        result.put("sign", md5Hex(base));
        result.put("timestamp", timestampMs);
        return result;
    }

    /** Wert für den Header "dreame-rlc": Base64(AES-ECB-PKCS7("region|lang|country")), Schlüssel = Client-Geheimnis. */
    public static String rlcHeader(String region, String lang, String country, String clientSecret)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "AES"));
        byte[] enc = cipher.doFinal((region + "|" + lang + "|" + country).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(enc);
    }

    /** Wert für den Header "dreame-meta". */
    public static String metaHeader(String versionId, boolean withHashes, String username, String visitorHash) {
        String meta = "cv=a_" + versionId;
        if (withHashes) {
            meta += ";canvasHash=" + md5Hex(username + "c").substring(0, 8) + ";webglHash="
                    + md5Hex(username + "w").substring(0, 8) + ";visitorIdHash=" + visitorHash;
        }
        return meta;
    }
}
