/*
 * DiscordSRV - https://github.com/DiscordSRV/DiscordSRV
 *
 * Copyright (C) 2016 - 2024 Austin "Scarsz" Shapiro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package github.scarsz.discordsrv.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONOptions;

/**
 * Converts Adventure components to/from the JSON text format of the Minecraft version this mod targets.
 * Adventure's default serializer emits the newest format (eg. snake_case click/hover events since 1.21.5),
 * which Minecraft 1.21.1 doesn't understand, so the options are pinned to 1.21.1's data version.
 */
public final class ComponentJsonUtil {

    /**
     * The data version of Minecraft 1.21.1
     */
    public static final int DATA_VERSION = 3955;

    public static final GsonComponentSerializer SERIALIZER = GsonComponentSerializer.builder()
            .options(JSONOptions.byDataVersion().at(DATA_VERSION))
            .build();

    private ComponentJsonUtil() {}

    public static String toJson(Component component) {
        return SERIALIZER.serialize(component);
    }

    public static Component fromJson(String json) {
        return SERIALIZER.deserialize(json);
    }

}
