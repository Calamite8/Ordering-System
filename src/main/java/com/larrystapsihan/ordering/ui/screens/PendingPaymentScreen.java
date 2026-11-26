package com.larrystapsihan.ordering.ui.screens;

import com.larrystapsihan.ordering.model.Order;
import com.larrystapsihan.ordering.model.OrderItem;
import com.larrystapsihan.ordering.service.OrderService;
import com.larrystapsihan.ordering.ui.OnScreenKeyboard;
import com.larrystapsihan.ordering.ui.ReceiptPrinter;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Pending Payment page (cashier): the orders customers placed on the
 * Customer Screen that are still waiting for cash. Pick one, enter the cash
 * received, see the change, and confirm - the order then goes to the Kitchen
 * Queue. Cancel throws away an order the customer walked away from.
 */
public class PendingPaymentScreen implements Screen {

    private static final int[] QUICK_AMOUNTS = {50, 100, 200, 500, 1000};

    private final OrderService orderService;
    private final VBox list = new VBox(8);
    private final VBox detail = new VBox(12);
    private final ToggleGroup group = new ToggleGroup();
    private final TextField cashField = new TextField();
    private final Label changeLabel = new Label();
    private final Button confirm = new Button("Confirm Payment");
    private final Label notice = new Label();
    private final Button printLast = new Button();
    private final OnScreenKeyboard keyboard = new OnScreenKeyboard();
    private final BorderPane view;

    private Integer selectedId;      // order being paid, or null
    private Order paidOrder;         // last order paid here (for the Print button)

    public PendingPaymentScreen(OrderService orderService) {
        this.orderService = orderService;

        keyboard.attachNumber(cashField);
        cashField.setPromptText("Cash received");
        cashField.getStyleClass().add("field-input");
        cashField.textProperty().addListener((obs, was, now) -> {
            // digits only
            if (!now.matches("\\d*")) cashField.setText(now.replaceAll("\\D", ""));
            else updateChange();
        });

        notice.getStyleClass().add("toast-ok");
        notice.setWrapText(true);
        notice.setVisible(false);
        notice.setManaged(false);
        printLast.getStyleClass().add("punch-btn");
        printLast.setVisible(false);
        printLast.setManaged(false);
        printLast.setOnAction(e -> ReceiptPrinter.print(paidOrder, printLast.getScene().getWindow()));

        list.setPrefWidth(300);
        list.setMinWidth(300);
        detail.getStyleClass().add("log-panel");
        detail.setPadding(new Insets(16));
        HBox.setHgrow(detail, Priority.ALWAYS);
        VBox listPanel = new VBox(8, titled("Waiting for Payment"), list);
        listPanel.setMinWidth(300);
        VBox right = new VBox(12, notice, printLast, detail);
        HBox.setHgrow(right, Priority.ALWAYS);
        HBox body = new HBox(18, listPanel, right, keyboard);
        body.setAlignment(Pos.TOP_LEFT);

        view = ScreenLayout.page("Pending Payment",
                "Orders placed on the Customer Screen. Take the cash, confirm, and the order goes to the kitchen.",
                null, ScreenLayout.transparentScroll(body));
    }

    @Override
    public Node getView() { return view; }

    @Override
    public void refresh() {
        List<Order> pending = orderService.getPending();
        if (selectedId != null && pending.stream().noneMatch(o -> o.getId() == selectedId)) {
            selectedId = null;            // paid or cancelled elsewhere
            cashField.clear();
        }
        if (selectedId == null && !pending.isEmpty()) selectedId = pending.get(0).getId();

        list.getChildren().clear();
        group.getToggles().clear();
        if (pending.isEmpty()) {
            Label none = new Label("No orders waiting for payment.");
            none.getStyleClass().add("log-empty");
            list.getChildren().add(none);
        }
        for (Order o : pending) {
            ToggleButton row = new ToggleButton("#" + o.getId() + "  ·  " + o.getType()
                    + (o.getTable() != null && !o.getTable().isBlank() ? " T" + o.getTable() : "")
                    + "  ·  ₱" + o.getTotal());
            row.getStyleClass().add("pending-row");
            row.setMaxWidth(Double.MAX_VALUE);
            row.setToggleGroup(group);
            row.setSelected(selectedId != null && o.getId() == selectedId);
            row.setOnAction(e -> {
                selectedId = o.getId();
                cashField.clear();
                refresh();
            });
            list.getChildren().add(row);
        }
        buildDetail(pending);
    }

    private void buildDetail(List<Order> pending) {
        detail.getChildren().clear();
        Order o = pending.stream().filter(x -> selectedId != null && x.getId() == selectedId).findFirst().orElse(null);
        if (o == null) {
            Label none = new Label("Select an order on the left to take payment.");
            none.getStyleClass().add("log-empty");
            detail.getChildren().add(none);
            return;
        }
        Label head = new Label("Order #" + o.getId() + " · " + o.getType()
                + (o.getTable() != null && !o.getTable().isBlank() ? " · Table " + o.getTable() : ""));
        head.getStyleClass().add("log-panel-title");
        VBox items = new VBox(3);
        for (OrderItem it : o.getItems()) {
            Label line = ScreenLayoutLabel.of(it.qty() + " × " + it.name() + "   ₱" + it.lineTotal());
            items.getChildren().add(line);
        }
        Label totalLbl = new Label("TOTAL");
        totalLbl.getStyleClass().add("field-label");
        Label total = new Label("₱" + o.getTotal());
        total.getStyleClass().add("pay-big");
        HBox totalRow = ScreenLayout.leftRight(totalLbl, total);

        Label cashLbl = new Label("CASH RECEIVED (₱)");
        cashLbl.getStyleClass().add("field-label");
        FlowPane quick = new FlowPane(6, 6);
        Button exact = new Button("Exact");
        exact.getStyleClass().add("toggle-btn");
        exact.setOnAction(e -> cashField.setText(String.valueOf(o.getTotal())));
        quick.getChildren().add(exact);
        for (int amt : QUICK_AMOUNTS) {
            Button b = new Button("₱" + amt);
            b.getStyleClass().add("toggle-btn");
            b.setOnAction(e -> cashField.setText(String.valueOf(amt)));
            quick.getChildren().add(b);
        }

        confirm.getStyleClass().setAll("button", "punch-btn");
        confirm.setMaxWidth(Double.MAX_VALUE);
        confirm.setOnAction(e -> {
            int paid = parsePaid();
            if (orderService.confirmPayment(o.getId(), paid)) {
                paidOrder = o;
                int change = Math.max(0, paid - o.getTotal());
                notice.setText("Order #" + o.getId() + " paid. Change: ₱" + change + ". Sent to the kitchen.");
                notice.setVisible(true);
                notice.setManaged(true);
                printLast.setText("Print Receipt #" + o.getId());
                printLast.setVisible(true);
                printLast.setManaged(true);
                selectedId = null;
                cashField.clear();
            }
        });
        Button cancel = new Button("Cancel Order");
        cancel.getStyleClass().add("void-link");
        cancel.setOnAction(e -> {
            orderService.cancelPending(o.getId());
            selectedId = null;
            cashField.clear();
        });

        detail.getChildren().addAll(head, items, totalRow, cashLbl, cashField, quick, changeLabel, confirm, cancel);
        updateChange();
    }

    private int parsePaid() {
        try { return Integer.parseInt(cashField.getText().trim()); } catch (NumberFormatException e) { return 0; }
    }

    /** Shows the change, or how much is still missing, and enables Confirm only when enough cash is entered. */
    private void updateChange() {
        Order o = orderService.getPending().stream()
                .filter(x -> selectedId != null && x.getId() == selectedId).findFirst().orElse(null);
        if (o == null) return;
        int paid = parsePaid();
        boolean enough = paid >= o.getTotal();
        confirm.setDisable(!enough);
        changeLabel.getStyleClass().setAll(enough ? "change-ok" : "change-short");
        changeLabel.setText(paid == 0 ? "Enter the cash received."
                : enough ? "Change: ₱" + (paid - o.getTotal()) : "Short by ₱" + (o.getTotal() - paid));
    }

    private Label titled(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("log-panel-title");
        return l;
    }

    /** Tiny helper so the item lines share one style. */
    private static final class ScreenLayoutLabel {
        static Label of(String text) {
            Label l = new Label(text);
            l.getStyleClass().add("rank-name");
            return l;
        }
    }
}
