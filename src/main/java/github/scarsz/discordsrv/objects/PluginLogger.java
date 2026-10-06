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

package github.scarsz.discordsrv.objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A java.util.logging-like facade over slf4j, so code ported from the Bukkit plugin (which used
 * {@code getLogger().severe(...)} etc.) keeps reading the same.
 */
public class PluginLogger {

    private final Logger logger;

    public PluginLogger(String name) {
        this.logger = LoggerFactory.getLogger(name);
    }

    public void info(String message) {
        logger.info(message);
    }

    public void warning(String message) {
        logger.warn(message);
    }

    public void severe(String message) {
        logger.error(message);
    }

    public void fine(String message) {
        logger.debug(message);
    }

    public Logger getSlf4j() {
        return logger;
    }

}
