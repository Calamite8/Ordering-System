package com.larrystapsihan.ordering;

import com.larrystapsihan.inventory.Dish;
import com.larrystapsihan.inventory.InventoryStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Controller for OrderView.fxml. Navigation is a custom icon sidebar on the
 * left (New Order / Kitchen Queue / Receipts / Shift Summary, with Settings
 * pinned at the bottom) rather than a JavaFX TabPane - a side-positioned
 * TabPane always rotates its tab labels 90 degrees, which isn't what was
 * wanted here. Each sidebar icon reveals its label on hover via a short
 * fade; clicking swaps the center content pane.
 *
 * The stock deduction underneath a finalized order is NOT reimplemented
 * here - it goes through {@link OrderService#finalizeOrder}, which reuses
 * the same {@link InventoryStore#punchTicket(String)} path "Record a Sale"
 * already uses, so Reports stays correct no matter which tab a sale came
 * from.
 */
public class OrderController {

    @FXML private BorderPane root;

    private InventoryStore store;
    private OrderService orderService;
    private OrderRepository repository; // optional - set before setStore() if persistence is wanted
    private Runnable onSignOut; // optional - set before setStore() to enable the Settings sign-out button

    /** Optional: call before setStore() to persist order history to a database. */
    public void setRepository(OrderRepository repository) {
        this.repository = repository;
    }

    /** Optional: call before setStore() to wire up the Settings panel's sign-out button. */
    public void setOnSignOut(Runnable onSignOut) {
        this.onSignOut = onSignOut;
    }

    // ---- New Order tab state (local to this cashier session; not persisted) ----
    private final Map<String, Integer> cart = new LinkedHashMap<>();
    private String orderType = "Dine-in";
    private String payMethod = "Cash";

    private FlowPane menuGrid;
    private VBox cartList;
    private Label cartTotal;
    private Button finalizeBtn;
    private Label toast;
    private VBox tableFieldBox;
    private TextField tableField;

    private FlowPane kitchenGrid;
    private FlowPane receiptGrid;
    private HBox shiftStats;
    private VBox orderHistory;
    private VBox voidLogRows;

    public void setStore(InventoryStore store) {
        this.store = store;
        this.orderService = new OrderService(store, repository);

        Node newOrderPane = buildNewOrderPane();
        Node kitchenPane = buildKitchenPane();
        Node receiptsPane = buildReceiptsPane();
        Node shiftPane = buildShiftPane();
        Node settingsPane = buildSettingsPane();

        root.setLeft(buildSidebar(newOrderPane, kitchenPane, receiptsPane, shiftPane, settingsPane));
        root.setCenter(newOrderPane);

        refresh();
    }

    public void refresh() {
        renderMenu();
        renderCart();
        renderKitchen();
        renderReceipts();
        renderShift();
    }

    // ============================================================ Sidebar

    private VBox buildSidebar(Node newOrderPane, Node kitchenPane, Node receiptsPane,
                               Node shiftPane, Node settingsPane) {
        ToggleGroup navGroup = new ToggleGroup();

        ToggleButton newOrderBtn = navItem("\uD83D\uDED2", "New Order", navGroup);
        ToggleButton kitchenBtn = navItem("\uD83C\uDF73", "Kitchen Queue", navGroup);
        ToggleButton receiptsBtn = navItem("\uD83E\uDDFE", "Receipts", navGroup);
        ToggleButton shiftBtn = navItem("\uD83D\uDCCA", "Shift Summary", navGroup);
        ToggleButton settingsBtn = navItem("\u2699", "Settings", navGroup);

        newOrderBtn.setSelected(true);
        newOrderBtn.setOnAction(e -> root.setCenter(newOrderPane));
        kitchenBtn.setOnAction(e -> root.setCenter(kitchenPane));
        receiptsBtn.setOnAction(e -> root.setCenter(receiptsPane));
        shiftBtn.setOnAction(e -> root.setCenter(shiftPane));
        settingsBtn.setOnAction(e -> root.setCenter(settingsPane));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox sidebar = new VBox(4, newOrderBtn, kitchenBtn, receiptsBtn, shiftBtn, spacer, settingsBtn);
        sidebar.getStyleClass().add("nav-sidebar");
        sidebar.setPrefWidth(190);
        sidebar.setPadding(new Insets(14, 0, 14, 0));
        return sidebar;
    }

    /** An icon-first nav button whose label fades in on hover, instead of a rotated side-tab label. */
    private ToggleButton navItem(String icon, String label, ToggleGroup group) {
        Label iconLbl = new Label(icon);
        iconLbl.getStyleClass().add("nav-icon");
        Label textLbl = new Label(label);
        textLbl.getStyleClass().add("nav-text");
        textLbl.setOpacity(1);

        HBox content = new HBox(12, iconLbl, textLbl);
        content.setAlignment(Pos.CENTER_LEFT);

        ToggleButton btn = new ToggleButton();
        btn.setGraphic(content);
        btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        btn.getStyleClass().add("nav-btn");
        btn.setToggleGroup(group);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);

        btn.setOnMouseEntered(e -> fade(textLbl, 1));
        btn.setOnMouseExited(e -> fade(textLbl, 1));

        return btn;
    }

    private void fade(Label label, double toValue) {
        FadeTransition ft = new FadeTransition(Duration.millis(150), label);
        ft.setToValue(toValue);
        ft.play();
    }

    // ============================================================ Settings

    private Node buildSettingsPane() {
        Label title = new Label("Settings");
        title.getStyleClass().add("section-title");
        Label note = new Label("Staff and system settings \u2014 placeholder for now.");
        note.getStyleClass().add("section-note");

        Button signOut = new Button("Sign Out");
        signOut.getStyleClass().add("void-link");
        signOut.setDisable(onSignOut == null);
        signOut.setOnAction(e -> { if (onSignOut != null) onSignOut.run(); });

        VBox panel = new VBox(14, title, note, signOut);
        panel.getStyleClass().add("log-panel");
        panel.setMaxWidth(340);

        BorderPane pane = new BorderPane();
        pane.getStyleClass().add("root-bg");
        pane.setPadding(new Insets(18));
        pane.setTop(panel);
        return pane;
    }

    // =========================================================== New Order

    private Node buildNewOrderPane() {
        Label title = new Label("Take an Order");
        title.getStyleClass().add("section-title");
        Label note = new Label("Add menu items to the cart, set dine-in or take-out, then finalize "
                + "to send a kitchen ticket and record the sale.");
        note.getStyleClass().add("section-note");
        note.setWrapText(true);
        VBox titleBox = new VBox(4, title, note);

        Button addItem = new Button("+ Add Item");
        addItem.getStyleClass().add("add-btn");
        addItem.setOnAction(e -> handleAddItem());

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(titleBox, headerSpacer, addItem);
        header.setAlignment(Pos.CENTER_LEFT);

        menuGrid = new FlowPane(14, 14);
        ScrollPane menuScroll = new ScrollPane(menuGrid);
        menuScroll.setFitToWidth(true);
        menuScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox cartPanel = buildCartPanel();
        cartPanel.setPrefWidth(300);

        HBox layout = new HBox(20, menuScroll, cartPanel);
        HBox.setHgrow(menuScroll, Priority.ALWAYS);

        BorderPane pane = new BorderPane();
        pane.getStyleClass().add("root-bg");
        pane.setPadding(new Insets(18));
        pane.setTop(header);
        BorderPane.setMargin(header, new Insets(0, 0, 14, 0));
        pane.setCenter(layout);

        return pane;
    }

    private VBox buildCartPanel() {
        Label heading = new Label("Current Order");
        heading.getStyleClass().add("log-panel-title");

        ToggleGroup typeGroup = new ToggleGroup();
        ToggleButton dineIn = new ToggleButton("Dine-in");
        ToggleButton takeOut = new ToggleButton("Take-out");
        dineIn.getStyleClass().add("toggle-btn");
        takeOut.getStyleClass().add("toggle-btn");
        dineIn.setToggleGroup(typeGroup);
        takeOut.setToggleGroup(typeGroup);
        dineIn.setSelected(true);
        dineIn.setMaxWidth(Double.MAX_VALUE);
        takeOut.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(dineIn, Priority.ALWAYS);
        HBox.setHgrow(takeOut, Priority.ALWAYS);
        HBox typeToggle = new HBox(8, dineIn, takeOut);

        Label tableLabel = new Label("TABLE NO.");
        tableLabel.getStyleClass().add("field-label");
        tableField = new TextField();
        tableField.setPromptText("e.g. 4");
        tableField.getStyleClass().add("field-input");
        tableFieldBox = new VBox(5, tableLabel, tableField);

        dineIn.setOnAction(e -> { orderType = "Dine-in"; tableFieldBox.setVisible(true); tableFieldBox.setManaged(true); });
        takeOut.setOnAction(e -> { orderType = "Take-out"; tableFieldBox.setVisible(false); tableFieldBox.setManaged(false); });

        cartList = new VBox(0);
        cartList.getStyleClass().add("cart-list");

        ToggleGroup payGroup = new ToggleGroup();
        ToggleButton cash = new ToggleButton("Cash");
        ToggleButton ewallet = new ToggleButton("E-wallet");
        cash.getStyleClass().add("toggle-btn");
        ewallet.getStyleClass().add("toggle-btn");
        cash.setToggleGroup(payGroup);
        ewallet.setToggleGroup(payGroup);
        cash.setSelected(true);
        cash.setMaxWidth(Double.MAX_VALUE);
        ewallet.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(cash, Priority.ALWAYS);
        HBox.setHgrow(ewallet, Priority.ALWAYS);
        HBox payToggle = new HBox(8, cash, ewallet);
        cash.setOnAction(e -> payMethod = "Cash");
        ewallet.setOnAction(e -> payMethod = "E-wallet");

        Label totalLbl = new Label("TOTAL");
        totalLbl.getStyleClass().add("field-label");
        cartTotal = new Label("\u20B10");
        cartTotal.getStyleClass().add("stat-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox totalRow = new HBox(totalLbl, spacer, cartTotal);
        totalRow.setAlignment(Pos.CENTER_LEFT);

        finalizeBtn = new Button("Finalize Order");
        finalizeBtn.getStyleClass().add("punch-btn");
        finalizeBtn.setMaxWidth(Double.MAX_VALUE);
        finalizeBtn.setDisable(true);
        finalizeBtn.setOnAction(e -> handleFinalize());

        toast = new Label();
        toast.setWrapText(true);

        VBox panel = new VBox(12, heading, typeToggle, tableFieldBox, cartList, payToggle, totalRow, finalizeBtn, toast);
        panel.getStyleClass().add("log-panel");
        return panel;
    }

    private void handleAddItem() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Menu Item");
        dialog.getDialogPane().getStylesheets().setAll(root.getScene().getStylesheets());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Label nameLabel = new Label("ITEM NAME");
        nameLabel.getStyleClass().add("field-label");
        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Halo-Halo");
        nameField.getStyleClass().add("field-input");

        Label priceLabel = new Label("PRICE (\u20B1)");
        priceLabel.getStyleClass().add("field-label");
        TextField priceField = new TextField();
        priceField.setPromptText("e.g. 85");
        priceField.getStyleClass().add("field-input");

        Label error = new Label();
        error.getStyleClass().add("toast-error");
        error.setWrapText(true);

        VBox content = new VBox(10,
                new VBox(5, nameLabel, nameField),
                new VBox(5, priceLabel, priceField),
                error);
        content.setPrefWidth(280);
        content.setPadding(new Insets(6, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStyleClass().add("root-bg");

        // Keep the OK button from closing the dialog on invalid input instead of
        // just letting it return a result we'd have to reject after the fact.
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            Integer price = parsePositiveInt(priceField.getText());
            if (name.isEmpty() || price == null) {
                error.setText(name.isEmpty() ? "Enter an item name." : "Enter a valid whole-number price.");
                e.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String name = nameField.getText().trim();
            int price = parsePositiveInt(priceField.getText());
            store.addMenuItem(name, price);
            // store.addMenuItem() already fired a change event, which re-runs refresh().
        }
    }

    private Integer parsePositiveInt(String text) {
        if (text == null) return null;
        try {
            int value = Integer.parseInt(text.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void renderMenu() {
        menuGrid.getChildren().clear();
        for (Dish dish : store.getMenu()) {
            menuGrid.getChildren().add(buildMenuCard(dish));
        }
    }

    private VBox buildMenuCard(Dish dish) {
        int wanted = cart.getOrDefault(dish.getId(), 0) + 1;
        boolean ok = store.canFulfill(dish, wanted);

        Label name = new Label(dish.getName());
        name.getStyleClass().add("ticket-name");
        Label price = new Label("\u20B1" + dish.getPrice());
        price.getStyleClass().add("ticket-price");

        Button add = new Button(ok ? "Add to Cart" : "Out of Stock");
        add.getStyleClass().add("punch-btn");
        add.setMaxWidth(Double.MAX_VALUE);
        add.setDisable(!ok);
        add.setOnAction(e -> {
            cart.merge(dish.getId(), 1, Integer::sum);
            renderCart();
            renderMenu();
        });

        VBox card = new VBox(6, name, price, add);
        card.getStyleClass().add("ticket");
        card.setPrefWidth(160);
        return card;
    }

    private void renderCart() {
        cartList.getChildren().clear();
        boolean empty = cart.values().stream().allMatch(q -> q <= 0);
        if (empty) {
            Label emptyLbl = new Label("Cart is empty - add a menu item.");
            emptyLbl.getStyleClass().add("log-empty");
            cartList.getChildren().add(emptyLbl);
        } else {
            for (Map.Entry<String, Integer> e : cart.entrySet()) {
                if (e.getValue() <= 0) continue;
                cartList.getChildren().add(buildCartLine(e.getKey(), e.getValue()));
            }
        }

        int total = cart.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .mapToInt(e -> dishById(e.getKey()).getPrice() * e.getValue())
                .sum();
        cartTotal.setText("\u20B1" + total);
        finalizeBtn.setDisable(total == 0);
    }

    private HBox buildCartLine(String dishId, int qty) {
        Dish dish = dishById(dishId);

        Label name = new Label(dish.getName());
        name.getStyleClass().add("log-row-label");
        HBox.setHgrow(name, Priority.ALWAYS);

        Button dec = new Button("\u2212");
        dec.getStyleClass().add("step-btn");
        Label qtyLbl = new Label(String.valueOf(qty));
        qtyLbl.getStyleClass().add("log-row-label");
        Button inc = new Button("+");
        inc.getStyleClass().add("step-btn");
        dec.setOnAction(e -> { cart.put(dishId, Math.max(0, qty - 1)); renderCart(); renderMenu(); });
        inc.setOnAction(e -> {
            if (store.canFulfill(dish, qty + 1)) { cart.put(dishId, qty + 1); renderCart(); renderMenu(); }
        });

        Label lineTotal = new Label("\u20B1" + (dish.getPrice() * qty));
        lineTotal.getStyleClass().add("log-row-label");

        Button remove = new Button("\u2715");
        remove.getStyleClass().add("void-link");
        remove.setOnAction(e -> { cart.put(dishId, 0); renderCart(); renderMenu(); });

        HBox row = new HBox(8, name, dec, qtyLbl, inc, lineTotal, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 0, 6, 0));
        return row;
    }

    private void handleFinalize() {
        String table = orderType.equals("Dine-in") ? tableField.getText().trim() : null;
        Order order = orderService.finalizeOrder(cart, orderType, (table == null || table.isEmpty()) ? null : table, payMethod);
        if (order == null) {
            toast.getStyleClass().setAll("toast-error");
            toast.setText("Stock changed before finalizing - re-check the cart.");
            return;
        }
        cart.clear();
        tableField.clear();
        toast.getStyleClass().setAll("toast-ok");
        toast.setText("Order #" + order.getId() + " sent to kitchen.");
        // orderService.finalizeOrder() already notified change listeners, which re-runs refresh().
    }

    private Dish dishById(String id) {
        return store.getMenu().stream().filter(m -> m.getId().equals(id)).findFirst().orElse(null);
    }

    // ============================================================ Kitchen

    private Node buildKitchenPane() {
        Label title = new Label("Kitchen Queue");
        title.getStyleClass().add("section-title");
        Label note = new Label("Tickets waiting to be served. Mark served once the order leaves the kitchen.");
        note.getStyleClass().add("section-note");
        VBox titleBox = new VBox(4, title, note);

        Button clearQueue = new Button("Clear Queue");
        clearQueue.getStyleClass().add("void-link");
        clearQueue.setOnAction(e -> orderService.clearActiveQueue());

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(titleBox, headerSpacer, clearQueue);
        header.setAlignment(Pos.CENTER_LEFT);

        kitchenGrid = new FlowPane(16, 16);
        ScrollPane scroll = new ScrollPane(kitchenGrid);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        BorderPane pane = new BorderPane();
        pane.getStyleClass().add("root-bg");
        pane.setPadding(new Insets(18));
        pane.setTop(header);
        BorderPane.setMargin(header, new Insets(0, 0, 14, 0));
        pane.setCenter(scroll);
        return pane;
    }

    private void renderKitchen() {
        kitchenGrid.getChildren().clear();
        List<Order> active = orderService.getOrders().stream().filter(o -> !o.isServed() && !o.isVoided()).toList();
        if (active.isEmpty()) {
            Label empty = new Label("No active tickets.");
            empty.getStyleClass().add("log-empty");
            kitchenGrid.getChildren().add(empty);
            return;
        }
        for (Order o : active) kitchenGrid.getChildren().add(buildKitchenTicket(o));
    }

    private VBox buildKitchenTicket(Order o) {
        Label header = new Label("Order #" + o.getId() + " \u00B7 " + o.getType()
                + (o.getTable() != null ? " \u00B7 T" + o.getTable() : ""));
        header.getStyleClass().add("ticket-name");
        header.setWrapText(true);
        Label time = new Label(o.getTime());
        time.getStyleClass().add("ticket-uses");

        VBox items = new VBox(2);
        for (OrderItem it : o.getItems()) {
            Label line = new Label(it.qty() + "\u00D7 " + it.name());
            line.getStyleClass().add("ticket-uses");
            items.getChildren().add(line);
        }

        Button serve = new Button("Mark Served");
        serve.getStyleClass().add("add-btn");
        serve.setMaxWidth(Double.MAX_VALUE);
        serve.setOnAction(e -> orderService.markServed(o.getId()));

        VBox ticket = new VBox(6, header, time, items, serve);
        ticket.getStyleClass().add("ticket");
        ticket.setPrefWidth(220);
        return ticket;
    }

    // =========================================================== Receipts

    private Node buildReceiptsPane() {
        Label title = new Label("Receipts");
        title.getStyleClass().add("section-title");
        Label note = new Label("Finalized orders for the day, in receipt form.");
        note.getStyleClass().add("section-note");
        VBox titleBox = new VBox(4, title, note);

        Button clearReceipts = new Button("Clear Receipts");
        clearReceipts.getStyleClass().add("void-link");
        clearReceipts.setOnAction(e -> {
            Alert confirm = new Alert(AlertType.CONFIRMATION);
            confirm.setTitle("Clear Receipts");
            confirm.setHeaderText(null);
            confirm.setContentText("This clears all order history (Kitchen Queue, Receipts, and Shift "
                    + "Summary) and starts a new shift. This can't be undone. Continue?");
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                orderService.clearHistory();
            }
        });

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(titleBox, headerSpacer, clearReceipts);
        header.setAlignment(Pos.CENTER_LEFT);

        receiptGrid = new FlowPane(16, 16);
        ScrollPane scroll = new ScrollPane(receiptGrid);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        BorderPane pane = new BorderPane();
        pane.getStyleClass().add("root-bg");
        pane.setPadding(new Insets(18));
        pane.setTop(header);
        BorderPane.setMargin(header, new Insets(0, 0, 14, 0));
        pane.setCenter(scroll);
        return pane;
    }

    private void renderReceipts() {
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

    private VBox buildReceipt(Order o) {
        Label header = new Label("#" + o.getId() + (o.isVoided() ? " (VOID)" : ""));
        header.getStyleClass().add("ticket-name");
        Label time = new Label(o.getType() + " \u00B7 " + o.getTime());
        time.getStyleClass().add("ticket-uses");

        VBox items = new VBox(2);
        for (OrderItem it : o.getItems()) {
            Label left = new Label(it.qty() + "\u00D7 " + it.name());
            left.getStyleClass().add("ticket-uses");
            Label right = new Label("\u20B1" + it.lineTotal());
            right.getStyleClass().add("ticket-uses");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox itemRow = new HBox(left, spacer, right);
            itemRow.setAlignment(Pos.CENTER_LEFT);
            items.getChildren().add(itemRow);
        }

        Label total = new Label("Total \u2014 \u20B1" + o.getTotal());
        total.getStyleClass().add("ticket-price");
        Label pay = new Label("Paid via " + o.getPayMethod());
        pay.getStyleClass().add("ticket-uses");

        VBox ticket = new VBox(6, header, time, items, total, pay);
        ticket.getStyleClass().add("ticket");
        ticket.setPrefWidth(220);
        return ticket;
    }

    // ====================================================== Shift Summary

    private Node buildShiftPane() {
        Label title = new Label("Shift Summary");
        title.getStyleClass().add("section-title");
        Label note = new Label("Cashier totals, order history, and void requests for this shift.");
        note.getStyleClass().add("section-note");

        shiftStats = new HBox(14);
        shiftStats.setPadding(new Insets(14, 0, 14, 0));

        Label historyTitle = new Label("Order History");
        historyTitle.getStyleClass().add("log-panel-title");
        orderHistory = new VBox(0);
        VBox historyPanel = new VBox(8, historyTitle, orderHistory);
        historyPanel.getStyleClass().add("log-panel");
        HBox.setHgrow(historyPanel, Priority.ALWAYS);

        Label voidTitle = new Label("Void / Cancellation Log");
        voidTitle.getStyleClass().add("log-panel-title");
        voidLogRows = new VBox(0);
        VBox voidPanel = new VBox(8, voidTitle, voidLogRows);
        voidPanel.getStyleClass().add("log-panel");
        HBox.setHgrow(voidPanel, Priority.ALWAYS);

        HBox panels = new HBox(16, historyPanel, voidPanel);

        VBox body = new VBox(0, shiftStats, panels);
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        BorderPane pane = new BorderPane();
        pane.getStyleClass().add("root-bg");
        pane.setPadding(new Insets(18));
        pane.setTop(new VBox(4, title, note));
        BorderPane.setMargin(pane.getTop(), new Insets(0, 0, 4, 0));
        pane.setCenter(scroll);
        return pane;
    }

    private void renderShift() {
        List<Order> valid = orderService.getOrders().stream().filter(o -> !o.isVoided()).toList();
        long dineIn = valid.stream().filter(o -> o.getType().equals("Dine-in")).count();
        long takeOut = valid.stream().filter(o -> o.getType().equals("Take-out")).count();

        shiftStats.getChildren().setAll(
                statBox("Orders Today", String.valueOf(valid.size())),
                statBox("Revenue Today", "\u20B1" + orderService.orderRevenueToday()),
                statBox("Dine-in / Take-out", dineIn + " / " + takeOut),
                statBox("Void Requests", String.valueOf(orderService.getVoidLog().size())));
        for (var box : shiftStats.getChildren()) HBox.setHgrow(box, Priority.ALWAYS);

        orderHistory.getChildren().clear();
        List<Order> all = orderService.getOrders();
        if (all.isEmpty()) {
            Label empty = new Label("No orders yet.");
            empty.getStyleClass().add("log-empty");
            orderHistory.getChildren().add(empty);
        } else {
            for (Order o : all) orderHistory.getChildren().add(buildHistoryRow(o));
        }

        voidLogRows.getChildren().clear();
        if (orderService.getVoidLog().isEmpty()) {
            Label empty = new Label("No void requests logged.");
            empty.getStyleClass().add("log-empty");
            voidLogRows.getChildren().add(empty);
        } else {
            for (VoidRecord v : orderService.getVoidLog()) {
                Label left = new Label("#" + v.orderId() + " \u2014 " + v.reason());
                left.getStyleClass().add("log-row-label");
                Label right = new Label(v.time());
                right.getStyleClass().add("log-row-label");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox row = new HBox(left, spacer, right);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(4, 0, 4, 0));
                voidLogRows.getChildren().add(row);
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

    private HBox buildHistoryRow(Order o) {
        Label id = new Label("#" + o.getId());
        id.getStyleClass().add("rank-num");

        String meta = o.getType() + (o.getTable() != null ? " \u00B7 T" + o.getTable() : "")
                + " \u2014 \u20B1" + o.getTotal();
        Label metaLbl = new Label(meta);
        metaLbl.getStyleClass().add("rank-name");
        Label subLbl = new Label(o.getTime() + " \u00B7 " + o.getPayMethod() + (o.isVoided() ? " \u00B7 VOIDED" : ""));
        subLbl.getStyleClass().add("log-row-label");
        VBox metaBox = new VBox(2, metaLbl, subLbl);
        HBox.setHgrow(metaBox, Priority.ALWAYS);

        HBox row = new HBox(10, id, metaBox);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 0, 6, 0));

        if (!o.isVoided()) {
            Button voidBtn = new Button("Request Void");
            voidBtn.getStyleClass().add("void-link");
            voidBtn.setOnAction(e -> {
                TextInputDialog dialog = new TextInputDialog("Customer cancelled");
                dialog.setTitle("Void Order #" + o.getId());
                dialog.setHeaderText(null);
                dialog.setContentText("Reason for voiding Order #" + o.getId() + "?");
                Optional<String> result = dialog.showAndWait();
                result.ifPresent(reason -> orderService.requestVoid(o.getId(), reason.isBlank() ? "No reason given" : reason));
            });
            row.getChildren().add(voidBtn);
        }
        return row;
    }
}
