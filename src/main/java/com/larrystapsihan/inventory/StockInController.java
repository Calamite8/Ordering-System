package com.larrystapsihan.inventory;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Controller for StockInView.fxml. Field names below are matched to
 * fx:id attributes in the FXML by FXMLLoader - don't rename one without
 * the other.
 */
public class StockInController {

    @FXML private ComboBox<IngredientChoice> ingredientBox;
    @FXML private TextField qtyField;
    @FXML private Label toast;
    @FXML private VBox logRows;

    private InventoryStore store;

    /** Called automatically by FXMLLoader once @FXML fields are injected. */
    @FXML
    private void initialize() {
        // Nothing needs the store yet - see setStore(), called by MainView after loading.
    }

    /** Wires this controller up to the shared data store. Call once, right after loading the FXML. */
    public void setStore(InventoryStore store) {
        this.store = store;
        ingredientBox.getItems().clear();
        for (Ingredient ing : store.getIngredients()) {
            ingredientBox.getItems().add(new IngredientChoice(ing.getId(), ing.getName() + " (" + ing.getUnit() + ")"));
        }
        ingredientBox.getSelectionModel().selectFirst();
        refresh();
    }

    @FXML
    private void handleAddStock() {
        double qty;
        try {
            qty = Double.parseDouble(qtyField.getText().trim());
        } catch (NumberFormatException ex) {
            qty = -1;
        }
        if (qty <= 0) {
            toast.getStyleClass().setAll("toast-error");
            toast.setText("Enter a quantity greater than zero.");
            return;
        }
        IngredientChoice choice = ingredientBox.getSelectionModel().getSelectedItem();
        if (choice == null) return;
        Ingredient ing = store.ingredientById(choice.id());
        boolean ok = store.addStock(choice.id(), qty);
        if (ok && ing != null) {
            toast.getStyleClass().setAll("toast-ok");
            toast.setText("Added " + trimZero(qty) + " " + ing.getUnit() + " to " + ing.getName() + ".");
            qtyField.clear();
            // store.addStock() already notified every registered change-listener
            // (MainView's refreshAll), which will call this controller's refresh().
        }
    }

    public void refresh() {
        logRows.getChildren().clear();
        if (store.getStockInLog().isEmpty()) {
            Label empty = new Label("No deliveries logged yet.");
            empty.getStyleClass().add("log-empty");
            logRows.getChildren().add(empty);
        } else {
            int shown = 0;
            for (StockInRecord s : store.getStockInLog()) {
                if (shown++ >= 12) break;
                logRows.getChildren().add(logRow(s));
            }
        }
    }

    private HBox logRow(StockInRecord s) {
        Label left = new Label(s.name());
        left.getStyleClass().add("log-row-label");
        Label right = new Label("+" + trimZero(s.qty()) + " " + s.unit() + " \u00B7 " + s.time());
        right.getStyleClass().add("log-row-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(left, spacer, right);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));
        return row;
    }

    private static String trimZero(double value) {
        if (value == Math.floor(value)) return String.valueOf((long) value);
        return String.format("%.2f", value);
    }

    /** Wrapper so the combo box shows a friendly label while keeping the ingredient id. */
    private record IngredientChoice(String id, String label) {
        @Override public String toString() { return label; }
    }
}
