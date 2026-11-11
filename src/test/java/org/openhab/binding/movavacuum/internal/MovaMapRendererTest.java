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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;

import javax.imageio.ImageIO;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MovaMapRenderer}.
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public class MovaMapRendererTest {

    private static BufferedImage png(byte[] data) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
        assertNotNull(img, "PNG must be readable");
        return img;
    }

    @Test
    public void rendersLightAndDarkInAllRotations() throws Exception {
        MovaMap.Result map = TestMaps.map();
        List<int[]> obstacles = List.of(new int[] { 200, 100 });
        int w0 = 0;
        int h0 = 0;
        for (boolean dark : new boolean[] { false, true }) {
            for (int rot : new int[] { 0, 90, 180, 270 }) {
                for (String labels : new String[] { "name", "number", "both" }) {
                    BufferedImage img = png(
                            MovaMapRenderer.render(map, rot, true, false, 400, labels, dark, obstacles));
                    if (rot == 0) {
                        w0 = img.getWidth();
                        h0 = img.getHeight();
                    } else if (rot == 90 || rot == 270) {
                        assertEquals(w0, img.getHeight());
                        assertEquals(h0, img.getWidth());
                    }
                }
            }
        }
    }

    @Test
    public void backgroundIsTransparent() throws Exception {
        BufferedImage img = png(MovaMapRenderer.render(TestMaps.map(), 0, false, false, 400, "name", false, List.of()));
        assertEquals(0, img.getRGB(0, 0) >>> 24, "corner must be transparent");
    }

    @Test
    public void adjacentRoomsGetDifferentColours() throws Exception {
        MovaMap.Result map = TestMaps.map();
        int[] pix = new int[map.imgWidth * map.imgHeight];
        for (int i = 0; i < pix.length; i++) {
            int v = map.image[i] & 0xff;
            pix[i] = v == 0 ? -1 : (v & 0x80) != 0 ? 64 : v & 0x3f;
        }
        int[] colors = MovaMapRenderer.colorRooms(pix, map.imgWidth, map.imgHeight, 4);
        assertNotEquals(colors[1], colors[2]);
    }

    @Test
    public void labelBlockAvoidsTheHole() throws Exception {
        MovaMap.Result map = TestMaps.map();
        int[] spot = MovaMapRenderer.fitBlock(map, 2, 20, 0, 39, 19, 2, 2);
        assertNotNull(spot);
        for (int y = spot[1] - 2; y <= spot[1] + 2; y++) {
            for (int x = spot[0] - 2; x <= spot[0] + 2; x++) {
                assertTrue(MovaVacuumHandler.isRoomPixel(map, x, y, 2));
            }
        }
        assertNull(MovaMapRenderer.fitBlock(map, 2, 20, 0, 39, 19, 30, 2), "block wider than the room");
    }
}
