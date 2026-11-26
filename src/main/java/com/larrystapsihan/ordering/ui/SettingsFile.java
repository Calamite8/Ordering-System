package com.larrystapsihan.ordering.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The small display settings file, ~/LarrysTapsihan/settings.properties.
 * Each setting is one key; setting one key keeps the others.
 */
final class SettingsFile {

    private static final Path FILE = Path.of(System.getProperty("user.home"), "LarrysTapsihan", "settings.properties");

    private SettingsFile() { }

    static String get(String key, String fallback) {
        return read().getProperty(key, fallback);
    }

    static void set(String key, String value) {
        Properties p = read();
        p.setProperty(key, value);
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Larry's Tapsihan display settings");
            }
        } catch (IOException e) {
            System.err.println("Could not save settings: " + e.getMessage());
        }
    }

    private static Properties read() {
        Properties p = new Properties();
        if (Files.exists(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                p.load(in);
            } catch (IOException ignored) { }
        }
        return p;
    }
}
