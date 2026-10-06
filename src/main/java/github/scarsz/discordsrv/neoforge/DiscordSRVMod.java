package github.scarsz.discordsrv.neoforge;

import github.scarsz.discordsrv.DiscordSRV;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge entrypoint. DiscordSRV is a server-side mod: it is only active on dedicated servers and isn't
 * required on clients.
 */
@Mod(value = DiscordSRVMod.MOD_ID, dist = Dist.DEDICATED_SERVER)
public class DiscordSRVMod {

    public static final String MOD_ID = "discordsrv";

    private static NeoForgePlatform platform;

    public DiscordSRVMod(IEventBus modEventBus, ModContainer container) {
        platform = new NeoForgePlatform(container.getModInfo().getVersion().toString());
        // loads the configuration
        DiscordSRV discordSRV = new DiscordSRV(platform);
        NeoForge.EVENT_BUS.register(new GameEventHandler(discordSRV, platform));
    }

    /**
     * @return the platform, or null if DiscordSRV isn't active (eg. on a client)
     */
    public static NeoForgePlatform getPlatform() {
        return platform;
    }

}
