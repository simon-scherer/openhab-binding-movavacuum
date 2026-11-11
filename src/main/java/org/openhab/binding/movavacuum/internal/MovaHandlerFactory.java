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

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Creates the handlers for the account bridge and the vacuum things.
 *
 * @author Simon Scherer - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.movavacuum", service = ThingHandlerFactory.class)
public class MovaHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set
            .of(MovaBindingConstants.THING_TYPE_ACCOUNT, MovaBindingConstants.THING_TYPE_VACUUM);

    private final HttpClient httpClient;

    @Activate
    public MovaHandlerFactory(@Reference HttpClientFactory httpClientFactory,
            @Reference LocaleProvider localeProvider) {
        this.httpClient = httpClientFactory.getCommonHttpClient();
        MovaI18n.setLocaleProvider(localeProvider);
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (MovaBindingConstants.THING_TYPE_ACCOUNT.equals(thingTypeUID) && thing instanceof Bridge bridge) {
            return new MovaAccountHandler(bridge, httpClient);
        }
        if (MovaBindingConstants.THING_TYPE_VACUUM.equals(thingTypeUID)) {
            return new MovaVacuumHandler(thing);
        }
        return null;
    }
}
