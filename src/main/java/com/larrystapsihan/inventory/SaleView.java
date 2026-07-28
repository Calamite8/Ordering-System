package com.larrystapsihan.inventory;

import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** "Record a Sale" view: mirrors the .ticket-grid / .log-panel section of the prototype. */
public class SaleView extends BorderPane {

    private final InventoryStore store;
    private final Runnable onDataChanged;
    private final FlowPane ticketGrid = new FlowPane(20, 20);
    private final VBox logRows = new VBox(0);

    public SaleView(InventoryStore store, Runnable onDataChanged) {
        this.store = store;
        this.onDataChanged = onDataChanged;
        getStyleClass().add("root-bg");
        setPadding(new Insets(18));

        Label title = new Label("Record a Sale");
        title.getStyleClass().add("section-title");
        setTop(title);
        BorderPane.setMargin(title, new Insets(0, 0, 14, 0));

        ticketGrid.setPrefWrapLength(900);
        ScrollPane ticketScroll = new ScrollPane(ticketGrid);
        ticketScroll.setFitToWidth(true);
        ticketScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        setCenter(ticketScroll);

        Label logTitle = new Label("Recently Punched");
        logTitle.getStyleClass().add("log-panel-title");
        VBox logPanel = new VBox(8, logTitle, logRows);
        logPanel.getStyleClass().add("log-panel");
        BorderPane.setMargin(logPanel, new Insets(14, 0, 0, 0));
        setBottom(logPanel);

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
        punch.setOnAction(e -> {
            if (store.punchTicket(dish.getId())) {
                onDataChanged.run();
            }
        });

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
