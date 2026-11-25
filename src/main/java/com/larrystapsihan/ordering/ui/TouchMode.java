package com.larrystapsihan.ordering.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Node;

/**
 * The Touchscreen Mode setting (Settings page).
 *
 * When on, the root of each window/dialog gets the "touch-mode" style class,
 * and styles.css (section 8) enlarges buttons to finger size; the sidebar
 * also keeps its tab names visible, since a finger can't hover.
 *
 * Use bind(root) on every new window root or DialogPane so it follows the
 * setting. The choice is remembered in ~/LarrysTapsihan/settings.properties.
 */
public final class TouchMode {

    public static final String STYLE_CLASS = "touch-mode";
    private static final Path FILE = Path.of(System.getProperty("user.home"), "LarrysTapsihan", "settings.properties");
    private static final BooleanProperty ENABLED = new SimpleBooleanProperty(load());

    static {
        ENABLED.addListener((obs, was, is) -> save(is));
    }

    private TouchMode() { }

    public static BooleanProperty enabledProperty() { return ENABLED; }
    public static boolean isEnabled() { return ENABLED.get(); }
    public static void setEnabled(boolean on) { ENABLED.set(on); }

    /** Keeps the "touch-mode" style class on this node in step with the setting. */
    public static void bind(Node root) {
        apply(root, ENABLED.get());
        ChangeListener<Boolean> listener = (obs, was, is) -> apply(root, is);
        root.getProperties().put("touchModeListener", listener);      // keeps the weak listener alive
        ENABLED.addListener(new WeakChangeListener<>(listener));
    }

    private static void apply(Node root, boolean on) {
        root.getStyleClass().remove(STYLE_CLASS);
        if (on) root.getStyleClass().add(STYLE_CLASS);
    }

    private static boolean load() {
        if (!Files.exists(FILE)) return false;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
        } catch (IOException e) {
            return false;
        }
        return Boolean.parseBoolean(p.getProperty("touchMode", "false"));
    }

    private static void save(boolean on) {
        Properties p = new Properties();
        p.setProperty("touchMode", String.valueOf(on));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Larry's Tapsihan display settings");
            }
        } catch (IOException e) {
            System.err.println("Could not save settings: " + e.getMessage());
        }
    }
}
