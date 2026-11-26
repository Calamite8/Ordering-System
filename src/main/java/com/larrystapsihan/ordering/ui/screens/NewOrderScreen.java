package com.larrystapsihan.ordering.ui.screens;

import com.larrystapsihan.ordering.data.MenuStore;
import com.larrystapsihan.ordering.model.Dish;
import com.larrystapsihan.ordering.model.Order;
import com.larrystapsihan.ordering.service.AdminService;
import com.larrystapsihan.ordering.service.OrderService;
import com.larrystapsihan.ordering.ui.ReceiptPrinter;
import com.larrystapsihan.ordering.ui.TouchMode;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * New Order page: the menu grid on the left, the current cart on the right
 * (dine-in/take-out, table picker, Finalize Order - cash only), and the
 * "+ Add New Item" button (and dashed tile, admin only) that adds a flat-fee menu item
 * with a photo (optional - shows "No photo" if none), name and price.
 *
 * The cart lives here only (it is not saved). Finalizing hands it to
 * OrderService, which creates the Order.
 */
public class NewOrderScreen implements Screen {

    private final MenuStore store;
    private final OrderService orderService;
    private final AdminService admin;

    // ---- cart state for the current customer ----
    private final Map<String, Integer> cart = new LinkedHashMap<>();   // dishId -> qty
    private String orderType = "Dine-in";
    private static final String PAYMENT = "Cash";    // the only payment method
    static final int TABLE_COUNT = 12;       // how many table buttons to show

    // ---- widgets that refresh() redraws ----
    private final FlowPane menuGrid = new FlowPane(14, 14);
    private final HBox categoryBar = new HBox(8);
    private String selectedCategory = null;          // null = All
    private final VBox cartList = new VBox(0);
    private final Label cartTotal = new Label("₱0");
    private final Button finalizeBtn = new Button("Finalize Order");
    private final Label toast = new Label();
    private final Button printLast = new Button("Print Receipt");
    private Order lastOrder;
    private final ToggleGroup tableGroup = new ToggleGroup();   // touch mode: tap a table; tap again to unselect
    private final TextField tableField = new TextField();       // normal mode: type the table number
    private final VBox tableBox = new VBox(5);

    private final BorderPane view;

    public NewOrderScreen(MenuStore store, OrderService orderService, AdminService admin) {
        this.store = store;
        this.admin = admin;
        this.orderService = orderService;

        VBox cartPanel = buildCartPanel();
        cartPanel.setPrefWidth(340);

        var menuScroll = ScreenLayout.transparentScroll(menuGrid);
        var categoryScroll = ScreenLayout.transparentScroll(categoryBar);
        categoryScroll.setFitToHeight(true);
        categoryScroll.setFitToWidth(false);
        categoryScroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        categoryScroll.setPrefHeight(58);
        categoryScroll.setMinHeight(58);
        categoryBar.setPadding(new Insets(4, 2, 8, 2));
        VBox menuColumn = new VBox(6, categoryScroll, menuScroll);
        VBox.setVgrow(menuScroll, Priority.ALWAYS);
        // let the cards wrap to however wide the window is (more columns when fullscreen)
        menuGrid.prefWrapLengthProperty().bind(menuScroll.widthProperty().subtract(30));
        HBox layout = new HBox(20, menuColumn, cartPanel);
        HBox.setHgrow(menuColumn, Priority.ALWAYS);

        Button addItem = new Button("+  ADD NEW ITEM");
        addItem.getStyleClass().add("add-item-btn");
        addItem.setOnAction(e -> MenuDialogs.itemDialog(addItem, store, null));
        ScreenLayout.adminOnly(addItem, admin);

        view = ScreenLayout.page("Take an Order",
                "Add menu items to the cart, set dine-in or take-out, then finalize "
                        + "to send a kitchen ticket and record the sale.",
                addItem, layout);
    }

    @Override
    public Node getView() { return view; }

    @Override
    public void refresh() {
        renderMenu();
        renderCart();
    }

    // ============================================================ menu grid

    private static final String UNCATEGORIZED = "Other";

    /** The category chips above the grid: All, each category, and "Other" if some items have none. */
    private void renderCategoryBar() {
        boolean hasOther = store.getMenu().stream().anyMatch(d -> d.getCategory().isEmpty());
        if (selectedCategory != null && !selectedCategory.equals(UNCATEGORIZED)
                && !store.getCategories().contains(selectedCategory)) selectedCategory = null;
        if (UNCATEGORIZED.equals(selectedCategory) && !hasOther) selectedCategory = null;

        categoryBar.getChildren().clear();
        categoryBar.getChildren().add(chip("All", null));
        for (String c : store.getCategories()) categoryBar.getChildren().add(chip(c, c));
        if (hasOther && !store.getCategories().isEmpty()) categoryBar.getChildren().add(chip(UNCATEGORIZED, UNCATEGORIZED));
        // no point showing a lone "All" chip
        categoryBar.setVisible(store.getCategories().size() > 0);
        categoryBar.setManaged(categoryBar.isVisible());
    }

    private ToggleButton chip(String label, String category) {
        ToggleButton b = new ToggleButton(label);
        b.getStyleClass().addAll("toggle-btn", "category-chip");
        b.setSelected(java.util.Objects.equals(category, selectedCategory));
        b.setOnAction(e -> { selectedCategory = category; renderMenu(); });
        return b;
    }

    private boolean inSelectedCategory(Dish d) {
        if (selectedCategory == null) return true;
        if (UNCATEGORIZED.equals(selectedCategory)) return d.getCategory().isEmpty();
        return d.getCategory().equals(selectedCategory);
    }

    private void renderMenu() {
        renderCategoryBar();
        menuGrid.getChildren().clear();
        for (Dish dish : store.getMenu()) {
            if (inSelectedCategory(dish)) menuGrid.getChildren().add(buildMenuCard(dish));
        }
        if (store.getMenu().isEmpty()) {
            Label empty = new Label("No menu items yet.\nThe admin can add them: Settings > Admin Login, "
                    + "then \"+ ADD NEW ITEM\" here or use Manage Menu.");
            empty.getStyleClass().add("log-empty");
            empty.setWrapText(true);
            empty.setPrefWidth(420);
            menuGrid.getChildren().add(empty);
        }
        menuGrid.getChildren().add(buildAddTile());
    }

    /** Dashed "+" tile at the end of the grid - a second, hard-to-miss way to add an item. */
    private VBox buildAddTile() {
        Label plus = new Label("+");
        plus.getStyleClass().add("add-tile-plus");
        Label text = new Label("Add New Item");
        text.getStyleClass().add("add-tile-text");
        VBox tile = new VBox(6, plus, text);
        ScreenLayout.adminOnly(tile, admin);
        tile.setAlignment(Pos.CENTER);
        tile.getStyleClass().add("add-tile");
        tile.setPrefSize(CARD_W, CARD_H);
        tile.setOnMouseClicked(e -> MenuDialogs.itemDialog(view, store, null));
        return tile;
    }

    private VBox buildMenuCard(Dish dish) {
        Label name = new Label(dish.getName());
        name.getStyleClass().add("ticket-name");
        Label price = new Label("₱" + dish.getPrice());
        price.getStyleClass().add("ticket-price");

        Button add = new Button("Add to Cart");
        add.getStyleClass().add("punch-btn");
        add.setMaxWidth(Double.MAX_VALUE);
        add.setOnAction(e -> {
            cart.merge(dish.getId(), 1, Integer::sum);
            refresh();
        });

        VBox card = new VBox(8, Photos.box(dish.getPhotoUri(), PHOTO_W, PHOTO_H), name, price, add);
        card.getStyleClass().add("ticket");
        card.setPrefSize(CARD_W, CARD_H);
        return card;
    }

    private static final double CARD_W = 190, CARD_H = 250;
    private static final double PHOTO_W = 166, PHOTO_H = 110;

    // ============================================================ cart panel

    /** Builds the right-hand "Current Order" panel (toggles, table picker, cart rows, total, button). */
    private VBox buildCartPanel() {
        Label heading = new Label("Current Order");
        heading.getStyleClass().add("log-panel-title");

        // Dine-in / Take-out
        ToggleButton dineIn = toggleButton("Dine-in");
        ToggleButton takeOut = toggleButton("Take-out");
        HBox typeToggle = toggleRow(dineIn, takeOut);

        // Table picker: tap a number instead of typing one
        Label tableLabel = new Label();
        tableLabel.getStyleClass().add("field-label");
        tableLabel.textProperty().bind(Bindings.when(TouchMode.enabledProperty())
                .then("TABLE NO.  (tap to choose)").otherwise("TABLE NO."));
        tableField.setPromptText("e.g. 4");
        tableField.getStyleClass().add("field-input");
        FlowPane tables = new FlowPane(6, 6);
        tables.setPrefWrapLength(300);
        for (int i = 1; i <= TABLE_COUNT; i++) {
            ToggleButton t = new ToggleButton(String.valueOf(i));
            t.getStyleClass().addAll("toggle-btn", "table-btn");
            t.setToggleGroup(tableGroup);
            t.setUserData(String.valueOf(i));
            tables.getChildren().add(t);
        }
        // Touchscreen Mode shows the table buttons; otherwise a normal box for the physical keyboard
        tables.visibleProperty().bind(TouchMode.enabledProperty());
        tables.managedProperty().bind(tables.visibleProperty());
        tableField.visibleProperty().bind(TouchMode.enabledProperty().not());
        tableField.managedProperty().bind(tableField.visibleProperty());
        tableBox.getChildren().addAll(tableLabel, tableField, tables);

        dineIn.setOnAction(e -> { orderType = "Dine-in"; showTableField(tableBox, true); });
        takeOut.setOnAction(e -> {
            orderType = "Take-out";
            tableGroup.selectToggle(null);
            tableField.clear();
            showTableField(tableBox, false);
        });

        cartList.getStyleClass().add("cart-list");

        Label payment = new Label("PAYMENT:  CASH ONLY");
        payment.getStyleClass().add("field-label");

        Label totalLbl = new Label("TOTAL");
        totalLbl.getStyleClass().add("field-label");
        cartTotal.getStyleClass().add("stat-value");
        HBox totalRow = ScreenLayout.leftRight(totalLbl, cartTotal);

        finalizeBtn.getStyleClass().add("punch-btn");
        finalizeBtn.setMaxWidth(Double.MAX_VALUE);
        finalizeBtn.setDisable(true);
        finalizeBtn.setOnAction(e -> finalizeOrder());

        toast.setWrapText(true);
        printLast.getStyleClass().add("punch-btn");
        printLast.setMaxWidth(Double.MAX_VALUE);
        printLast.setVisible(false);
        printLast.setManaged(false);
        printLast.setOnAction(e -> ReceiptPrinter.print(lastOrder, view.getScene().getWindow()));

        VBox panel = new VBox(12, heading, typeToggle, tableBox, cartList, payment, totalRow, finalizeBtn, toast, printLast);
        panel.getStyleClass().add("log-panel");
        return panel;
    }

    /** A "toggle-btn" styled button; the first one in a row is selected by toggleRow(). */
    private ToggleButton toggleButton(String text) {
        ToggleButton b = new ToggleButton(text);
        b.getStyleClass().add("toggle-btn");
        b.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(b, Priority.ALWAYS);
        return b;
    }

    /** Puts two toggle buttons side by side in one ToggleGroup, first one selected. */
    private HBox toggleRow(ToggleButton first, ToggleButton second) {
        ToggleGroup group = new ToggleGroup();
        first.setToggleGroup(group);
        second.setToggleGroup(group);
        first.setSelected(true);
        return new HBox(8, first, second);
    }

    private void showTableField(VBox tableBox, boolean visible) {
        tableBox.setVisible(visible);
        tableBox.setManaged(visible);
    }

    private void renderCart() {
        cart.keySet().removeIf(id -> store.dishById(id) == null);   // item removed from the menu by the admin
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
                .mapToInt(e -> store.dishById(e.getKey()).getPrice() * e.getValue())
                .sum();
        cartTotal.setText("₱" + total);
        finalizeBtn.setDisable(total == 0);
    }

    private HBox buildCartLine(String dishId, int qty) {
        Dish dish = store.dishById(dishId);

        Label name = new Label(dish.getName());
        name.getStyleClass().add("log-row-label");
        HBox.setHgrow(name, Priority.ALWAYS);

        Button dec = new Button("−");
        dec.getStyleClass().add("step-btn");
        Label qtyLbl = new Label(String.valueOf(qty));
        qtyLbl.getStyleClass().add("log-row-label");
        Button inc = new Button("+");
        inc.getStyleClass().add("step-btn");
        dec.setOnAction(e -> { cart.put(dishId, Math.max(0, qty - 1)); refresh(); });
        inc.setOnAction(e -> {
            cart.put(dishId, qty + 1);
            refresh();
        });

        Label lineTotal = new Label("₱" + (dish.getPrice() * qty));
        lineTotal.getStyleClass().add("log-row-label");

        Button remove = new Button("✕");
        remove.getStyleClass().add("void-link");
        remove.setOnAction(e -> { cart.put(dishId, 0); refresh(); });

        HBox row = new HBox(8, name, dec, qtyLbl, inc, lineTotal, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 0, 6, 0));
        return row;
    }

    private void finalizeOrder() {
        Toggle picked = tableGroup.getSelectedToggle();
        String typed = tableField.getText() == null ? "" : tableField.getText().trim();
        String table = null;
        if (orderType.equals("Dine-in")) {
            if (picked != null) table = (String) picked.getUserData();
            else if (!typed.isEmpty()) table = typed;
        }
        Order order = orderService.finalizeOrder(cart, orderType, table, PAYMENT);
        if (order == null) {
            toast.getStyleClass().setAll("toast-error");
            toast.setText("Could not send the order - check the cart.");
            return;
        }
        lastOrder = order;
        cart.clear();
        tableGroup.selectToggle(null);
        tableField.clear();
        refresh();   // finalizeOrder() refreshed before the cart was cleared, so redraw now
        toast.getStyleClass().setAll("toast-ok");
        toast.setText("Order #" + order.getId() + " sent to kitchen.");
        printLast.setText("Print Receipt #" + order.getId());
        printLast.setVisible(true);
        printLast.setManaged(true);
    }
}
