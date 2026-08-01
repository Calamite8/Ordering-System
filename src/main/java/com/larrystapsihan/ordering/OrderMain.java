package com.larrystapsihan.ordering;

import com.larrystapsihan.inventory.InventoryStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Entry point for the Order System UI - a standalone JavaFX application
 * with its own window, separate from the Inventory System app (see
 * com.larrystapsihan.inventory.Main in the app module).
 *
 * Each app currently creates its own in-memory InventoryStore, so the two
 * windows don't share live data yet - that's expected until the roadmap's
 * Database (MySQL) phase gives both a common backing store to read/write.
 */
public class OrderMain extends Application {

    @Override
    public void start(Stage stage) {
        InventoryStore store = new InventoryStore();

        try {
            FXMLLoader loader = new FXMLLoader(
                    OrderMain.class.getResource("/com/larrystapsihan/ordering/OrderView.fxml"));
            Parent root = loader.load();
            OrderController controller = loader.getController();
            controller.setStore(store);
            store.addChangeListener(controller::refresh);

            Scene scene = new Scene(root, 1080, 720);
            scene.getStylesheets().add(
                    OrderMain.class.getResource("/com/larrystapsihan/inventory/styles.css").toExternalForm());

            stage.setTitle("Larry's Tapsihan \u2014 Order System");
            stage.setScene(scene);
            stage.setMinWidth(860);
            stage.setMinHeight(600);
            stage.show();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
