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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the source tree for imports that shouldn't be there
 */
public class SourceImportsTest {

    private static final Path SOURCES = Paths.get("src/main/java");
    private static final String NEOFORGE_PACKAGE = "github/scarsz/discordsrv/neoforge/";

    private static List<Path> sources() throws IOException {
        assertTrue(Files.isDirectory(SOURCES), "Run the tests from the project directory");
        try (Stream<Path> stream = Files.walk(SOURCES)) {
            return stream.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }

    /**
     * org.apache.commons.lang3.* should be used instead of org.apache.commons.lang.*
     */
    @Test
    public void correctCommonsLangImport() throws IOException {
        for (Path file : sources()) {
            String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            assertFalse(source.contains("\nimport org.apache.commons.lang."), "File " + file + " uses illegal import for org.apache.commons.lang");
        }
    }

    /**
     * Only the neoforge package may use Minecraft/NeoForge classes, the rest of DiscordSRV is platform independent.
     * Nothing may use Bukkit anymore.
     */
    @Test
    public void coreIsPlatformIndependent() throws IOException {
        for (Path file : sources()) {
            String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String relative = SOURCES.relativize(file).toString().replace('\\', '/');
            assertFalse(source.contains("import org.bukkit."), "File " + relative + " uses Bukkit");
            if (relative.startsWith(NEOFORGE_PACKAGE)) continue;
            for (String forbidden : new String[] {"import net.minecraft.", "import net.neoforged.", "import com.mojang."}) {
                assertFalse(source.contains(forbidden), "File " + relative + " is part of the platform independent core but contains \"" + forbidden + "\"");
            }
        }
    }

}
