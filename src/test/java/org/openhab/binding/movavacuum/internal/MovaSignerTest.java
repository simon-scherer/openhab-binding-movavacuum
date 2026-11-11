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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MovaSigner}. Expected values were computed with the Python implementation of
 * Tasshack/dreame-vacuum (_spliced/_signed).
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public class MovaSignerTest {

    private static Map<String, Object> sample() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("did", "988750884");
        data.put("method", "get_properties");
        data.put("params", List.of(Map.of("siid", 2, "piid", 1)));
        Map<String, Object> list = new LinkedHashMap<>();
        list.put("b", 2);
        list.put("a", "ä");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("did", "988750884");
        p.put("id", 5);
        p.put("data", data);
        p.put("flag", true);
        p.put("list", List.of(list));
        return p;
    }

    @Test
    public void md5Hex() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", MovaSigner.md5Hex("abc"));
    }

    @Test
    public void splicedMatchesReference() {
        assertEquals("data=[did=\"988750884\"&method=\"get_properties\"]&did=988750884&flag=true&id=5"
                + "&list=[{\"a\":\"ä\",\"b\":2}]", MovaSigner.spliced(sample(), true));
    }

    @Test
    public void signedMatchesReference() {
        Map<String, Object> signed = MovaSigner.signed(sample(), 1700000000000L, "secret");
        assertEquals("adee5dee21faee2f546a49d155d38c5c", signed.get("sign"));
        assertEquals(1700000000000L, signed.get("timestamp"));
    }

    @Test
    public void toJsonSortsKeysAndEscapes() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("z", "a\"b");
        m.put("a", List.of(1, true));
        assertEquals("{\"a\":[1,true],\"z\":\"a\\\"b\"}", MovaSigner.toJson(m, true));
    }
}
