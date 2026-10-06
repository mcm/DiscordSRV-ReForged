package github.scarsz.discordsrv.neoforge;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.util.ComponentJsonUtil;
import github.scarsz.discordsrv.util.MessageUtil;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;

/**
 * Converts between Adventure components (used by DiscordSRV's core) and Minecraft's own components.
 */
public final class ComponentConverter {

    private ComponentConverter() {}

    /**
     * Converts an Adventure component to a Minecraft component through Minecraft's JSON text format.
     * Falls back to a literal with legacy formatting codes if the JSON can't be parsed.
     */
    public static Component toMinecraft(net.kyori.adventure.text.Component component, MinecraftServer server) {
        if (component == null) return Component.empty();
        HolderLookup.Provider registries = server != null ? server.registryAccess() : RegistryAccess.EMPTY;
        try {
            Component converted = Component.Serializer.fromJson(ComponentJsonUtil.toJson(component), registries);
            if (converted != null) return converted;
        } catch (Exception e) {
            DiscordSRV.debug("Failed to convert component to Minecraft's format, falling back to legacy text: " + e);
        }
        return Component.literal(MessageUtil.toLegacy(component));
    }

    /**
     * Converts a Minecraft component to an Adventure component, keeping the text and its colors/decorations.
     * Translatable components are resolved using the server's language (en_us).
     * Click and hover events are intentionally not carried over.
     */
    public static net.kyori.adventure.text.Component toAdventure(Component component) {
        if (component == null) return net.kyori.adventure.text.Component.empty();
        TextComponent.Builder builder = net.kyori.adventure.text.Component.text();
        component.visit((style, text) -> {
            if (!text.isEmpty()) builder.append(net.kyori.adventure.text.Component.text(text, toAdventure(style)));
            return Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return builder.build().compact();
    }

    private static Style toAdventure(net.minecraft.network.chat.Style style) {
        Style.Builder builder = Style.style();
        net.minecraft.network.chat.TextColor color = style.getColor();
        if (color != null) builder.color(TextColor.color(color.getValue()));
        if (style.isBold()) builder.decoration(TextDecoration.BOLD, true);
        if (style.isItalic()) builder.decoration(TextDecoration.ITALIC, true);
        if (style.isUnderlined()) builder.decoration(TextDecoration.UNDERLINED, true);
        if (style.isStrikethrough()) builder.decoration(TextDecoration.STRIKETHROUGH, true);
        if (style.isObfuscated()) builder.decoration(TextDecoration.OBFUSCATED, true);
        return builder.build();
    }

    /**
     * @return the plain text of the given Minecraft component (translations resolved)
     */
    public static String toPlain(FormattedText component) {
        return component != null ? component.getString() : "";
    }

}
