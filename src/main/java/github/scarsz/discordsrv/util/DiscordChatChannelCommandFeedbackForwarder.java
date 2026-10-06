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

import github.scarsz.discordsrv.DiscordSRV;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.StringJoiner;

/**
 * Collects the feedback of a command ran from a Discord chat channel (with the console command prefix)
 * and sends it back to the channel the command was sent in.
 */
public class DiscordChatChannelCommandFeedbackForwarder {

    private final MessageReceivedEvent event;
    private final TextChannel channel;

    private StringJoiner messageBuffer = new StringJoiner("\n");

    private boolean bufferCollecting = false;

    public DiscordChatChannelCommandFeedbackForwarder(MessageReceivedEvent event) {
        this.event = event;
        this.channel = event.getChannel() instanceof TextChannel ? (TextChannel) event.getChannel() : null;

        // expire request message after specified time
        if (DiscordSRV.config().getInt("DiscordChatChannelConsoleCommandExpiration") > 0 && DiscordSRV.config().getBoolean("DiscordChatChannelConsoleCommandExpirationDeleteRequest")) {
            SchedulerUtil.runTaskLaterAsynchronously(() -> {
                event.getMessage().delete().queue();
            }, DiscordSRV.config().getInt("DiscordChatChannelConsoleCommandExpiration") * 20L); // Seconds to ticks
        }
    }

    /**
     * Forwards the given command feedback
     */
    public void send(Component message) {
        send(PlainTextComponentSerializer.plainText().serialize(message));
    }

    public synchronized void send(String message) {
        if (this.bufferCollecting) { // If the buffer has started collecting messages, we should just add this one to it.
            if (DiscordUtil.escapeMarkdown(this.messageBuffer + "\n" + message).length() > 1998) { // If the message will be too long (allowing for markdown escaping and the newline)
                // Send the message, then clear the buffer and add this message to the empty buffer
                DiscordUtil.sendMessage(channel, DiscordUtil.escapeMarkdown(this.messageBuffer.toString()), DiscordSRV.config().getInt("DiscordChatChannelConsoleCommandExpiration") * 1000);
                this.messageBuffer = new StringJoiner("\n");
                this.messageBuffer.add(message);
            } else { // If adding this message to the buffer won't send it over the 2000 character limit
                this.messageBuffer.add(message);
            }
        } else { // Messages aren't currently being collected, let's start doing that
            this.bufferCollecting = true;
            this.messageBuffer.add(message); // This message is the first one in the buffer
            SchedulerUtil.runTaskLaterAsynchronously(this::sendBuffer, 3L); // Collect messages for 3 ticks, then send
        }
    }

    private synchronized void sendBuffer() {
        this.bufferCollecting = false;
        if (this.messageBuffer.length() == 0) return; // There's nothing in the buffer to send, leave it
        DiscordUtil.sendMessage(channel, DiscordUtil.escapeMarkdown(this.messageBuffer.toString()), DiscordSRV.config().getInt("DiscordChatChannelConsoleCommandExpiration") * 1000);
        this.messageBuffer = new StringJoiner("\n");
    }

}
