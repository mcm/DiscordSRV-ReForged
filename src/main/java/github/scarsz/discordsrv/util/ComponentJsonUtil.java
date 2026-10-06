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
