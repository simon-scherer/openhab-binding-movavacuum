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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Descriptions of the error codes in German and English (source: error list and translations of the
 * dreame-vacuum integration by Tasshack, MIT license).
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public final class MovaErrors {
    private static final Map<Integer, String> TEXTS = build();
    private static final Map<Integer, String> TEXTS_EN = buildEn();

    private MovaErrors() {
    }

    private static Map<Integer, String> build() {
        Map<Integer, String> m = new HashMap<>();
        m.put(0, "Kein Fehler");
        m.put(1, "Räder in der Luft");
        m.put(2, "Klippensensorfehler");
        m.put(3, "Aufprallsensor klemmt");
        m.put(4, "Roboter ist gekippt");
        m.put(5, "Kollisionssensor klemmt");
        m.put(6, "Räder in der Luft");
        m.put(7, "Fehler des optischen Flow-Sensors");
        m.put(8, "Staubbehälter nicht installiert");
        m.put(9, "Wassertank nicht installiert");
        m.put(10, "Wassertank ist leer");
        m.put(11, "Der Filter ist nicht trocken oder verstopft");
        m.put(12, "Die Hauptbürste ist eingewickelt");
        m.put(13, "Die Seitenbürste ist eingewickelt");
        m.put(14, "Der Filter ist feucht oder verstopft");
        m.put(15, "Der Roboter steckt fest, oder sein linkes Rad ist möglicherweise durch Fremdkörper blockiert");
        m.put(16, "Der Roboter steckt fest, oder sein rechtes Rad ist möglicherweise durch Fremdkörper blockiert");
        m.put(17, "Der Roboter steckt fest oder kann sich nicht drehen");
        m.put(18, "Der Roboter steckt fest oder kann nicht vorwärts fahren");
        m.put(19, "Kann die Ladestation nicht finden");
        m.put(20, "Batterie schwach");
        m.put(21, "Fehler beim Aufladen");
        m.put(22, "Fehler beim Batteriestand");
        m.put(23, "Interner Fehler");
        m.put(24, "Fehler des visuellen Positionssensors");
        m.put(25, "Fehler des Bewegungssensors");
        m.put(26, "Optischer Sensorfehler");
        m.put(27, "Fehler in der Infrarotabschirmung");
        m.put(28, "Die Ladestation ist nicht eingeschaltet");
        m.put(29, "Batterie Fehler");
        m.put(30, "Fehler des Lüfterdrehzahlsensors");
        m.put(31, "Linkes Rad ist möglicherweise durch Fremdkörper blockiert");
        m.put(32, "Rechtes Rad ist möglicherweise durch Fremdkörper blockiert");
        m.put(33, "Fehler im Beschleunigungssensor");
        m.put(34, "Fehler im Beschleunigungssensor");
        m.put(35, "Fehler im Beschleunigungssensor");
        m.put(36, "Fehler des linken Magnetsensors");
        m.put(37, "Fehler des rechten Magnetsensors");
        m.put(38, "Durchflusssensor Fehler");
        m.put(39, "Infrarotsensor Fehler");
        m.put(40, "Kamera Fehler");
        m.put(41, "Starkes Magnetfeld festgestellt");
        m.put(42, "Wasserpumpen Fehler");
        m.put(43, "RTC Fehler");
        m.put(44, "Interner Fehler");
        m.put(45, "Interner Fehler");
        m.put(46, "Interner Fehler");
        m.put(47, "Reinigungsroute ist blockiert kehre zur Dock zurück");
        m.put(48, "Fehler im Laserentfernungssensor");
        m.put(49, "Fehler im Laserentfernungssensor (Bumper)");
        m.put(50, "Wasserpumpen Fehler");
        m.put(51, "Der Filter ist feucht oder verstopft");
        m.put(54, "Kantensensor Fehler");
        m.put(55, "Bitte den Roboter in einem Bereich ohne Teppich starten.");
        m.put(56, "Der 3D-Hindernisvermeidungssensor ist defekt.");
        m.put(57, "Kantensensor Fehler");
        m.put(58, "Der Ultraschallsensor ist gestört.");
        m.put(59, "No-Go-Zone oder virtuelle Wand erkannt.");
        m.put(61, "Die Reinigungsroute ist blockiert.");
        m.put(62, "Die Reinigungsroute ist blockiert.");
        m.put(63, "Reinigungsroute ist blockiert kehre zur Dock zurück");
        m.put(64, "Reinigungsroute ist blockiert kehre zur Dock zurück");
        m.put(65, "Es wurde festgestellt, dass sich der Staubsauger in einem Sperrgebiet befindet.");
        m.put(66, "Es wurde festgestellt, dass sich der Staubsauger in einem Sperrgebiet befindet.");
        m.put(67, "Es wurde festgestellt, dass sich der Staubsauger in einem Sperrgebiet befindet.");
        m.put(68, "Wischen abgeschlossen. Bitte das Wischpad rechtzeitig entfernen und reinigen.");
        m.put(69, "Das Wischpad hat sich während der Reinigung gelöst.");
        m.put(70, "Das Wischpad hat sich während der Reinigung gelöst.");
        m.put(71, "Das Wischpad dreht sich nicht mehr");
        m.put(72, "Das Wischpad dreht sich nicht mehr");
        m.put(74, "Die Installation des Wischpads ist fehlgeschlagen.");
        m.put(75, "Niedriger Batteriestatus.");
        m.put(76, "Der Schmutzwassertank des Roboters ist nicht installiert.");
        m.put(78, "Roboter im versteckten Bereich. Bitte aus dem Bereich entfernen und erneut versuchen.");
        m.put(79, "LDS-Modul konnte nicht angehoben werden.");
        m.put(80, "LDS kann hier zur Positionierung nicht angehoben werden.");
        m.put(81, "LDS kann hier zur Positionierung nicht angehoben werden.");
        m.put(82, "Rutschiger Boden. Bitte versuchen Sie es später erneut.");
        m.put(85, "Bitte prüfen Sie, ob der Mopp richtig installiert ist.");
        m.put(86, "Abnormaler Wasserstand im Schmutzwassertank des Roboters.");
        m.put(88, "Bitte prüfen Sie, ob die einziehbaren Beine verheddert sind.");
        m.put(89, "Fehlfunktion aufgrund eines internen Fehlers. Versuchen Sie, den Roboter neu zu starten.");
        m.put(90, "LDS kann hier zur Positionierung nicht angehoben werden.");
        m.put(91, "Roboter steckt zwischen Tischen und Stühlen fest.");
        m.put(92, "Roboter steckt im engen Durchgang fest.");
        m.put(93, "Roboter steckt an der Stufe/Schwelle fest.");
        m.put(94, "Roboter steckt in einem Bereich mit geringer Höhe fest.");
        m.put(95, "Roboter hat Rampen mit Absturzgefahr auf dem Weg erkannt.");
        m.put(96, "Roboter hat Hindernisse auf dem Weg erkannt.");
        m.put(97, "Roboter hat Personen oder Haustiere auf dem Weg erkannt.");
        m.put(98, "Roboter steckt aufgrund von Rutschen fest.");
        m.put(99, "Roboter rutscht auf dem Teppich");
        m.put(101, "Der Staubsaugerbeutel ist voll, oder der Luftkanal ist verstopft.");
        m.put(102, "Die obere Abdeckung der Ladestation ist nicht geschlossen, oder der Staubsaugerbeutel ist nicht installiert.");
        m.put(103, "Die obere Abdeckung der Ladestation ist nicht geschlossen, oder der Staubsaugerbeutel ist nicht installiert.");
        m.put(104, "Der Staubsaugerbeutel ist voll, oder der Luftkanal ist verstopft.");
        m.put(105, "Der Frischwassertank ist nicht installiert.");
        m.put(106, "Der Schmutzwassertank ist voll oder nicht installiert.");
        m.put(107, "Niedriger Wasserstand im Frischwassertank, bitte rechtzeitig Wasser nachfüllen.");
        m.put(108, "Der Schmutzwassertank ist voll oder nicht installiert.");
        m.put(109, "Schmutzwassertank blockiert.");
        m.put(110, "Schmutzwassertank Pumpenfehler.");
        m.put(111, "Das Wischpad ist nicht richtig installiert.");
        m.put(112, "Der Wasserstand des Wischpads ist ungewöhnlich, bitte den Wischpad rechtzeitig reinigen.");
        m.put(114, "Die Reinigungsaufgabe ist abgeschlossen, bitte das Wischpad reinigen.");
        m.put(116, "Bitte den Frischwassertank umgehend überprüfen und nachfüllen.");
        m.put(117, "Basisstation nicht eingeschaltet");
        m.put(118, "Der Wasserstand im Schmutzwassertank ist zu hoch.");
        m.put(119, "Wasserstand im Waschbrett ist zu hoch.");
        m.put(120, "Wischpad ist nicht in der Station.");
        m.put(121, "Der Staubbeutel ist voll oder die Lüftungsschlitze sind blockiert.");
        m.put(123, "Ungewöhnlicher Wasseraustritt aus dem Frischwassertank des oberen und unteren Wassermoduls.");
        m.put(124, "Waschbrett funktioniert nicht mehr.");
        m.put(125, "Abnormaler Wasserablauf aus dem Schmutzwassertank");
        m.put(126, "Mopp nicht erkannt.");
        m.put(127, "Fehler bei Menge/Platzierung der Mopphalter in der Station.");
        m.put(128, "Stationsfehler.");
        m.put(129, "Reinigung des schmutzigen Mopps fehlgeschlagen.");
        m.put(200, "Roboter rutscht im Vorhangbereich");
        m.put(201, "Kantenmopp dreht sich nicht mehr.");
        m.put(202, "Kantenmopp hat sich gelöst.");
        m.put(203, "Fehlfunktion der Fahrgestell-Anhebung.");
        m.put(207, "Fehlfunktion aufgrund eines internen Fehlers. Versuchen Sie, den Roboter neu zu starten.");
        m.put(209, "Prüfen Sie auf Fremdkörper in der Nähe der Mopprolle und der Abdeckung.");
        m.put(210, "Prüfen Sie auf Fremdkörper in der Nähe der Mopprolle und der Abdeckung.");
        m.put(212, "Roboterarm gestoppt");
        m.put(213, "Niedriger Wasserstand im Frischwassertank des Roboters.");
        m.put(214, "Der Schmutzwassertank des Roboters ist voll.");
        m.put(215, "Mopp nicht installiert.");
        m.put(217, "Fehler im Laserentfernungssensor");
        m.put(218, "Prüfen Sie auf Fremdkörper in der Nähe der Mopprolle und der Abdeckung.");
        m.put(222, "Fehler bei der Auflockerungsrolle.");
        m.put(223, "Prüfen Sie auf Fremdkörper in der Nähe der Mopprolle und der Abdeckung.");
        m.put(224, "Prüfen Sie auf Fremdkörper in der Nähe der Mopprolle und der Abdeckung.");
        m.put(225, "Prüfen Sie auf Fremdkörper in der Nähe der Mopprolle und der Abdeckung.");
        m.put(226, "Roboter durch Hindernis blockiert.");
        m.put(227, "Abflussfilter verstopft");
        m.put(228, "Fehler der Haupträder");
        m.put(229, "Fehlfunktion aufgrund eines internen Fehlers. Versuchen Sie, den Roboter neu zu starten.");
        m.put(230, "Fehlfunktion aufgrund eines internen Fehlers. Versuchen Sie, den Roboter neu zu starten.");
        m.put(1000, "Rückkehr zum Laden fehlgeschlagen.");
        return m;
    }

    private static Map<Integer, String> buildEn() {
        Map<Integer, String> m = new HashMap<>();
        m.put(0, "No error");
        m.put(1, "Wheels are suspended");
        m.put(2, "Cliff sensor error");
        m.put(3, "Collision sensor is stuck");
        m.put(4, "Robot is tilted");
        m.put(5, "Collision sensor is stuck");
        m.put(6, "Wheels are suspended");
        m.put(7, "Optical flow sensor error");
        m.put(8, "Dust bin not installed");
        m.put(9, "Water tank not installed");
        m.put(10, "Water tank is empty");
        m.put(11, "The filter not dry or blocked");
        m.put(12, "The main brush wrapped");
        m.put(13, "The side brush wrapped");
        m.put(14, "The filter not dry or blocked");
        m.put(15, "The robot is stuck, or its left wheel may be blocked by foreign objects");
        m.put(16, "The robot is stuck, or its right wheel may be blocked by foreign objects");
        m.put(17, "The robot is stuck, or cannot turn");
        m.put(18, "The robot is stuck, or cannot go forward");
        m.put(19, "Cannot find base");
        m.put(20, "Low battery");
        m.put(21, "Charging error");
        m.put(22, "Battery level error");
        m.put(23, "Internal error");
        m.put(24, "Visual positioning sensor error");
        m.put(25, "Move sensor error");
        m.put(26, "Optical sensor error");
        m.put(27, "Infrared shielding error");
        m.put(28, "The charging dock is not powered on");
        m.put(29, "Battery error");
        m.put(30, "Fan speed sensor error");
        m.put(31, "Left wheel may be blocked by foreign objects");
        m.put(32, "Right wheel may be blocked by foreign objects");
        m.put(33, "Accelerometer error");
        m.put(34, "Gyro error");
        m.put(35, "Gyro error");
        m.put(36, "Left magnet sensor error");
        m.put(37, "Right magnet sensor error");
        m.put(38, "Flow sensor error");
        m.put(39, "Infrared error");
        m.put(40, "Camera error");
        m.put(41, "Strong magnetic field detected");
        m.put(42, "Water pump error");
        m.put(43, "RTC error");
        m.put(44, "Internal error");
        m.put(45, "Internal error");
        m.put(46, "Internal error");
        m.put(47, "Cleanup route is blocked, returning to the dock.");
        m.put(48, "Laser distance sensor error");
        m.put(49, "Laser distance sensor bumper error");
        m.put(50, "Water pump error");
        m.put(51, "The filter not dry or blocked");
        m.put(54, "Edge sensor error");
        m.put(55, "Please start the robot in non-carpet area.");
        m.put(56, "The 3D obstacle avoidance sensor is malfunctioning.");
        m.put(57, "Edge sensor error");
        m.put(58, "The ultrasonic sensor is malfunctioning.");
        m.put(59, "No-Go zone or virtual wall detected.");
        m.put(61, "Cleanup route is blocked.");
        m.put(62, "Cleanup route is blocked.");
        m.put(63, "Cleanup route is blocked, returning to the dock.");
        m.put(64, "Cleanup route is blocked, returning to the dock.");
        m.put(65, "Detected that the vacuum-mop is in a restricted area.");
        m.put(66, "Detected that the vacuum-mop is in a restricted area.");
        m.put(67, "Detected that the vacuum-mop is in a restricted area.");
        m.put(68, "Mopping completed. Please remove and clean the mop in time.");
        m.put(69, "The mop pad came off during the cleaning task.");
        m.put(70, "The mop pad came off during the cleaning task.");
        m.put(71, "The mop pad has stopped rotating.");
        m.put(72, "The mop pad has stopped rotating.");
        m.put(74, "Mop pad installation failed.");
        m.put(75, "Low battery. Robot will shut down soon.");
        m.put(76, "The used water tank of robot is not installed.");
        m.put(78, "Hidden area. Please move the robot to the appropriate area and retry.");
        m.put(79, "Failed to lift LDS module.");
        m.put(80, "LDS cannot be raised here for positioning.");
        m.put(81, "LDS cannot be raised here for positioning.");
        m.put(82, "Slippery floor. Please try again later.");
        m.put(85, "Please check if the mop is installed properly.");
        m.put(86, "Abnormal water level in the robot's used water tank.");
        m.put(88, "Please check if the retractable legs are tangled.");
        m.put(89, "Malfunction due to an internal error. Try to restart the robot.");
        m.put(90, "LDS cannot be raised here for positioning.");
        m.put(91, "Robot stuck among the tables and chairs.");
        m.put(92, "Robot stuck in the narrow passage.");
        m.put(93, "Robot stuck at the step/threshold.");
        m.put(94, "Robot stuck in a low-clearance area.");
        m.put(95, "Robot has detected Easy-to-Fall ramps on the path.");
        m.put(96, "Robot has detected obstacles on the path.");
        m.put(97, "Robot has detected people or pets on the path.");
        m.put(98, "Robot is stuck due to slipping.");
        m.put(99, "Robot slips on the carpet");
        m.put(101, "The dust collection bag is full, or the air duct is blocked.");
        m.put(102, "The upper cover of auto-empty base is not closed, or the dust collection bag is not installed.");
        m.put(103, "The upper cover of auto-empty base is not closed, or the dust collection bag is not installed.");
        m.put(104, "The dust collection bag is full, or the air duct is blocked.");
        m.put(105, "The clean water tank is not installed.");
        m.put(106, "The dirty water tank is full or not installed.");
        m.put(107, "Low water level in the clean water tank, please fill with water timely.");
        m.put(108, "The dirty water tank is full or not installed.");
        m.put(109, "Dirty water tank blocked.");
        m.put(110, "Dirty water tank pump error.");
        m.put(111, "The washboard is not installed properly.");
        m.put(112, "The water level of the washboard is abnormal, please clean the washboard timely.");
        m.put(114, "The cleaning task is complete, please clean the mop pad washboard.");
        m.put(116, "Check and fill the clean water tank.");
        m.put(117, "Base station not powered on");
        m.put(118, "The water level in the used water tank is too high.");
        m.put(119, "Water level in the washboard is too high.");
        m.put(120, "Mop pad is not in the station.");
        m.put(121, "Dust bag is full or vents are blocked.");
        m.put(123, "Abnormal water discharge from the clean water tank of the upper and lower water modules.");
        m.put(124, "Washboard stops working.");
        m.put(125, "Abnormal water drainage from used water tank");
        m.put(126, "Mop not detected.");
        m.put(127, "Mop holder quantity/placement error in the dock.");
        m.put(128, "Dock Error.");
        m.put(129, "Failed to wash dirty mop.");
        m.put(200, "Robot slips in the curtain area");
        m.put(201, "Edge mop stopped rotating.");
        m.put(202, "Edge mop detached.");
        m.put(203, "Chassis lift malfunction.");
        m.put(207, "Malfunction due to an internal error. Try to restart the robot.");
        m.put(209, "Check for any debris near the roller mop and mop cover.");
        m.put(210, "Check for any debris near the roller mop and mop cover.");
        m.put(212, "Robotic arm stopped.");
        m.put(213, "Low water level in robot’s clean water box.");
        m.put(214, "Robot's used water box is full.");
        m.put(215, "Mop Not Installed.");
        m.put(217, "Laser distance sensor error");
        m.put(218, "Check for any debris near the roller mop and mop cover.");
        m.put(222, "Fluffing Roller Error.");
        m.put(223, "Check for any debris near the roller mop and mop cover.");
        m.put(224, "Check for any debris near the roller mop and mop cover.");
        m.put(225, "Check for any debris near the roller mop and mop cover.");
        m.put(226, "Robot blocked by obstacle.");
        m.put(227, "The wastewater outlet filter of robot is clogged.");
        m.put(228, "Main wheels error.");
        m.put(229, "Malfunction due to an internal error. Try to restart the robot.");
        m.put(230, "Malfunction due to an internal error. Try to restart the robot.");
        m.put(1000, "Failed to return to charge.");
        return m;
    }

    /** Reason why a cleaning job was interrupted (TaskInterruptReason of the reference). */
    public static String interruptReason(int code) {
        return switch (code) {
            case 11 -> MovaI18n.t("Roboter angehoben", "Robot lifted");
            case 12 -> MovaI18n.t("Roboter umgefallen", "Robot tipped over");
            case 13 -> MovaI18n.t("Klippensensor-Fehler", "Cliff sensor error");
            case 14 -> MovaI18n.t("Mopp-Pad entfernt", "Mop pad removed");
            case 15, 17, 18, 20 -> MovaI18n.t("Mopp-Pad abgefallen", "Mop pad fell off");
            case 16 -> MovaI18n.t("Mopp-Pad klemmt", "Mop pad stuck");
            case 19 -> MovaI18n.t("Mopp-Pad unerwartet entfernt", "Mop pad removed unexpectedly");
            case 21, 22, 23 -> MovaI18n.t("Bürste verfangen", "Brush tangled");
            case 24 -> MovaI18n.t("Laserdistanzsensor-Fehler", "Laser distance sensor error");
            case 25 -> MovaI18n.t("Roboter steckt an Stufe fest", "Robot stuck on a step");
            case 26 -> MovaI18n.t("Roboter steckt an Hindernis fest", "Robot stuck on an obstacle");
            case 27 -> MovaI18n.t("Basisstation ohne Strom", "Base station without power");
            case 101 -> MovaI18n.t("Andocken fehlgeschlagen", "Docking failed");
            case 102 -> MovaI18n.t("Basisstation nicht gefunden", "Base station not found");
            default -> "Code " + code;
        };
    }

    /** Description of the error code in the configured language; "Error <code>" for unknown codes. */
    public static String text(int code) {
        String t = (MovaI18n.isGerman() ? TEXTS : TEXTS_EN).get(code);
        return t != null ? t : MovaI18n.t("Fehler ", "Error ") + code;
    }
}
