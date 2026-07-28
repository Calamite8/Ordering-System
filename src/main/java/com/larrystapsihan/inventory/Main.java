package com.larrystapsihan.inventory;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Entry point for the Inventory System UI (JavaFX, in-memory data).
 * This is the UI-only phase of the roadmap: no database wiring yet.
 */
public class Main extends Application {

    @Override
    public void start(Stage stage) {
        InventoryStore store = new InventoryStore();
        MainView root = new MainView(store);

        Scene scene = new Scene(root, 1080, 720);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

        stage.setTitle("Larry's Tapsihan \u2014 Inventory System");
        stage.setScene(scene);
        stage.setMinWidth(860);
        stage.setMinHeight(600);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
