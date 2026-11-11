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
import java.util.Base64;
import java.util.zip.Deflater;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Builds small synthetic maps for the tests (27 byte header, pixels, JSON; zlib; Base64).
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
final class TestMaps {

    static final int W = 40;
    static final int H = 20;
    static final int GRID = 50;
    static final int LEFT = -1000;
    static final int TOP = -500;

    private TestMaps() {
    }

    /** Room 1 = x 0..19, room 2 = x 20..39 with a hole (wall) at x 26..33 / y 6..13; all rows. */
    static byte[] pixels() {
        byte[] px = new byte[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                px[y * W + x] = (byte) (x < W / 2 ? 1 : 2);
            }
        }
        for (int y = 6; y <= 13; y++) {
            for (int x = 26; x <= 33; x++) {
                px[y * W + x] = (byte) 0x80;
            }
        }
        // carpet in room 1
        for (int y = 2; y < 6; y++) {
            for (int x = 2; x < 8; x++) {
                px[y * W + x] = (byte) (1 | 0x40);
            }
        }
        return px;
    }

    static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    static String roomsJson() {
        return "{\"seg_inf\":{\"1\":{\"name\":\"" + b64("Küche") + "\"},\"2\":{\"type\":1}},"
                + "\"robot\":[100,200],\"charger\":[-100,-200],\"delsr\":[],"
                + "\"ai_obstacle\":[[200,100,133,0.9]]}";
    }

    static byte[] raw(String json, boolean withPixels) {
        int w = withPixels ? W : 0;
        int h = withPixels ? H : 0;
        byte[] header = new byte[27];
        header[0] = 7;
        header[17] = GRID;
        header[19] = (byte) w;
        header[21] = (byte) h;
        header[23] = (byte) LEFT;
        header[24] = (byte) (LEFT >> 8);
        header[25] = (byte) TOP;
        header[26] = (byte) (TOP >> 8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(header);
        if (withPixels) {
            out.writeBytes(pixels());
        }
        out.writeBytes(json.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    static byte[] deflate(byte[] in) {
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

    static String encode(byte[] raw) {
        return Base64.getEncoder().encodeToString(deflate(raw)).replace('/', '_').replace('+', '-');
    }

    static MovaMap.Result map() throws MovaCloudException {
        return MovaMap.decode(encode(raw(roomsJson(), true)), null, null);
    }
}
