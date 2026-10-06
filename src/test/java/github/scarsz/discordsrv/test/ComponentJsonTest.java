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

package github.scarsz.discordsrv.test;

import github.scarsz.discordsrv.util.ComponentJsonUtil;
import github.scarsz.discordsrv.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ComponentJsonTest {

    /**
     * Minecraft 1.21.1 uses camelCase clickEvent/hoverEvent with "contents"; newer versions use snake_case,
     * which 1.21.1 can't read.
     */
    @Test
    public void emitsMinecraft1211Format() {
        Component component = Component.text("link")
                .clickEvent(ClickEvent.openUrl("https://example.com"))
                .hoverEvent(HoverEvent.showText(Component.text("hover")));
        String json = ComponentJsonUtil.toJson(component);
        assertTrue(json.contains("\"clickEvent\""), json);
        assertTrue(json.contains("\"hoverEvent\""), json);
        assertTrue(json.contains("\"contents\""), json);
        assertFalse(json.contains("click_event"), json);
        assertEquals(component, ComponentJsonUtil.fromJson(json));
    }

    @Test
    public void legacyAndMiniMessageFormatting() {
        assertEquals("Hello", MessageUtil.strip("§aHel&blo"));
        assertEquals("§cred", MessageUtil.translateLegacy("&cred"));
        Component mini = MessageUtil.toComponent("<aqua>Discord</aqua> text");
        assertEquals("Discord text", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(mini));
        // role mentions must not be treated as color codes
        assertEquals("<@&123>", MessageUtil.translateLegacy("<@&123>"));
    }

}
