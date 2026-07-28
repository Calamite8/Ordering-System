package com.larrystapsihan.inventory;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** "Stock In" view: mirrors the .stockin-form section of the prototype. */
public class StockInView extends BorderPane {

    private final InventoryStore store;
    private final Runnable onDataChanged;

    private final ComboBox<IngredientChoice> ingredientBox = new ComboBox<>();
    private final TextField qtyField = new TextField();
    private final Label toast = new Label(" ");
    private final VBox logRows = new VBox(0);

    public StockInView(InventoryStore store, Runnable onDataChanged) {
        this.store = store;
        this.onDataChanged = onDataChanged;
        getStyleClass().add("root-bg");
        setPadding(new Insets(18));

        Label title = new Label("Stock In");
        title.getStyleClass().add("section-title");
        setTop(title);
        BorderPane.setMargin(title, new Insets(0, 0, 14, 0));

        VBox form = buildForm();
        Region gap = new Region();
        gap.setMinWidth(24);
        VBox logPanel = buildLogPanel();

        HBox content = new HBox(form, gap, logPanel);
        HBox.setHgrow(logPanel, Priority.ALWAYS);
        setCenter(content);

        refresh();
    }

    private VBox buildForm() {
        for (Ingredient ing : store.getIngredients()) {
            ingredientBox.getItems().add(new IngredientChoice(ing.getId(), ing.getName() + " (" + ing.getUnit() + ")"));
        }
        ingredientBox.getSelectionModel().selectFirst();
        ingredientBox.getStyleClass().add("field-input");
        ingredientBox.setMaxWidth(Double.MAX_VALUE);

        qtyField.getStyleClass().add("field-input");
        qtyField.setPromptText("e.g. 5");

        Button addBtn = new Button("Add Stock");
        addBtn.getStyleClass().add("add-btn");
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setOnAction(e -> handleAddStock());

        toast.getStyleClass().add("toast-ok");

        Label ingredientLabel = fieldLabel("Ingredient");
        Label qtyLabel = fieldLabel("Quantity Delivered");

        VBox form = new VBox(6, ingredientLabel, ingredientBox, qtyLabel, qtyField, addBtn, toast);
        form.getStyleClass().add("stockin-form");
        form.setPrefWidth(320);
        form.setMaxWidth(320);
        VBox.setMargin(qtyLabel, new Insets(10, 0, 0, 0));
        VBox.setMargin(addBtn, new Insets(6, 0, 0, 0));
        return form;
    }

    private VBox buildLogPanel() {
        Label logTitle = new Label("Recent Deliveries");
        logTitle.getStyleClass().add("log-panel-title");
        VBox logPanel = new VBox(8, logTitle, logRows);
        logPanel.getStyleClass().add("log-panel");
        return logPanel;
    }

    private Label fieldLabel(String text) {
        Label l = new Label(text.toUpperCase());
        l.getStyleClass().add("field-label");
        return l;
    }

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
            onDataChanged.run();
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
