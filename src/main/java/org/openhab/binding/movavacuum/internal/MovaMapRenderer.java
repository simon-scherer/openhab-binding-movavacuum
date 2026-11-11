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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Renders the room map as PNG in the style of the Mova app: muted room colours (adjacent rooms get different
 * colours), outlined walls, dotted carpets, a faint grid, numbered bubbles with the cleaning order, room names,
 * obstacles, station and robot. A light and a dark theme are available; the background is transparent.
 * Pixel format of the map: bit 7 = wall, bit 6 = carpet, lower 6 bits = room id.
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public final class MovaMapRenderer {

    /** Colour set of a theme. */
    private record Theme(int[] rooms, int[] bubbles, int border, int unassigned, int hidden, int text, int grid,
            int robotRing) {
    }

    private static final int[] BUBBLES = { 0xE98A2E, 0xE8B723, 0x5B79E0, 0x7DB83A, 0x9B6FD6, 0x2FA5A0 };

    private static final Theme LIGHT = new Theme(
            new int[] { 0xF0C6B1, 0xF4E4BF, 0xC4CFEF, 0xCDE2BA, 0xDECDEB, 0xC0E2DE }, BUBBLES, 0x8FA3BD, 0xD4D4D4,
            0xE8E8E8, 0x4A3B30, 0x14000000, 0x606060);

    private static final Theme DARK = new Theme(
            new int[] { 0xB9866F, 0xD8C49A, 0x8796C2, 0x93AE7E, 0xB09ACB, 0x86B5B0 }, BUBBLES, 0xA9BBD3, 0x7A7A7A,
            0x555555, 0x3A2E25, 0x16FFFFFF, 0xDDDDDD);

    private MovaMapRenderer() {
    }

    /**
     * @param rotation 0, 90, 180 or 270 degrees clockwise
     * @param flipVertical flips the image top to bottom (before rotating)
     * @param showHidden false = rooms hidden in the app (incl. their walls) are not drawn
     * @param maxSize target edge length in pixels
     * @param labelMode "name" (bubble with cleaning order and room name), "number" (bubble with room id only) or
     *            "both" (bubble with room id and name)
     * @param dark dark theme instead of light theme
     * @param obstacles obstacle positions in map units (x, y), may be empty
     */
    public static byte[] render(MovaMap.Result r, int rotation, boolean flipVertical, boolean showHidden,
            int maxSize, String labelMode, boolean dark, List<int[]> obstacles) throws IOException {
        int w = r.imgWidth;
        int h = r.imgHeight;
        if (w <= 0 || h <= 0 || r.image.length < w * h) {
            throw new IOException("No map image available");
        }
        System.setProperty("java.awt.headless", "true");
        Theme theme = dark ? DARK : LIGHT;

        Set<Integer> visible = new HashSet<>();
        Map<Integer, MovaMap.Room> roomById = new HashMap<>();
        for (MovaMap.Room room : r.rooms) {
            visible.add(room.id);
            roomById.put(room.id, room);
        }
        int[] pix = new int[w * h]; // -1 = nothing, 0 = unassigned floor, 1..63 = room, 64 = wall
        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;
        int[] rMinX = new int[64];
        int[] rMaxX = new int[64];
        int[] rMinY = new int[64];
        int[] rMaxY = new int[64];
        java.util.Arrays.fill(rMinX, Integer.MAX_VALUE);
        java.util.Arrays.fill(rMinY, Integer.MAX_VALUE);
        java.util.Arrays.fill(rMaxX, -1);
        java.util.Arrays.fill(rMaxY, -1);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = r.image[y * w + x] & 0xff;
                int id = px & 0x3f;
                int v;
                if (px == 0 || (!showHidden && id > 0 && !visible.contains(id))) {
                    v = -1;
                } else if ((px & 0x80) != 0) {
                    v = 64;
                } else {
                    v = id;
                }
                pix[y * w + x] = v;
                if (v < 0) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                if (v > 0 && v < 64) {
                    rMinX[v] = Math.min(rMinX[v], x);
                    rMaxX[v] = Math.max(rMaxX[v], x);
                    rMinY[v] = Math.min(rMinY[v], y);
                    rMaxY[v] = Math.max(rMaxY[v], y);
                }
            }
        }
        int[] colorIndex = colorRooms(pix, w, h, theme.rooms.length);

        int scale = Math.max(2, Math.min(8, Math.round(Math.max(200, maxSize) / (float) Math.max(w, h))));
        BufferedImage img = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_ARGB);
        int line = Math.max(1, scale / 3);
        int gridStep = r.grid > 0 ? Math.max(4, Math.round(500f / r.grid)) : 10; // every 0.5 m
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = pix[y * w + x];
                if (v < 0) {
                    continue;
                }
                int rgb;
                if (v == 64) {
                    rgb = theme.border;
                } else if (v == 0) {
                    rgb = theme.unassigned;
                } else if (!visible.contains(v)) {
                    rgb = theme.hidden;
                } else {
                    rgb = theme.rooms[colorIndex[v]];
                }
                fill(img, x * scale, y * scale, scale, scale, 0xff000000 | rgb);
                if (v == 64) {
                    continue;
                }
                boolean carpet = (r.image[y * w + x] & 0x40) != 0;
                if (carpet && scale >= 2 && ((x + y) & 1) == 0) {
                    int d = Math.max(1, scale / 3);
                    fill(img, x * scale + (scale - d) / 2, y * scale + (scale - d) / 2, d, d,
                            0xff000000 | shade(rgb, 78));
                }
                if (x % gridStep == 0) {
                    blend(img, x * scale, y * scale, 1, scale, theme.grid);
                }
                if (y % gridStep == 0) {
                    blend(img, x * scale, y * scale, scale, 1, theme.grid);
                }
                // outline towards empty space or another room
                int b = 0xff000000 | theme.border;
                if (edge(pix, w, h, x - 1, y, v)) {
                    fill(img, x * scale, y * scale, line, scale, b);
                }
                if (edge(pix, w, h, x + 1, y, v)) {
                    fill(img, (x + 1) * scale - line, y * scale, line, scale, b);
                }
                if (edge(pix, w, h, x, y - 1, v)) {
                    fill(img, x * scale, y * scale, scale, line, b);
                }
                if (edge(pix, w, h, x, y + 1, v)) {
                    fill(img, x * scale, (y + 1) * scale - line, scale, line, b);
                }
            }
        }

        // crop to the drawn area (with a margin for the markers)
        BufferedImage out = img;
        int offX = 0;
        int offY = 0;
        if (maxX >= minX && maxY >= minY) {
            int m = 10 * scale;
            BufferedImage padded = new BufferedImage((maxX - minX + 1) * scale + 2 * m,
                    (maxY - minY + 1) * scale + 2 * m, BufferedImage.TYPE_INT_ARGB);
            Graphics2D pg = padded.createGraphics();
            pg.drawImage(img.getSubimage(minX * scale, minY * scale, (maxX - minX + 1) * scale,
                    (maxY - minY + 1) * scale), m, m, null);
            pg.dispose();
            out = padded;
            offX = minX * scale - m;
            offY = minY * scale - m;
        }
        int deg = ((rotation % 360) + 360) % 360;
        Transform t = new Transform(scale, offX, offY, out.getWidth(), out.getHeight(), flipVertical, deg);
        if (flipVertical) {
            out = flip(out);
        }
        out = rotate(out, deg);

        // overlays are drawn after flipping/rotating so that symbols and texts stay upright
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            for (int[] o : obstacles) {
                double[] p = t.map(r, o[0], o[1]);
                obstacle(g, p[0], p[1], scale);
            }
            try {
                labels(g, r, t, theme, labelMode, colorIndex, roomById, rMinX, rMinY, rMaxX, rMaxY, scale, deg);
            } catch (Throwable e) { // NOPMD - fonts may be missing on headless systems
                // continue without labels
            }
            int[] ch = r.charger;
            if (ch != null && ch.length >= 2) {
                double[] p = t.map(r, ch[0], ch[1]);
                station(g, p[0], p[1], scale);
            }
            int[] rb = r.robot;
            // like the app: while the robot is docked only the station is shown
            boolean docked = rb != null && ch != null && rb.length >= 2 && ch.length >= 2
                    && Math.hypot(rb[0] - ch[0], rb[1] - ch[1]) < 300;
            if (rb != null && rb.length >= 2 && !docked) {
                double[] p = t.map(r, rb[0], rb[1]);
                robot(g, p[0], p[1], scale, theme);
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (!javax.imageio.ImageIO.write(out, "png", bos)) {
            throw new IOException("PNG writer not available");
        }
        return bos.toByteArray();
    }

    /** Converts map coordinates / image pixels into coordinates of the final (cropped, flipped, rotated) image. */
    private static final class Transform {
        final int scale;
        final int offX;
        final int offY;
        final int cw;
        final int ch;
        final boolean flip;
        final int deg;

        Transform(int scale, int offX, int offY, int cw, int ch, boolean flip, int deg) {
            this.scale = scale;
            this.offX = offX;
            this.offY = offY;
            this.cw = cw;
            this.ch = ch;
            this.flip = flip;
            this.deg = deg;
        }

        /** Pixel (centre) of the map image. */
        double[] pixel(double px, double py) {
            double x = (px + 0.5) * scale - offX;
            double y = (py + 0.5) * scale - offY;
            if (flip) {
                y = ch - y;
            }
            return switch (deg) {
                case 90 -> new double[] { ch - y, x };
                case 180 -> new double[] { cw - x, ch - y };
                case 270 -> new double[] { y, cw - x };
                default -> new double[] { x, y };
            };
        }

        /** Position in map units (mm). */
        double[] map(MovaMap.Result r, int mx, int my) {
            double g = r.grid > 0 ? r.grid : 50;
            return pixel((mx - r.left) / g - 0.5, (my - r.top) / g - 0.5);
        }
    }

    private static void labels(Graphics2D g, MovaMap.Result r, Transform t, Theme theme, String labelMode,
            int[] colorIndex, Map<Integer, MovaMap.Room> roomById, int[] rMinX, int[] rMinY, int[] rMaxX,
            int[] rMaxY, int scale, int deg) {
        boolean numberOnly = "number".equalsIgnoreCase(labelMode);
        boolean both = "both".equalsIgnoreCase(labelMode);
        boolean quarter = deg == 90 || deg == 270;
        int bubbleR = Math.round(6.5f * scale);
        int gap = scale;
        Font bubbleFont = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(8f * scale));
        float baseSize = 7.5f * scale;
        float minSize = 5.5f * scale;
        for (MovaMap.Room room : r.rooms) {
            int id = room.id;
            if (id <= 0 || id >= 64 || rMaxX[id] < 0) {
                continue;
            }
            String bubbleText = !numberOnly && !both && room.order != null ? String.valueOf(room.order)
                    : String.valueOf(id);
            String name = numberOnly ? "" : room.name;

            // find font size and position so that bubble and name fit completely into the room
            int[] spot = null;
            float size = baseSize;
            int textW = 0;
            int textH = 0;
            for (float s = baseSize; s >= minSize - 0.01f; s -= 0.5f * scale) {
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(s)));
                FontMetrics fm = g.getFontMetrics();
                textW = name.isEmpty() ? 0 : fm.stringWidth(name);
                textH = name.isEmpty() ? 0 : fm.getAscent() + fm.getDescent();
                int blockW = Math.max(textW, 2 * bubbleR) + 2 * scale;
                int blockH = 2 * bubbleR + (name.isEmpty() ? 0 : gap + textH) + 2 * scale;
                int hw = (int) Math.ceil((quarter ? blockH : blockW) / 2.0 / scale);
                int hh = (int) Math.ceil((quarter ? blockW : blockH) / 2.0 / scale);
                spot = fitBlock(r, id, rMinX[id], rMinY[id], rMaxX[id], rMaxY[id], hw, hh);
                size = s;
                if (spot != null) {
                    break;
                }
            }
            boolean overflow = spot == null;
            if (spot == null && !name.isEmpty()) {
                // name too wide: at least keep the block vertically inside the room (name may stick out sideways)
                int blockW = 2 * bubbleR + 2 * scale;
                int blockH = 2 * bubbleR + gap + textH + 2 * scale;
                int hw = (int) Math.ceil((quarter ? blockH : blockW) / 2.0 / scale);
                int hh = (int) Math.ceil((quarter ? blockW : blockH) / 2.0 / scale);
                spot = fitBlock(r, id, rMinX[id], rMinY[id], rMaxX[id], rMaxY[id], hw, hh);
            }
            if (spot == null) {
                spot = MovaVacuumHandler.roomCenterPixel(r, id, rMinX[id], rMinY[id], rMaxX[id], rMaxY[id]);
            }
            Font nameFont = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size));
            g.setFont(nameFont);
            FontMetrics fm = g.getFontMetrics();
            textW = name.isEmpty() ? 0 : fm.stringWidth(name);
            textH = name.isEmpty() ? 0 : fm.getAscent() + fm.getDescent();
            double[] p = t.pixel(spot[0], spot[1]);
            int cx = (int) Math.round(p[0]);
            int cy = (int) Math.round(p[1]);
            int blockH = 2 * bubbleR + (name.isEmpty() ? 0 : gap + textH);
            int top = cy - blockH / 2;
            int bubbleY = top + bubbleR;

            Color bubble = new Color(theme.bubbles[colorIndex[id] % theme.bubbles.length]);
            g.setColor(new Color(255, 255, 255, 230));
            g.fillOval(cx - bubbleR - scale, bubbleY - bubbleR - scale, 2 * (bubbleR + scale),
                    2 * (bubbleR + scale));
            g.setColor(bubble);
            g.fillOval(cx - bubbleR, bubbleY - bubbleR, 2 * bubbleR, 2 * bubbleR);
            g.setFont(bubbleFont);
            FontMetrics bm = g.getFontMetrics();
            g.setColor(Color.WHITE);
            g.drawString(bubbleText, cx - bm.stringWidth(bubbleText) / 2,
                    bubbleY + (bm.getAscent() - bm.getDescent()) / 2);
            if (name.isEmpty()) {
                continue;
            }
            g.setFont(nameFont);
            int ty = top + 2 * bubbleR + gap + fm.getAscent();
            if (overflow) {
                // the name does not fit into the room: put it on a small plate in the room colour
                int padX = 2 * scale;
                int padY = scale / 2;
                int plateX = cx - textW / 2 - padX;
                int plateY = ty - fm.getAscent() - padY;
                Color rc = new Color(theme.rooms[colorIndex[id]]);
                g.setColor(new Color(rc.getRed(), rc.getGreen(), rc.getBlue(), 235));
                g.fillRoundRect(plateX, plateY, textW + 2 * padX, textH + 2 * padY, textH / 2, textH / 2);
                g.setColor(new Color(theme.border));
                g.setStroke(new BasicStroke(Math.max(1f, scale / 3f)));
                g.drawRoundRect(plateX, plateY, textW + 2 * padX, textH + 2 * padY, textH / 2, textH / 2);
            }
            g.setColor(new Color(theme.text));
            g.drawString(name, cx - textW / 2, ty);
        }
    }

    /**
     * Searches the room pixel closest to the room's centroid around which a block of (2*hw+1) x (2*hh+1) pixels
     * lies completely inside the room. Returns null if there is no such place.
     */
    static int @org.eclipse.jdt.annotation.Nullable [] fitBlock(MovaMap.Result r, int id, int minX, int minY,
            int maxX, int maxY, int hw, int hh) {
        long sx = 0;
        long sy = 0;
        long n = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (MovaVacuumHandler.isRoomPixel(r, x, y, id)) {
                    sx += x;
                    sy += y;
                    n++;
                }
            }
        }
        if (n == 0 || 2 * hw + 1 > maxX - minX + 1 || 2 * hh + 1 > maxY - minY + 1) {
            return null;
        }
        double cx = (double) sx / n;
        double cy = (double) sy / n;
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int y = minY + hh; y <= maxY - hh; y++) {
            for (int x = minX + hw; x <= maxX - hw; x++) {
                double d = (x - cx) * (x - cx) + (y - cy) * (y - cy);
                if (d >= bestD || !MovaVacuumHandler.isRoomPixel(r, x, y, id)) {
                    continue;
                }
                boolean ok = true;
                for (int yy = y - hh; yy <= y + hh && ok; yy++) {
                    for (int xx = x - hw; xx <= x + hw; xx++) {
                        if (!MovaVacuumHandler.isRoomPixel(r, xx, yy, id)) {
                            ok = false;
                            break;
                        }
                    }
                }
                if (ok) {
                    bestD = d;
                    best = new int[] { x, y };
                }
            }
        }
        return best;
    }

    private static void station(Graphics2D g, double x, double y, int scale) {
        int s = 7 * scale;
        int ix = (int) Math.round(x - s);
        int iy = (int) Math.round(y - s);
        g.setColor(Color.WHITE);
        g.fillRoundRect(ix - scale, iy - scale, 2 * s + 2 * scale, 2 * s + 2 * scale, 4 * scale, 4 * scale);
        g.setColor(new Color(0x3FA34D));
        g.fillRoundRect(ix, iy, 2 * s, 2 * s, 3 * scale, 3 * scale);
        // lightning bolt
        double cx = x;
        double cy = y;
        double u = s / 6.0;
        Polygon bolt = new Polygon();
        bolt.addPoint((int) Math.round(cx + 0.8 * u), (int) Math.round(cy - 4.2 * u));
        bolt.addPoint((int) Math.round(cx - 2.4 * u), (int) Math.round(cy + 0.6 * u));
        bolt.addPoint((int) Math.round(cx - 0.2 * u), (int) Math.round(cy + 0.6 * u));
        bolt.addPoint((int) Math.round(cx - 0.8 * u), (int) Math.round(cy + 4.2 * u));
        bolt.addPoint((int) Math.round(cx + 2.4 * u), (int) Math.round(cy - 0.6 * u));
        bolt.addPoint((int) Math.round(cx + 0.2 * u), (int) Math.round(cy - 0.6 * u));
        g.setColor(Color.WHITE);
        g.fillPolygon(bolt);
    }

    private static void robot(Graphics2D g, double x, double y, int scale, Theme theme) {
        int rad = 7 * scale;
        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(ix - rad - scale / 2, iy - rad + scale / 2, 2 * rad + scale, 2 * rad + scale);
        g.setColor(Color.WHITE);
        g.fillOval(ix - rad, iy - rad, 2 * rad, 2 * rad);
        g.setColor(new Color(theme.robotRing));
        g.setStroke(new BasicStroke(Math.max(1f, scale / 2f)));
        g.drawOval(ix - rad, iy - rad, 2 * rad, 2 * rad);
        int inner = 3 * scale;
        g.setColor(new Color(0x2F6FDB));
        g.fillOval(ix - inner, iy - inner, 2 * inner, 2 * inner);
    }

    private static void obstacle(Graphics2D g, double x, double y, int scale) {
        int rad = Math.round(2.5f * scale);
        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);
        g.setColor(Color.WHITE);
        g.fillOval(ix - rad - scale / 2, iy - rad - scale / 2, 2 * rad + scale, 2 * rad + scale);
        g.setColor(new Color(0xE0592A));
        g.fillOval(ix - rad, iy - rad, 2 * rad, 2 * rad);
    }

    /** True if the neighbour pixel is empty or belongs to another room (but not a wall). */
    private static boolean edge(int[] pix, int w, int h, int x, int y, int v) {
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return true;
        }
        int n = pix[y * w + x];
        return n != 64 && n != v;
    }

    /**
     * Greedy colouring so that neighbouring rooms (touching or separated only by a thin wall) get different
     * colours. Returns the palette index per room id.
     */
    static int[] colorRooms(int[] pix, int w, int h, int paletteSize) {
        List<Set<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            adj.add(new HashSet<>());
        }
        int reach = 3;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = pix[y * w + x];
                if (v <= 0 || v >= 64) {
                    continue;
                }
                for (int d = 1; d <= reach; d++) {
                    if (x + d < w) {
                        link(adj, v, pix[y * w + x + d]);
                    }
                    if (y + d < h) {
                        link(adj, v, pix[(y + d) * w + x]);
                    }
                }
            }
        }
        int[] color = new int[64];
        for (int id = 1; id < 64; id++) {
            boolean[] used = new boolean[paletteSize];
            for (int n : adj.get(id)) {
                if (n < id) {
                    used[color[n]] = true;
                }
            }
            int c = (id - 1) % paletteSize;
            for (int k = 0; k < paletteSize; k++) {
                int cand = (id - 1 + k) % paletteSize;
                if (!used[cand]) {
                    c = cand;
                    break;
                }
            }
            color[id] = c;
        }
        return color;
    }

    private static void link(List<Set<Integer>> adj, int a, int b) {
        if (b > 0 && b < 64 && b != a) {
            adj.get(a).add(b);
            adj.get(b).add(a);
        }
    }

    private static void fill(BufferedImage img, int x, int y, int fw, int fh, int argb) {
        for (int yy = y; yy < y + fh; yy++) {
            for (int xx = x; xx < x + fw; xx++) {
                img.setRGB(xx, yy, argb);
            }
        }
    }

    /** Blends a colour with alpha (ARGB) over the image. */
    private static void blend(BufferedImage img, int x, int y, int fw, int fh, int argb) {
        int a = (argb >>> 24) & 0xff;
        for (int yy = y; yy < y + fh; yy++) {
            for (int xx = x; xx < x + fw; xx++) {
                int base = img.getRGB(xx, yy);
                int rr = (((argb >> 16) & 0xff) * a + ((base >> 16) & 0xff) * (255 - a)) / 255;
                int gg = (((argb >> 8) & 0xff) * a + ((base >> 8) & 0xff) * (255 - a)) / 255;
                int bb = ((argb & 0xff) * a + (base & 0xff) * (255 - a)) / 255;
                img.setRGB(xx, yy, (base & 0xff000000) | (rr << 16) | (gg << 8) | bb);
            }
        }
    }

    /** Darkens a colour to the given percentage (100 = unchanged). */
    private static int shade(int rgb, int percent) {
        int rr = ((rgb >> 16) & 0xff) * percent / 100;
        int gg = ((rgb >> 8) & 0xff) * percent / 100;
        int bb = (rgb & 0xff) * percent / 100;
        return (rr << 16) | (gg << 8) | bb;
    }

    private static BufferedImage flip(BufferedImage in) {
        BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < in.getHeight(); y++) {
            for (int x = 0; x < in.getWidth(); x++) {
                out.setRGB(x, in.getHeight() - 1 - y, in.getRGB(x, y));
            }
        }
        return out;
    }

    private static BufferedImage rotate(BufferedImage in, int degrees) {
        if (degrees == 0) {
            return in;
        }
        int w = in.getWidth();
        int h = in.getHeight();
        boolean quarter = degrees == 90 || degrees == 270;
        BufferedImage out = new BufferedImage(quarter ? h : w, quarter ? w : h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = in.getRGB(x, y);
                switch (degrees) {
                    case 90 -> out.setRGB(h - 1 - y, x, rgb);
                    case 180 -> out.setRGB(w - 1 - x, h - 1 - y, rgb);
                    default -> out.setRGB(y, w - 1 - x, rgb);
                }
            }
        }
        return out;
    }
}
