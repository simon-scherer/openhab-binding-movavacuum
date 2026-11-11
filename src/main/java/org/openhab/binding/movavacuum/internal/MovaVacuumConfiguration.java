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

/**
 * Konfiguration des Vacuum-Things (Thing-Type "vacuum").
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public class MovaVacuumConfiguration {

    /** Die "did" (device id) des Saugers, wie von der Geräteliste der Cloud geliefert. */
    public String did = "";

    /** Abfrage-Intervall für die Einstellungen (Teppich, Boden, ...) in Sekunden. */
    public int settingsInterval = 20;

    /** Optional: AES-Anfangswert (16 Zeichen) für die Kartenentschlüsselung; leer = eingebauter Wert des Modells. */
    public String mapKey = "";

    /** Drehung des Kartenbilds in Grad: -1 = automatisch (Wert der Karte), sonst 0, 90, 180 oder 270. */
    public int mapRotation = -1;

    /** Spiegelt das Kartenbild von oben nach unten (vor dem Drehen). */
    public boolean mapFlip = true;

    /** Zeigt in der App ausgeblendete Räume im Kartenbild (grau) an. */
    public boolean showHiddenRooms = false;

    /** Angestrebte Kantenlänge des Kartenbilds in Pixeln. */
    public int mapSize = 600;

    /** Beschriftung im Kartenbild: name, number oder both. */
    public String mapLabels = "name";
}
