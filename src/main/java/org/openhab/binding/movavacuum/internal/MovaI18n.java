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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.i18n.LocaleProvider;

/**
 * Language of the texts the binding writes into channels (error texts, room lists, obstacles, ...).
 * German if the openHAB regional settings use German, English otherwise. Channel and config labels are
 * translated by openHAB itself via the i18n properties files.
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public final class MovaI18n {
    private static volatile @Nullable LocaleProvider localeProvider;

    private MovaI18n() {
    }

    public static void setLocaleProvider(@Nullable LocaleProvider provider) {
        localeProvider = provider;
    }

    public static boolean isGerman() {
        LocaleProvider p = localeProvider;
        return p != null && "de".equals(p.getLocale().getLanguage());
    }

    /** Returns the German or the English text depending on the configured language. */
    public static String t(String de, String en) {
        return isGerman() ? de : en;
    }
}
