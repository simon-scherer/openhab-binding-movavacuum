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

/**
 * Fehler bei der Kommunikation mit der Mova/DreameHome-Cloud.
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public class MovaCloudException extends Exception {

    private static final long serialVersionUID = 1L;

    public MovaCloudException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
