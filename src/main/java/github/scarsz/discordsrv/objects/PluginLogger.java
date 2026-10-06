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
