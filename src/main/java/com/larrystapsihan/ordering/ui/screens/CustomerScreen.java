package com.larrystapsihan.ordering.ui.screens;

import com.larrystapsihan.ordering.data.MenuStore;
import com.larrystapsihan.ordering.model.Dish;
import com.larrystapsihan.ordering.model.Order;
import com.larrystapsihan.ordering.model.OrderStatus;
import com.larrystapsihan.ordering.service.OrderService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * The Customer Screen: a tap-only self-order screen for the second display.
 *
 * The customer browses the menu (by category, with photos), taps items,
 * chooses Dine-in (with a table number) or Take-out, and presses Place Order.
 * The order is saved as "Waiting for Payment" and the screen shows the order
 * number: the customer pays the cashier, who confirms it on the Pending
 * Payment page. A status board along the bottom always shows which order
 * numbers are being prepared and which are ready.
 *
 * Nothing here needs typing, and it never shows admin tools.
 */
public class CustomerScreen implements Screen {

    private static final String UNCATEGORIZED = "Other";
    private static final Duration THANK_YOU_TIME = Duration.seconds(20);   // confirmation stays this long
    private static final Duration IDLE_RESET = Duration.minutes(2);        // abandoned cart is cleared after this

    private final MenuStore store;
    private final OrderService orderService;

    private final Map<String, Integer> cart = new LinkedHashMap<>();
    private String orderType = "Dine-in";
    private String selectedCategory = null;       // null = All

    private final BorderPane root = new BorderPane();
    private final HBox categoryBar = new HBox(10);
    private final FlowPane menuGrid = new FlowPane(16, 16);
    private final VBox cartLines = new VBox(8);
    private final FlowPane tables = new FlowPane(8, 8);
    private final ToggleGroup tableGroup = new ToggleGroup();
    private final Label totalLabel = new Label("₱0");
    private final Button placeBtn = new Button("Place Order");
    private final Label hint = new Label();
    private final HBox preparingNums = new HBox(14);
    private final HBox readyNums = new HBox(14);
    private final VBox thankYou = new VBox(18);
    private final HBox orderingView = new HBox(20);
    private final PauseTransition thankYouTimer = new PauseTransition(THANK_YOU_TIME);
    private final PauseTransition idleTimer = new PauseTransition(IDLE_RESET);

    public CustomerScreen(MenuStore store, OrderService orderService) {
        this.store = store;
        this.orderService = orderService;

        root.getStyleClass().addAll("customer-root", "touch-mode");   // always finger-sized
        root.setPadding(new Insets(20, 24, 0, 24));

        Label title = new Label("Larry's Tapsihan");
        title.getStyleClass().add("customer-title");
        Label sub = new Label("Tap the food you want, then press Place Order and pay at the counter.");
        sub.getStyleClass().add("customer-sub");
        VBox head = new VBox(2, title, sub);
        head.setPadding(new Insets(0, 0, 12, 0));
        root.setTop(head);

        // ---- left: categories + menu ----
        ScrollPane categoryScroll = ScreenLayout.transparentScroll(categoryBar);
        categoryScroll.setFitToHeight(true);
        categoryScroll.setFitToWidth(false);
        categoryScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        categoryScroll.setMinHeight(70);
        categoryScroll.setPrefHeight(70);
        categoryBar.setPadding(new Insets(4, 2, 10, 2));
        ScrollPane menuScroll = ScreenLayout.transparentScroll(menuGrid);
        VBox menuColumn = new VBox(6, categoryScroll, menuScroll);
        VBox.setVgrow(menuScroll, Priority.ALWAYS);
        HBox.setHgrow(menuColumn, Priority.ALWAYS);

        orderingView.getChildren().addAll(menuColumn, buildCartPanel());
        root.setCenter(orderingView);

        buildThankYou();
        root.setBottom(buildBoard());

        // a half-finished cart is cleared after a couple of idle minutes
        idleTimer.setOnFinished(e -> { if (!cart.isEmpty()) resetOrder(); });
        root.addEventFilter(MouseEvent.ANY, e -> idleTimer.playFromStart());
        thankYouTimer.setOnFinished(e -> showOrdering());
    }

    @Override
    public Node getView() { return root; }

    @Override
    public void refresh() {
        cart.keySet().removeIf(id -> store.dishById(id) == null);   // admin removed an item
        renderCategories();
        renderMenu();
        renderCart();
        renderBoard();
    }

    // ============================================================ cart panel

    private VBox buildCartPanel() {
        Label heading = new Label("Your Order");
        heading.getStyleClass().add("log-panel-title");
        heading.setStyle("-fx-font-size: 22px;");

        ToggleGroup typeGroup = new ToggleGroup();
        ToggleButton dineIn = typeButton("Dine-in", typeGroup);
        ToggleButton takeOut = typeButton("Take-out", typeGroup);
        dineIn.setSelected(true);
        HBox typeRow = new HBox(8, dineIn, takeOut);
        HBox.setHgrow(dineIn, Priority.ALWAYS);
        HBox.setHgrow(takeOut, Priority.ALWAYS);

        Label tableLabel = new Label("YOUR TABLE NUMBER");
        tableLabel.getStyleClass().add("field-label");
        for (int i = 1; i <= NewOrderScreen.TABLE_COUNT; i++) {
            ToggleButton t = new ToggleButton(String.valueOf(i));
            t.getStyleClass().addAll("toggle-btn", "table-btn");
            t.setToggleGroup(tableGroup);
            t.setUserData(String.valueOf(i));
            tables.getChildren().add(t);
        }
        tables.setPrefWrapLength(330);
        VBox tableBox = new VBox(6, tableLabel, tables);
        tableGroup.selectedToggleProperty().addListener((obs, was, now) -> updatePlaceButton());

        dineIn.setOnAction(e -> {
            orderType = "Dine-in";
            tableBox.setVisible(true);
            tableBox.setManaged(true);
            updatePlaceButton();
        });
        takeOut.setOnAction(e -> {
            orderType = "Take-out";
            tableGroup.selectToggle(null);
            tableBox.setVisible(false);
            tableBox.setManaged(false);
            updatePlaceButton();
        });
        // keep one type always selected
        typeGroup.selectedToggleProperty().addListener((obs, was, now) -> { if (now == null) typeGroup.selectToggle(was); });

        ScrollPane lineScroll = ScreenLayout.transparentScroll(cartLines);
        lineScroll.setMinHeight(120);
        VBox.setVgrow(lineScroll, Priority.ALWAYS);

        Label totalCaption = new Label("TOTAL");
        totalCaption.getStyleClass().add("field-label");
        totalLabel.getStyleClass().add("cust-total");
        HBox totalRow = ScreenLayout.leftRight(totalCaption, totalLabel);

        hint.getStyleClass().add("customer-sub");
        hint.setWrapText(true);
        placeBtn.getStyleClass().add("punch-btn");
        placeBtn.setStyle("-fx-font-size: 22px;");
        placeBtn.setMaxWidth(Double.MAX_VALUE);
        placeBtn.setOnAction(e -> placeOrder());
        Button clear = new Button("Start Over");
        clear.getStyleClass().add("void-link");
        clear.setOnAction(e -> resetOrder());

        HBox headRow = ScreenLayout.leftRight(heading, clear);
        VBox panel = new VBox(12, headRow, typeRow, tableBox, lineScroll, totalRow, hint, placeBtn);
        panel.getStyleClass().add("cust-panel");
        panel.setPadding(new Insets(18));
        panel.setPrefWidth(400);
        panel.setMinWidth(400);
        return panel;
    }

    private ToggleButton typeButton(String text, ToggleGroup group) {
        ToggleButton b = new ToggleButton(text);
        b.getStyleClass().add("toggle-btn");
        b.setToggleGroup(group);
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private void renderCart() {
        cartLines.getChildren().clear();
        int total = 0;
        for (Map.Entry<String, Integer> e : cart.entrySet()) {
            Dish d = store.dishById(e.getKey());
            if (d == null) continue;
            int qty = e.getValue();
            total += d.getPrice() * qty;
            Label name = new Label(d.getName());
            name.getStyleClass().add("cust-line");
            name.setWrapText(true);
            HBox.setHgrow(name, Priority.ALWAYS);
            name.setMaxWidth(Double.MAX_VALUE);
            Button minus = new Button("−");
            minus.getStyleClass().add("qty-btn");
            minus.setOnAction(ev -> changeQty(d.getId(), -1));
            Label q = new Label(String.valueOf(qty));
            q.getStyleClass().add("cust-line");
            q.setMinWidth(26);
            q.setAlignment(Pos.CENTER);
            Button plus = new Button("+");
            plus.getStyleClass().add("qty-btn");
            plus.setOnAction(ev -> changeQty(d.getId(), 1));
            Label price = new Label("₱" + d.getPrice() * qty);
            price.getStyleClass().add("cust-line");
            price.setMinWidth(70);
            price.setAlignment(Pos.CENTER_RIGHT);
            HBox row = new HBox(8, name, minus, q, plus, price);
            row.setAlignment(Pos.CENTER_LEFT);
            cartLines.getChildren().add(row);
        }
        if (cartLines.getChildren().isEmpty()) {
            Label empty = new Label("Your order is empty.\nTap a dish to add it.");
            empty.getStyleClass().add("customer-sub");
            cartLines.getChildren().add(empty);
        }
        totalLabel.setText("₱" + total);
        updatePlaceButton();
    }

    private void updatePlaceButton() {
        boolean hasItems = !cart.isEmpty();
        boolean needsTable = orderType.equals("Dine-in") && tableGroup.getSelectedToggle() == null;
        placeBtn.setDisable(!hasItems || needsTable);
        hint.setText(!hasItems ? "" : needsTable ? "Choose your table number to continue." : "");
    }

    private void changeQty(String dishId, int delta) {
        int q = cart.getOrDefault(dishId, 0) + delta;
        if (q <= 0) cart.remove(dishId); else cart.put(dishId, q);
        renderCart();
    }

    // ============================================================ menu

    private void renderCategories() {
        boolean hasOther = store.getMenu().stream().anyMatch(d -> d.getCategory().isEmpty());
        if (selectedCategory != null && !selectedCategory.equals(UNCATEGORIZED)
                && !store.getCategories().contains(selectedCategory)) selectedCategory = null;
        if (UNCATEGORIZED.equals(selectedCategory) && !hasOther) selectedCategory = null;

        categoryBar.getChildren().clear();
        if (store.getCategories().isEmpty()) return;
        categoryBar.getChildren().add(chip("All", null));
        for (String c : store.getCategories()) categoryBar.getChildren().add(chip(c, c));
        if (hasOther) categoryBar.getChildren().add(chip(UNCATEGORIZED, UNCATEGORIZED));
    }

    private ToggleButton chip(String label, String category) {
        ToggleButton b = new ToggleButton(label);
        b.getStyleClass().addAll("toggle-btn", "category-chip");
        b.setStyle("-fx-font-size: 18px;");
        b.setSelected(Objects.equals(category, selectedCategory));
        b.setOnAction(e -> { selectedCategory = category; renderCategories(); renderMenu(); });
        return b;
    }

    private void renderMenu() {
        menuGrid.getChildren().clear();
        for (Dish d : store.getMenu()) {
            boolean inCat = selectedCategory == null
                    || (UNCATEGORIZED.equals(selectedCategory) ? d.getCategory().isEmpty() : d.getCategory().equals(selectedCategory));
            if (inCat) menuGrid.getChildren().add(card(d));
        }
        if (store.getMenu().isEmpty()) {
            Label empty = new Label("The menu is not ready yet.\nPlease ask the staff at the counter.");
            empty.getStyleClass().add("customer-sub");
            menuGrid.getChildren().add(empty);
        }
    }

    private VBox card(Dish d) {
        Label name = new Label(d.getName());
        name.getStyleClass().add("cust-name");
        name.setWrapText(true);
        Label price = new Label("₱" + d.getPrice());
        price.getStyleClass().add("cust-price");
        VBox card = new VBox(8, Photos.box(d.getPhotoUri(), 200, 140), name, price);
        card.getStyleClass().add("cust-card");
        card.setPadding(new Insets(10));
        card.setPrefWidth(220);
        card.setMinWidth(220);
        card.setMaxWidth(220);
        // the whole card is the button: one tap adds one
        card.setOnMouseClicked(e -> changeQty(d.getId(), 1));
        return card;
    }

    // ============================================================ placing the order

    private void placeOrder() {
        String table = orderType.equals("Dine-in") && tableGroup.getSelectedToggle() != null
                ? (String) tableGroup.getSelectedToggle().getUserData() : null;
        Order order = orderService.placeCustomerOrder(new LinkedHashMap<>(cart), orderType, table);
        if (order == null) return;
        showThankYou(order);
    }

    private void buildThankYou() {
        thankYou.setAlignment(Pos.CENTER);
        thankYou.getStyleClass().add("cust-panel");
        thankYou.setPadding(new Insets(30));
    }

    private void showThankYou(Order order) {
        Label thanks = new Label("Thank you!");
        thanks.getStyleClass().add("cust-big");
        Label yourNumber = new Label("Your order number is");
        yourNumber.getStyleClass().add("customer-title");
        Label number = new Label("#" + order.getId());
        number.getStyleClass().add("cust-number");
        Label pay = new Label("Please pay ₱" + order.getTotal() + " in cash at the counter.\n"
                + "Your order goes to the kitchen once you have paid.");
        pay.getStyleClass().add("customer-title");
        pay.setStyle("-fx-font-size: 22px; -fx-text-alignment: center;");
        pay.setWrapText(true);
        Button again = new Button("Start a New Order");
        again.getStyleClass().add("punch-btn");
        again.setStyle("-fx-font-size: 20px;");
        again.setOnAction(e -> showOrdering());
        thankYou.getChildren().setAll(thanks, yourNumber, number, pay, again);
        root.setCenter(thankYou);
        thankYouTimer.playFromStart();
    }

    /** Goes back to the ordering view with an empty cart (also used when the window is (re)opened). */
    public void showOrdering() {
        thankYouTimer.stop();
        resetOrder();
        root.setCenter(orderingView);
    }

    /** Empties the cart and goes back to the Dine-in default. */
    private void resetOrder() {
        cart.clear();
        tableGroup.selectToggle(null);
        refresh();
    }

    // ============================================================ status board

    private HBox buildBoard() {
        Label prep = new Label("NOW PREPARING");
        prep.getStyleClass().add("board-title");
        Label ready = new Label("READY FOR PICKUP");
        ready.getStyleClass().add("board-title");
        VBox prepBox = new VBox(4, prep, preparingNums);
        VBox readyBox = new VBox(4, ready, readyNums);
        HBox.setHgrow(prepBox, Priority.ALWAYS);
        HBox.setHgrow(readyBox, Priority.ALWAYS);
        prepBox.setMinWidth(0);
        readyBox.setMinWidth(0);
        HBox board = new HBox(40, prepBox, readyBox);
        board.getStyleClass().add("cust-board");
        board.setPadding(new Insets(10, 24, 12, 24));
        board.setMinHeight(Region.USE_PREF_SIZE);
        BorderPane.setMargin(board, new Insets(12, -24, 0, -24));
        return board;
    }

    private void renderBoard() {
        List<Order> inKitchen = orderService.getOrders().stream()
                .filter(o -> o.isActive() && o.getStatus() == OrderStatus.IN_KITCHEN).toList();
        List<Order> ready = orderService.getOrders().stream()
                .filter(o -> o.isActive() && o.getStatus() == OrderStatus.READY).toList();
        fill(preparingNums, inKitchen, "board-num");
        fill(readyNums, ready, "board-num-ready");
    }

    private void fill(HBox box, List<Order> orders, String style) {
        box.getChildren().clear();
        if (orders.isEmpty()) {
            Label none = new Label("—");
            none.getStyleClass().add("board-num");
            box.getChildren().add(none);
            return;
        }
        // oldest first, so the next order to come out is on the left
        for (int i = orders.size() - 1; i >= 0; i--) {
            Label n = new Label("#" + orders.get(i).getId());
            n.getStyleClass().add(style);
            box.getChildren().add(n);
        }
    }
}
