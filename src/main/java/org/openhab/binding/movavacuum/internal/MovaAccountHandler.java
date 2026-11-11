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

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.movavacuum.internal.dto.DeviceRecord;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler für die Account-Bridge: Login bei der Mova/DreameHome-Cloud, Bereitstellung des
 * {@link MovaCloudApi} für die Vacuum-Things und Auflistung der Geräte im Log (dort steht die "did").
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
public class MovaAccountHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(MovaAccountHandler.class);
    private final HttpClient httpClient;

    private @Nullable MovaCloudApi cloudApi;
    private @Nullable ScheduledFuture<?> loginJob;

    public MovaAccountHandler(Bridge bridge, HttpClient httpClient) {
        super(bridge);
        this.httpClient = httpClient;
    }

    @Override
    public void initialize() {
        MovaAccountConfiguration config = getConfigAs(MovaAccountConfiguration.class);
        if (config.username.isBlank() || config.password.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Username and password must be set");
            return;
        }
        updateStatus(ThingStatus.UNKNOWN);
        cloudApi = new MovaCloudApi(httpClient, config.username, config.password, config.accountType,
                config.country);
        // Solange die Bridge nicht ONLINE ist, wird der Login alle 2 Minuten erneut versucht.
        loginJob = scheduler.scheduleWithFixedDelay(this::doLogin, 0, 120, TimeUnit.SECONDS);
    }

    private void doLogin() {
        MovaCloudApi api = cloudApi;
        if (api == null || getThing().getStatus() == ThingStatus.ONLINE) {
            return;
        }
        try {
            if (!api.login()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Login rejected - check credentials, account type and region");
                return;
            }
            updateStatus(ThingStatus.ONLINE);
            logger.info("Logged in to the Mova/DreameHome cloud.");
            logDevices(api);
        } catch (MovaCloudException e) {
            logger.warn("Mova cloud login: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    private void logDevices(MovaCloudApi api) {
        try {
            List<DeviceRecord> devices = api.getDevices();
            if (devices.isEmpty()) {
                logger.warn("The device list of the account is empty - check account type/region.");
            }
            for (DeviceRecord d : devices) {
                logger.info("Mova/Dreame device found: did={} model={} name={}", d.did, d.model,
                        d.getDisplayName());
            }
        } catch (MovaCloudException e) {
            logger.warn("Could not load the device list: {}", e.getMessage());
        }
    }

    public @Nullable MovaCloudApi getCloudApi() {
        return cloudApi;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Die Bridge selbst hat keine Kanäle.
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> job = loginJob;
        if (job != null) {
            job.cancel(true);
            loginJob = null;
        }
        cloudApi = null;
        super.dispose();
    }
}
