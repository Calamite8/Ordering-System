package com.larrystapsihan.ordering.app;

import com.larrystapsihan.ordering.data.MenuStore;
import com.larrystapsihan.ordering.data.OrderRepository;
import com.larrystapsihan.ordering.ui.OrderController;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.sql.SQLException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * The JavaFX Application: opens the window (maximized) straight onto the
 * Order System - there is no startup login; admin access is unlocked from
 * Settings. Opens the local data file (DB_FILE below) that holds the menu,
 * orders and admin account.
 */
public class OrderMain extends Application {

    // Everything is saved on this device, in one SQLite file (no server needed).
    private static final Path DB_FILE =
            Path.of(System.getProperty("user.home"), "LarrysTapsihan", "ordering.db");

    // Dark-olive app background (matches .root-bg in styles.css) - the
    // Scene's own fill, so no white shows while the window is created/resized.
    private static final Color BOARD_COLOR = Color.web("#2B332C");

    private Scene scene;
    private OrderRepository repository;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Larry's Tapsihan \u2014 Order System");
        stage.setMinWidth(860);
        stage.setMinHeight(600);

        repository = connectRepository();

        try {
            scene = new Scene(buildOrderRoot(), 1080, 720);
            scene.setFill(BOARD_COLOR);
            scene.getStylesheets().add(
                    OrderMain.class.getResource("/com/larrystapsihan/ordering/ui/styles.css").toExternalForm());

            stage.setScene(scene);
            stage.setMaximized(true);
            stage.show();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Parent buildOrderRoot() throws IOException {
        MenuStore store = new MenuStore(repository);
        FXMLLoader loader = new FXMLLoader(
                OrderMain.class.getResource("/com/larrystapsihan/ordering/ui/OrderView.fxml"));
        Parent root = loader.load();
        OrderController controller = loader.getController();
        controller.setRepository(repository);
        controller.setStore(store);
        store.addChangeListener(controller::refresh);
        return root;
    }

    /** Opens the local data file; returns null (nothing is saved this session) if that fails. */
    private OrderRepository connectRepository() {
        try {
            return new OrderRepository(DB_FILE);
        } catch (SQLException e) {
            System.err.println("Could not open the local data file " + DB_FILE
                    + " - continuing without saving. (" + e.getMessage() + ")");
            return null;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
