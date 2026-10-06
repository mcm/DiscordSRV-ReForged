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

package github.scarsz.discordsrv.platform.event;

import github.scarsz.discordsrv.platform.GamePlayer;
import net.kyori.adventure.text.Component;

/**
 * A player completed an advancement
 */
public class PlayerAdvancementDoneEvent extends PlayerGameEvent {

    private final String advancementId;
    private final Component title;
    private final Component description;
    private final String frame;
    private final boolean announceToChat;

    public PlayerAdvancementDoneEvent(GamePlayer player, String advancementId, Component title, Component description,
                                      String frame, boolean announceToChat, Object platformEvent) {
        super(player, platformEvent);
        this.advancementId = advancementId;
        this.title = title;
        this.description = description;
        this.frame = frame;
        this.announceToChat = announceToChat;
    }

    /**
     * @return the advancement's id, eg. {@code minecraft:story/mine_stone}
     */
    public String getAdvancementId() {
        return advancementId;
    }

    /**
     * @return the advancement's title, or null if the advancement has no display (eg. recipe advancements)
     */
    public Component getTitle() {
        return title;
    }

    public Component getDescription() {
        return description;
    }

    /**
     * @return the frame type: task, goal or challenge (null if not displayed)
     */
    public String getFrame() {
        return frame;
    }

    /**
     * @return whether the game would announce this advancement in chat
     */
    public boolean isAnnounceToChat() {
        return announceToChat;
    }

}
