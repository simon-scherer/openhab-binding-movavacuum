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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Consistency of thing-types.xml, the i18n files and the channels the handler creates at runtime.
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public class MovaResourcesTest {

    private static Document thingTypes() throws Exception {
        try (InputStream in = MovaResourcesTest.class.getResourceAsStream("/OH-INF/thing/thing-types.xml")) {
            assertNotNull(in, "thing-types.xml on the classpath");
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        }
    }

    private static Properties props(String name) throws Exception {
        Properties p = new Properties();
        try (InputStream in = MovaResourcesTest.class.getResourceAsStream("/OH-INF/i18n/" + name)) {
            assertNotNull(in, name + " on the classpath");
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    private static Set<String> channelTypeIds(Document doc) {
        Set<String> ids = new HashSet<>();
        NodeList nl = doc.getElementsByTagName("channel-type");
        for (int i = 0; i < nl.getLength(); i++) {
            ids.add(((Element) nl.item(i)).getAttribute("id"));
        }
        return ids;
    }

    @Test
    public void everyChannelTypeIsTranslated() throws Exception {
        Properties en = props("movavacuum.properties");
        Properties de = props("movavacuum_de.properties");
        for (String id : channelTypeIds(thingTypes())) {
            String key = "channel-type.movavacuum." + id + ".label";
            assertNotNull(en.getProperty(key), "missing " + key + " (en)");
            assertNotNull(de.getProperty(key), "missing " + key + " (de)");
        }
    }

    @Test
    public void everyOptionIsTranslated() throws Exception {
        Properties de = props("movavacuum_de.properties");
        NodeList types = thingTypes().getElementsByTagName("channel-type");
        for (int i = 0; i < types.getLength(); i++) {
            Element ct = (Element) types.item(i);
            for (String kind : new String[] { "state", "command" }) {
                NodeList holders = ct.getElementsByTagName(kind);
                for (int h = 0; h < holders.getLength(); h++) {
                    NodeList opts = ((Element) holders.item(h)).getElementsByTagName("option");
                    for (int o = 0; o < opts.getLength(); o++) {
                        String key = "channel-type.movavacuum." + ct.getAttribute("id") + "." + kind + ".option."
                                + ((Element) opts.item(o)).getAttribute("value");
                        assertNotNull(de.getProperty(key), "missing " + key);
                    }
                }
            }
        }
    }

    @Test
    public void runtimeChannelsUseExistingChannelTypes() throws Exception {
        Set<String> types = channelTypeIds(thingTypes());
        Field extra = MovaVacuumHandler.class.getDeclaredField("EXTRA_CHANNELS");
        extra.setAccessible(true);
        for (String[] row : (String[][]) Objects.requireNonNull(extra.get(null))) {
            assertTrue(types.contains(row[2]), "unknown channel type " + row[2] + " for " + row[0]);
        }
        MovaSettings.DEFS.forEach(d -> assertTrue(types.contains(d.channelTypeId), "unknown " + d.channelTypeId));
        MovaStation.SENSORS.forEach(s -> assertTrue(types.contains(s.channelTypeId), "unknown " + s.channelTypeId));
    }
}
