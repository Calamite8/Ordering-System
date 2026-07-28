package com.larrystapsihan.inventory;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/** Controller for StockBoardView.fxml. */
public class StockBoardController {

    @FXML private FlowPane grid;

    private InventoryStore store;

    public void setStore(InventoryStore store) {
        this.store = store;
        refresh();
    }

    public void refresh() {
        grid.getChildren().clear();
        for (Ingredient ing : store.getIngredients()) {
            grid.getChildren().add(buildCard(ing));
        }
    }

    private VBox buildCard(Ingredient ing) {
        Circle dot = new Circle(5.5);
        dot.getStyleClass().add(statusStyle(ing.getStatus()));

        Label name = new Label(ing.getName());
        name.getStyleClass().add("card-name");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topRow = new HBox(name, spacer, dot);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label qtyValue = new Label(trimZero(ing.getQty()) + " ");
        Label qtyUnit = new Label(ing.getUnit());
        qtyUnit.getStyleClass().add("qty-unit");
        HBox qtyLine = new HBox(qtyValue, qtyUnit);
        qtyLine.setAlignment(Pos.BASELINE_LEFT);
        qtyValue.getStyleClass().add("qty-line");

        Label threshold = new Label("reorder at " + trimZero(ing.getThreshold()) + " " + ing.getUnit());
        threshold.getStyleClass().add("threshold-line");

        ProgressBar gauge = new ProgressBar(ing.getGaugePercent() / 100.0);
        gauge.getStyleClass().add("gauge-bar");
        gauge.getStyleClass().add("gauge-" + statusStyle(ing.getStatus()).replace("status-", ""));
        gauge.setMaxWidth(Double.MAX_VALUE);
        gauge.setPrefHeight(7);

        VBox card = new VBox(6, topRow, qtyLine, threshold, gauge);
        card.getStyleClass().add("chalk-card");
        card.setPrefWidth(220);
        card.setMinWidth(200);
        return card;
    }

    private String statusStyle(Ingredient.Status status) {
        return switch (status) {
            case FRESH -> "status-fresh";
            case WARN -> "status-warn";
            case CRITICAL -> "status-critical";
        };
    }

    private static String trimZero(double value) {
        if (value == Math.floor(value)) return String.valueOf((long) value);
        return String.format("%.2f", value);
    }
}
