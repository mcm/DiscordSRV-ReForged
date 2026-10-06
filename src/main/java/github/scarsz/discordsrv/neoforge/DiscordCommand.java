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

package github.scarsz.discordsrv.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.platform.CommandSender;
import github.scarsz.discordsrv.util.SchedulerUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Registers the {@code /discord} command (aliased {@code /discordsrv}) with Brigadier and hands it over to
 * DiscordSRV's own command manager.
 */
public final class DiscordCommand {

    private DiscordCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, DiscordSRV discordSRV, NeoForgePlatform platform) {
        SuggestionProvider<CommandSourceStack> suggestions = (context, builder) -> suggest(discordSRV, platform, context.getSource(), builder);

        for (String name : new String[] {"discord", "discordsrv"}) {
            dispatcher.register(Commands.literal(name)
                    .requires(source -> NeoForgeCommandSender.of(platform, source).hasPermission("discordsrv.discord"))
                    .executes(context -> execute(discordSRV, platform, context.getSource(), ""))
                    .then(Commands.argument("arguments", StringArgumentType.greedyString())
                            .suggests(suggestions)
                            .executes(context -> execute(discordSRV, platform, context.getSource(), StringArgumentType.getString(context, "arguments")))
                    )
            );
        }
    }

    private static int execute(DiscordSRV discordSRV, NeoForgePlatform platform, CommandSourceStack source, String arguments) {
        CommandSender sender = NeoForgeCommandSender.of(platform, source);
        String[] args = arguments.trim().isEmpty() ? new String[0] : arguments.trim().split("\\s+");
        // commands may talk to Discord, don't block the server thread with that
        SchedulerUtil.runTaskAsynchronously(() -> discordSRV.onCommand(sender, args));
        return 1;
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(DiscordSRV discordSRV, NeoForgePlatform platform, CommandSourceStack source, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        // only the sub command name (the first argument) is completed
        if (remaining.contains(" ")) return builder.buildFuture();

        CommandSender sender = NeoForgeCommandSender.of(platform, source);
        List<String> completions = discordSRV.onTabComplete(sender, new String[] {remaining});
        if (completions != null) {
            completions.stream().sorted().forEach(builder::suggest);
        }
        return builder.buildFuture();
    }

}
