package com.larrystapsihan.ordering.ui;

import com.larrystapsihan.ordering.ui.screens.CustomerScreen;
import java.util.List;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The window that holds the Customer Screen. With two displays it opens
 * full-screen on the second one; with one display it opens as a normal
 * window. It is opened and closed from Settings (and can open itself at
 * startup - the choice is remembered in settings.properties).
 */
public final class CustomerWindow {

    private static final BooleanProperty AUTO_OPEN =
            new SimpleBooleanProperty(Boolean.parseBoolean(SettingsFile.get("customerScreenAtStartup", "false")));

    static {
        AUTO_OPEN.addListener((obs, was, is) -> SettingsFile.set("customerScreenAtStartup", String.valueOf(is)));
    }

    /** Whether the Customer Screen opens by itself when the app starts. */
    public static BooleanProperty autoOpenProperty() { return AUTO_OPEN; }

    private final CustomerScreen content;
    private final BooleanProperty open = new SimpleBooleanProperty(false);
    private Stage stage;

    public CustomerWindow(CustomerScreen content) {
        this.content = content;
    }

    /** True while the window is showing. */
    public BooleanProperty openProperty() { return open; }

    /** How many displays this computer has (Settings shows it). */
    public static int displayCount() { return javafx.stage.Screen.getScreens().size(); }

    public void open(Window owner) {
        if (stage != null && stage.isShowing()) { stage.toFront(); return; }
        content.showOrdering();   // always starts on the ordering view
        stage = new Stage();
        stage.setTitle("Larry's Tapsihan - Customer Screen");
        if (owner != null) stage.initOwner(owner);   // closes together with the main window
        Scene scene = new Scene((javafx.scene.Parent) content.getView(), 1280, 720);
        // same look as the main window
        if (owner != null && owner.getScene() != null) scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        scene.setFill(javafx.scene.paint.Color.web("#2B332C"));
        stage.setScene(scene);
        stage.setOnHidden(e -> {
            // free the screen's root so a later open() can put it in a new window
            stage.getScene().setRoot(new javafx.scene.layout.Pane());
            open.set(false);
        });

        List<javafx.stage.Screen> screens = javafx.stage.Screen.getScreens();
        javafx.stage.Screen primary = javafx.stage.Screen.getPrimary();
        javafx.stage.Screen second = screens.stream().filter(s -> !s.equals(primary)).findFirst().orElse(null);
        if (second != null) {
            // second display: fill it, with no way for a customer to exit full screen
            Rectangle2D b = second.getBounds();
            stage.setX(b.getMinX());
            stage.setY(b.getMinY());
            stage.setWidth(b.getWidth());
            stage.setHeight(b.getHeight());
            stage.setFullScreenExitHint("");
            stage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);
            stage.show();
            stage.setFullScreen(true);
        } else {
            stage.show();
        }
        open.set(true);
    }

    public void close() {
        if (stage != null) stage.close();
    }

    public boolean isOpen() { return stage != null && stage.isShowing(); }
}
