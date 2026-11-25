package com.larrystapsihan.ordering;

import com.larrystapsihan.inventory.InventoryStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Entry point for the Order System UI. Shows a login screen first
 * (LoginController); once "signed in" it fades out and swaps to the Order
 * System itself (New Order / Kitchen Queue / Receipts / Shift Summary).
 * Signing out from the Order System's Settings panel fades back to a fresh
 * login screen the same way, in reverse.
 *
 * This is a standalone, self-contained app - it creates its own in-memory
 * InventoryStore. Order history persistence is optional: if a MySQL server
 * matching DB_URL/DB_USER/DB_PASSWORD below is reachable, orders are saved
 * there and reloaded on startup (see OrderRepository). If it isn't, the
 * app falls back to in-memory-only order tracking so it still runs without
 * a database set up.
 */
public class OrderMain extends Application {

    // Adjust these to match your MySQL setup. createDatabaseIfNotExist=true
    // means the database itself doesn't need to exist yet - OrderRepository
    // creates its tables on first connect either way.
    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/larrys_tapsihan?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";

    // Board dark-olive color from styles.css (.root's -fx-board). Scene.fill
    // defaults to white, which is what caused the white flash mid-fade: as
    // a root node's opacity drops during a FadeTransition, whatever color
    // the Scene itself is painted with shows through behind it. Matching
    // the Scene's own fill to the app's background eliminates that flash
    // regardless of which root is currently fading.
    private static final Color BOARD_COLOR = Color.web("#2B332C");

    private Scene scene;
    private OrderRepository repository;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Larry's Tapsihan \u2026 Order System");
        stage.setMinWidth(860);
        stage.setMinHeight(600);

        repository = connectRepository();

        try {
            Parent loginRoot = buildLoginRoot();
            scene = new Scene(loginRoot, 1080, 720);
            scene.setFill(BOARD_COLOR);
            scene.getStylesheets().add(
                    OrderMain.class.getResource("/com/larrystapsihan/inventory/styles.css").toExternalForm());

            stage.setScene(scene);
            stage.setMaximized(true);
            stage.show();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Parent buildLoginRoot() throws IOException {
        FXMLLoader loader = new FXMLLoader(
                OrderMain.class.getResource("/com/larrystapsihan/ordering/LoginView.fxml"));
        Parent root = loader.load();
        LoginController controller = loader.getController();
        controller.setOnLoginSuccess(() -> fadeTo(root, this::buildOrderRoot));
        return root;
    }

    private Parent buildOrderRoot() {
        InventoryStore store = new InventoryStore();
        try {
            FXMLLoader loader = new FXMLLoader(
                    OrderMain.class.getResource("/com/larrystapsihan/ordering/OrderView.fxml"));
            Parent root = loader.load();
            OrderController controller = loader.getController();
            controller.setRepository(repository);
            controller.setOnSignOut(() -> fadeTo(root, this::buildLoginRootUnchecked));
            controller.setStore(store);
            store.addChangeListener(controller::refresh);
            return root;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Parent buildLoginRootUnchecked() {
        try {
            return buildLoginRoot();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Fades currentRoot out, builds the next screen, then fades it in. */
    private void fadeTo(Parent currentRoot, java.util.function.Supplier<Parent> nextRootBuilder) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(400), currentRoot);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> {
            Parent nextRoot = nextRootBuilder.get();
            nextRoot.setOpacity(0);
            scene.setRoot(nextRoot);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(350), nextRoot);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.play();
        });
        fadeOut.play();
    }

    /** Tries to connect to MySQL; returns null (no persistence) if it's not reachable. */
    private OrderRepository connectRepository() {
        try {
            return new OrderRepository(DB_URL, DB_USER, DB_PASSWORD);
        } catch (SQLException e) {
            System.err.println("Order history database not reachable - continuing "
                    + "with in-memory order tracking only. (" + e.getMessage() + ")");
            return null;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
