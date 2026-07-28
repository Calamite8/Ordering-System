package com.larrystapsihan.inventory;

import java.util.Map;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Controller for SaleView.fxml. */
public class SaleController {

    @FXML private FlowPane ticketGrid;
    @FXML private VBox logRows;

    private InventoryStore store;

    public void setStore(InventoryStore store) {
        this.store = store;
        refresh();
    }

    public void refresh() {
        ticketGrid.getChildren().clear();
        for (Dish dish : store.getMenu()) {
            ticketGrid.getChildren().add(buildTicket(dish));
        }

        logRows.getChildren().clear();
        if (store.getSalesLog().isEmpty()) {
            Label empty = new Label("No tickets punched yet.");
            empty.getStyleClass().add("log-empty");
            logRows.getChildren().add(empty);
        } else {
            int shown = 0;
            for (SaleRecord s : store.getSalesLog()) {
                if (shown++ >= 12) break;
                logRows.getChildren().add(logRow(s.name(), s.time()));
            }
        }
    }

    private VBox buildTicket(Dish dish) {
        boolean ok = store.canFulfill(dish);

        Label name = new Label(dish.getName());
        name.getStyleClass().add("ticket-name");
        Label price = new Label("\u20B1" + dish.getPrice());
        price.getStyleClass().add("ticket-price");
        Label uses = new Label(recipeText(dish));
        uses.getStyleClass().add("ticket-uses");
        uses.setWrapText(true);

        Button punch = new Button(ok ? "Punch Ticket" : "Out of Stock");
        punch.getStyleClass().add("punch-btn");
        punch.setMaxWidth(Double.MAX_VALUE);
        punch.setDisable(!ok);
        punch.setOnAction(e -> store.punchTicket(dish.getId()));

        VBox ticket = new VBox(6, name, price, uses, punch);
        ticket.getStyleClass().add("ticket");
        ticket.setPrefWidth(200);
        return ticket;
    }

    private String recipeText(Dish dish) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> e : dish.getRecipe().entrySet()) {
            Ingredient ing = store.ingredientById(e.getKey());
            if (ing == null) continue;
            if (sb.length() > 0) sb.append(" \u00B7 ");
            sb.append(ing.getName()).append(' ').append(trimZero(e.getValue())).append(ing.getUnit());
        }
        return sb.toString();
    }

    private HBox logRow(String name, String time) {
        Label left = new Label(name);
        left.getStyleClass().add("log-row-label");
        Label right = new Label(time);
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
}
