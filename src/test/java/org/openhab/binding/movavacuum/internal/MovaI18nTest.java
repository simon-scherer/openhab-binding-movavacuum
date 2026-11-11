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

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the German/English texts ({@link MovaI18n}, {@link MovaErrors}, {@link MovaLabels}).
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public class MovaI18nTest {

    @AfterEach
    public void reset() {
        MovaI18n.setLocaleProvider(null);
    }

    @Test
    public void defaultIsEnglish() {
        MovaI18n.setLocaleProvider(null);
        assertFalse(MovaI18n.isGerman());
        assertEquals("No error", MovaErrors.text(0));
        assertEquals("Error 99999", MovaErrors.text(99999));
    }

    @Test
    public void germanLocale() {
        MovaI18n.setLocaleProvider(() -> Locale.GERMANY);
        assertTrue(MovaI18n.isGerman());
        assertEquals("Kein Fehler", MovaErrors.text(0));
        assertEquals("Fehler 99999", MovaErrors.text(99999));
        assertEquals("Roboter angehoben", MovaErrors.interruptReason(11));
    }

    @Test
    public void otherLocaleIsEnglish() {
        MovaI18n.setLocaleProvider(() -> Locale.FRANCE);
        assertEquals("Robot lifted", MovaErrors.interruptReason(11));
    }

    @Test
    public void everyErrorCodeHasBothTexts() throws Exception {
        Field de = MovaErrors.class.getDeclaredField("TEXTS");
        Field en = MovaErrors.class.getDeclaredField("TEXTS_EN");
        de.setAccessible(true);
        en.setAccessible(true);
        assertEquals(((Map<?, ?>) de.get(null)).keySet(), ((Map<?, ?>) en.get(null)).keySet());
    }

    @Test
    public void everyRuntimeChannelHasAnEnglishLabel() throws Exception {
        Set<String> ids = new TreeSet<>();
        Field extra = MovaVacuumHandler.class.getDeclaredField("EXTRA_CHANNELS");
        extra.setAccessible(true);
        for (String[] row : (String[][]) extra.get(null)) {
            ids.add(row[0]);
        }
        MovaSettings.DEFS.forEach(d -> ids.add(d.channel));
        MovaStation.SENSORS.forEach(s -> ids.add(s.channel));
        ids.add(MovaBindingConstants.CHANNEL_WASHBOARD_LEFT);
        ids.add(MovaBindingConstants.CHANNEL_WASHBOARD_TIME_LEFT);
        MovaI18n.setLocaleProvider(() -> Locale.ENGLISH);
        for (String id : ids) {
            assertNotEquals("", MovaLabels.label(id, ""), "English label missing for channel " + id);
        }
    }
}
