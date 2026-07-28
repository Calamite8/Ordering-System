package com.larrystapsihan.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Controller for ReportsView.fxml. */
public class ReportsController {

    @FXML private HBox statStrip;
    @FXML private VBox fastMovers;
    @FXML private VBox lowStock;

    private InventoryStore store;

    public void setStore(InventoryStore store) {
        this.store = store;
        refresh();
    }

    public void refresh() {
        statStrip.getChildren().setAll(
                statBox("Tickets Today", String.valueOf(store.totalTicketsToday())),
                statBox("Revenue Today", "\u20B1" + store.totalRevenueToday()),
                statBox("Low-Stock Items", String.valueOf(store.lowStockCount())));
        for (var box : statStrip.getChildren()) {
            HBox.setHgrow(box, Priority.ALWAYS);
        }

        fastMovers.getChildren().clear();
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(store.getSalesCount().entrySet());
        ranked.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed());
        if (ranked.isEmpty()) {
            fastMovers.getChildren().add(emptyNote("No sales recorded yet."));
        } else {
            int idx = 1;
            for (Map.Entry<String, Integer> e : ranked) {
                if (idx > 6) break;
                Dish dish = store.getMenu().stream().filter(m -> m.getId().equals(e.getKey())).findFirst().orElse(null);
                if (dish == null) continue;
                fastMovers.getChildren().add(rankRow(String.format("%02d", idx), dish.getName(), e.getValue() + " sold", false));
                idx++;
            }
        }

        lowStock.getChildren().clear();
        List<Ingredient> low = new ArrayList<>(store.getIngredients());
        low.removeIf(i -> i.getStatus() == Ingredient.Status.FRESH);
        low.sort(Comparator.comparingDouble(i -> i.getQty() / i.getThreshold()));
        if (low.isEmpty()) {
            lowStock.getChildren().add(emptyNote("All ingredients are above threshold."));
        } else {
            for (Ingredient ing : low) {
                String mark = ing.getStatus() == Ingredient.Status.CRITICAL ? "!!" : "!";
                lowStock.getChildren().add(rankRow(mark, ing.getName(),
                        trimZero(ing.getQty()) + " " + ing.getUnit() + " left", true));
            }
        }
    }

    private VBox statBox(String label, String value) {
        Label l = new Label(label);
        l.getStyleClass().add("stat-label");
        Label v = new Label(value);
        v.getStyleClass().add("stat-value");
        VBox box = new VBox(4, l, v);
        box.getStyleClass().add("stat-box");
        return box;
    }

    private HBox rankRow(String num, String name, String value, boolean alert) {
        Label numLabel = new Label(num);
        numLabel.getStyleClass().add(alert ? "alert-num" : "rank-num");
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("rank-name");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("rank-val");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(10, numLabel, nameLabel, spacer, valueLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Label emptyNote(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("log-empty");
        return l;
    }

    private static String trimZero(double value) {
        if (value == Math.floor(value)) return String.valueOf((long) value);
        return String.format("%.2f", value);
    }
}
