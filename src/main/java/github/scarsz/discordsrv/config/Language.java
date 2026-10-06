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

package github.scarsz.discordsrv.config;

import lombok.Getter;

/**
 * Languages that DiscordSRV ships translated configuration files for.
 * Replaces configuralize's Language enum from the Spigot version.
 */
public enum Language {

    EN("English"),
    FR("French"),
    DE("German"),
    JA("Japanese"),
    KO("Korean"),
    NL("Dutch"),
    ES("Spanish"),
    RU("Russian"),
    ET("Estonian"),
    ZH("Chinese"),
    PL("Polish"),
    DA("Danish"),
    UK("Ukrainian"),
    NB("Norwegian");

    @Getter private final String name;

    Language(String name) {
        this.name = name;
    }

    public String getCode() {
        return name();
    }

}
