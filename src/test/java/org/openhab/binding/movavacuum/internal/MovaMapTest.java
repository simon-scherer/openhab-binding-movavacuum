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

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.Deflater;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MovaMap} with synthetic maps (header, pixels, JSON; zlib; Base64; optional AES).
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public class MovaMapTest {

    private static final int W = 20;
    private static final int H = 10;

    /** Room 1 = left half, room 2 = right half; room 2 has a hole (wall pixels) in the middle. */
    private static byte[] pixels() {
        byte[] px = new byte[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                px[y * W + x] = (byte) (x < W / 2 ? 1 : 2);
            }
        }
        for (int y = 3; y <= 6; y++) {
            for (int x = 13; x <= 16; x++) {
                px[y * W + x] = (byte) 0x80;
            }
        }
        return px;
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] rawMap(String json, boolean withPixels) {
        int w = withPixels ? W : 0;
        int h = withPixels ? H : 0;
        byte[] header = new byte[27];
        header[0] = 7; // map id
        header[17] = 50; // grid size (mm)
        header[19] = (byte) w;
        header[21] = (byte) h;
        short left = -500;
        short top = -250;
        header[23] = (byte) left;
        header[24] = (byte) (left >> 8);
        header[25] = (byte) top;
        header[26] = (byte) (top >> 8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(header);
        if (withPixels) {
            out.writeBytes(pixels());
        }
        out.writeBytes(json.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static byte[] deflate(byte[] in) {
        Deflater d = new Deflater();
        d.setInput(in);
        d.finish();
        byte[] buf = new byte[in.length + 64];
        int n = d.deflate(buf);
        d.end();
        byte[] r = new byte[n];
        System.arraycopy(buf, 0, r, 0, n);
        return r;
    }

    private static String encode(byte[] raw) {
        return Base64.getEncoder().encodeToString(deflate(raw)).replace('/', '_').replace('+', '-');
    }

    private static String roomsJson() {
        return "{\"seg_inf\":{\"1\":{\"name\":\"" + b64("Küche") + "\"},\"2\":{\"type\":1}},"
                + "\"robot\":[100,200],\"charger\":[-100,-200],\"delsr\":[]}";
    }

    @Test
    public void decodesRoomsAndHeader() throws Exception {
        MovaI18n.setLocaleProvider(() -> Locale.ENGLISH);
        MovaMap.Result r = MovaMap.decode(encode(rawMap(roomsJson(), true)), null, null);
        assertEquals(7, r.mapId);
        assertEquals(50, r.grid);
        assertEquals(-500, r.left);
        assertEquals(-250, r.top);
        assertEquals(2, r.rooms.size());
        assertEquals("Küche", r.rooms.get(0).name);
        assertEquals("Living Room", r.rooms.get(1).name);
        assertArrayEquals(new int[] { 100, 200 }, r.robot);
    }

    @Test
    public void decodesEncryptedMap() throws Exception {
        String iv = "NRwnBj5FsNPgBNbT";
        String key = "testkey";
        byte[] data = deflate(rawMap(roomsJson(), true));
        byte[] padded = new byte[(data.length + 15) / 16 * 16];
        System.arraycopy(data, 0, padded, 0, data.length);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        Cipher c = Cipher.getInstance("AES/CBC/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(hex.substring(0, 32).getBytes(StandardCharsets.US_ASCII), "AES"),
                new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8)));
        String raw = Base64.getEncoder().encodeToString(c.doFinal(padded));
        MovaMap.Result r = MovaMap.decode(raw + "," + key, null, iv);
        assertEquals(2, r.rooms.size());
    }

    @Test
    public void encryptedMapWithoutIvFails() {
        assertThrows(MovaCloudException.class, () -> MovaMap.decode(encode(rawMap(roomsJson(), true)) + ",key", null, null));
    }

    @Test
    public void liveMapTakesRoomDataFromSavedMap() throws Exception {
        String saved = encode(rawMap(roomsJson(), true));
        String live = "{\"robot\":[300,400],\"rism\":\"" + saved + "\"}";
        MovaMap.Result r = MovaMap.decode(encode(rawMap(live, true)), null, null);
        assertEquals(2, r.rooms.size());
        assertArrayEquals(new int[] { 300, 400 }, r.robot);
        assertNotNull(r.json.get("seg_inf"), "seg_inf of the saved map must be available (room edit)");
    }

    @Test
    public void roomCentreAvoidsHoles() throws Exception {
        MovaMap.Result r = MovaMap.decode(encode(rawMap(roomsJson(), true)), null, null);
        // room 2 spans x 10..19; the centre of its bounding box (14,4) lies in the hole
        assertFalse(MovaVacuumHandler.isRoomPixel(r, 14, 4, 2));
        int[] c = MovaVacuumHandler.roomCenterPixel(r, 2, 10, 0, 19, 9);
        assertTrue(MovaVacuumHandler.isRoomPixel(r, c[0], c[1], 2), "centre must be on floor of room 2");
    }
}
