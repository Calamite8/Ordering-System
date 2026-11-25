package com.larrystapsihan.ordering.ui.screens;

import com.larrystapsihan.ordering.model.Order;
import com.larrystapsihan.ordering.model.VoidRecord;
import com.larrystapsihan.ordering.service.OrderService;
import com.larrystapsihan.ordering.ui.TouchMode;
import java.util.List;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Shift Summary page (admin only - its sidebar tab appears after Settings >
 * Admin Login): four stat boxes (orders, revenue, dine-in/take-out, voided
 * orders), the full order history with a "Void Order" button on each order,
 * and the void log.
 */
public class ShiftSummaryScreen implements Screen {

    private final OrderService orderService;
    private final HBox statRow = new HBox(14);
    private final VBox historyRows = new VBox(0);
    private final VBox voidRows = new VBox(0);
    private final BorderPane view;

    public ShiftSummaryScreen(OrderService orderService) {
        this.orderService = orderService;

        statRow.setPadding(new Insets(4, 0, 14, 0));

        HBox panels = new HBox(16,
                logPanel("Order History", historyRows),
                logPanel("Void Log", voidRows));

        view = ScreenLayout.page("Shift Summary",
                "Totals, order history and voided orders for this shift.",
                null, ScreenLayout.transparentScroll(new VBox(0, statRow, panels)));
    }

    @Override
    public Node getView() { return view; }

    @Override
    public void refresh() {
        List<Order> all = orderService.getOrders();
        List<Order> valid = all.stream().filter(o -> !o.isVoided()).toList();
        long dineIn = valid.stream().filter(o -> o.getType().equals("Dine-in")).count();
        long takeOut = valid.stream().filter(o -> o.getType().equals("Take-out")).count();

        statRow.getChildren().setAll(
                statBox("Orders Today", String.valueOf(valid.size())),
                statBox("Revenue Today", "₱" + orderService.orderRevenueToday()),
                statBox("Dine-in / Take-out", dineIn + " / " + takeOut),
                statBox("Voided Orders", String.valueOf(orderService.getVoidLog().size())));
        for (Node box : statRow.getChildren()) HBox.setHgrow(box, Priority.ALWAYS);

        historyRows.getChildren().clear();
        if (all.isEmpty()) {
            historyRows.getChildren().add(emptyLabel("No orders yet."));
        } else {
            for (Order o : all) historyRows.getChildren().add(buildHistoryRow(o));
        }

        voidRows.getChildren().clear();
        if (orderService.getVoidLog().isEmpty()) {
            voidRows.getChildren().add(emptyLabel("No voided orders."));
        } else {
            for (VoidRecord v : orderService.getVoidLog()) voidRows.getChildren().add(buildVoidRow(v));
        }
    }

    // ---- building blocks ----

    private VBox logPanel(String title, VBox rows) {
        Label heading = new Label(title);
        heading.getStyleClass().add("log-panel-title");
        VBox panel = new VBox(8, heading, rows);
        panel.getStyleClass().add("log-panel");
        HBox.setHgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private Label emptyLabel(String text) {
        Label empty = new Label(text);
        empty.getStyleClass().add("log-empty");
        return empty;
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

    private HBox buildVoidRow(VoidRecord v) {
        Label left = new Label("#" + v.orderId() + " — " + v.reason());
        left.getStyleClass().add("log-row-label");
        Label right = new Label(v.time());
        right.getStyleClass().add("log-row-label");
        HBox row = ScreenLayout.leftRight(left, right);
        row.setPadding(new Insets(4, 0, 4, 0));
        return row;
    }

    private HBox buildHistoryRow(Order o) {
        Label id = new Label("#" + o.getId());
        id.getStyleClass().add("rank-num");

        String meta = o.getType() + (o.getTable() != null ? " · T" + o.getTable() : "")
                + " — ₱" + o.getTotal();
        Label metaLbl = new Label(meta);
        metaLbl.getStyleClass().add("rank-name");
        Label subLbl = new Label(o.getTime() + " · " + o.getPayMethod()
                + (o.isVoided() ? " · VOIDED" : ""));
        subLbl.getStyleClass().add("log-row-label");
        VBox metaBox = new VBox(2, metaLbl, subLbl);
        HBox.setHgrow(metaBox, Priority.ALWAYS);

        HBox row = new HBox(10, id, metaBox);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 0, 6, 0));

        if (!o.isVoided()) {
            Button voidBtn = new Button("Void Order");
            voidBtn.getStyleClass().add("void-link");
            voidBtn.setOnAction(e -> askVoidReason(o));
            row.getChildren().add(voidBtn);
        }
        return row;
    }

    /** Reasons offered when voiding - tap one, no typing needed. Edit this list to change them. */
    private static final List<String> VOID_REASONS = List.of(
            "Customer cancelled", "Wrong order", "Item unavailable", "Duplicate order", "Other");

    private void askVoidReason(Order o) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Void Order #" + o.getId());
        dialog.getDialogPane().getStylesheets().setAll(view.getScene().getStylesheets());
        dialog.getDialogPane().getStyleClass().add("root-bg");
        TouchMode.bind(dialog.getDialogPane());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText("Void Order");

        Label prompt = new Label("Reason for voiding Order #" + o.getId() + "?");
        prompt.getStyleClass().add("field-label");
        VBox choices = new VBox(8, prompt);
        ToggleGroup group = new ToggleGroup();
        for (String reason : VOID_REASONS) {
            ToggleButton b = new ToggleButton(reason);
            b.getStyleClass().add("toggle-btn");
            b.setToggleGroup(group);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setPrefWidth(300);
            choices.getChildren().add(b);
        }
        group.selectToggle(group.getToggles().get(0));
        // keep one option always selected (tapping the selected one must not clear it)
        group.selectedToggleProperty().addListener((obs, was, now) -> { if (now == null) group.selectToggle(was); });
        dialog.getDialogPane().setContent(choices);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            orderService.voidOrder(o.getId(), ((ToggleButton) group.getSelectedToggle()).getText());
        }
    }
}
