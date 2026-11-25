package com.larrystapsihan.ordering.ui.screens;

import com.larrystapsihan.ordering.model.Order;
import com.larrystapsihan.ordering.model.OrderItem;
import com.larrystapsihan.ordering.service.AdminService;
import com.larrystapsihan.ordering.service.OrderService;
import com.larrystapsihan.ordering.ui.TouchMode;
import java.util.List;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/**
 * Receipts page: one receipt card per order (newest first), with
 * "Clear Receipts" (admin only) to wipe all order history and start a new shift.
 */
public class ReceiptsScreen implements Screen {

    private final OrderService orderService;
    private final FlowPane receiptGrid = new FlowPane(16, 16);
    private final BorderPane view;

    public ReceiptsScreen(OrderService orderService, AdminService admin) {
        this.orderService = orderService;

        Button clearReceipts = new Button("Clear Receipts");
        clearReceipts.getStyleClass().add("void-link");
        clearReceipts.setOnAction(e -> confirmAndClear());
        ScreenLayout.adminOnly(clearReceipts, admin);

        view = ScreenLayout.page("Receipts",
                "Finalized orders for the day, in receipt form.",
                clearReceipts, ScreenLayout.transparentScroll(receiptGrid));
    }

    @Override
    public Node getView() { return view; }

    @Override
    public void refresh() {
        receiptGrid.getChildren().clear();
        List<Order> all = orderService.getOrders();
        if (all.isEmpty()) {
            Label empty = new Label("No receipts yet.");
            empty.getStyleClass().add("log-empty");
            receiptGrid.getChildren().add(empty);
            return;
        }
        for (Order o : all) receiptGrid.getChildren().add(buildReceipt(o));
    }

    private void confirmAndClear() {
        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle("Clear Receipts");
        confirm.setHeaderText(null);
        confirm.getDialogPane().getStylesheets().setAll(view.getScene().getStylesheets());
        TouchMode.bind(confirm.getDialogPane());
        confirm.setContentText("This clears all order history (Kitchen Queue, Receipts, and Shift "
                + "Summary) and starts a new shift. This can't be undone. Continue?");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            orderService.clearHistory();
        }
    }

    private VBox buildReceipt(Order o) {
        Label header = new Label("#" + o.getId() + (o.isVoided() ? " (VOID)" : ""));
        header.getStyleClass().add("ticket-name");
        Label time = new Label(o.getType() + " · " + o.getTime());
        time.getStyleClass().add("ticket-uses");

        VBox items = new VBox(2);
        for (OrderItem it : o.getItems()) {
            Label left = new Label(it.qty() + "× " + it.name());
            left.getStyleClass().add("ticket-uses");
            Label right = new Label("₱" + it.lineTotal());
            right.getStyleClass().add("ticket-uses");
            items.getChildren().add(ScreenLayout.leftRight(left, right));
        }

        Label total = new Label("Total — ₱" + o.getTotal());
        total.getStyleClass().add("ticket-price");
        Label pay = new Label("Paid via " + o.getPayMethod());
        pay.getStyleClass().add("ticket-uses");

        VBox ticket = new VBox(6, header, time, items, total, pay);
        ticket.getStyleClass().add("ticket");
        ticket.setPrefWidth(220);
        return ticket;
    }
}
