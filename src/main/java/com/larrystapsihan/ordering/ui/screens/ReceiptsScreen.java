package com.larrystapsihan.ordering.ui.screens;

import com.larrystapsihan.ordering.model.Order;
import com.larrystapsihan.ordering.model.OrderItem;
import com.larrystapsihan.ordering.service.AdminService;
import com.larrystapsihan.ordering.service.OrderService;
import com.larrystapsihan.ordering.ui.ReceiptPrinter;
import com.larrystapsihan.ordering.ui.TouchMode;
import java.util.List;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/**
 * Receipts page: one receipt card per order (newest first). Every card has a
 * Print button; the admin also gets "Void Order" on each card and
 * "Clear Receipts" to wipe the order history and start a new shift.
 */
public class ReceiptsScreen implements Screen {

    private final OrderService orderService;
    private final FlowPane receiptGrid = new FlowPane(16, 16);
    private final AdminService admin;
    private final BorderPane view;

    public ReceiptsScreen(OrderService orderService, AdminService admin) {
        this.orderService = orderService;
        this.admin = admin;

        Button clearReceipts = new Button("Clear Receipts");
        clearReceipts.getStyleClass().add("void-link");
        clearReceipts.setOnAction(e -> confirmAndClear());
        ScreenLayout.adminOnly(clearReceipts, admin);

        view = ScreenLayout.page("Receipts",
                "Finalized orders, newest first. Press Print to print a receipt.",
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
        confirm.setContentText("This clears all order history (Kitchen Queue and Receipts) and starts "
                + "a new shift. The menu is kept. This can't be undone. Continue?");
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

        Button print = new Button("Print");
        print.getStyleClass().add("punch-btn");
        print.setOnAction(e -> ReceiptPrinter.print(o, view.getScene().getWindow()));
        HBox actions = new HBox(8, print);
        if (!o.isVoided()) {
            Button voidBtn = new Button("Void Order");
            voidBtn.getStyleClass().add("void-link");
            voidBtn.setOnAction(e -> askVoidReason(o));
            ScreenLayout.adminOnly(voidBtn, admin);
            actions.getChildren().add(voidBtn);
        }

        VBox ticket = new VBox(6, header, time, items, total, pay, actions);
        ticket.getStyleClass().add("ticket");
        ticket.setPrefWidth(220);
        return ticket;
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
