package com.larrystapsihan.ordering.ui.screens;

import com.larrystapsihan.ordering.data.MenuStore;
import com.larrystapsihan.ordering.model.Dish;
import com.larrystapsihan.ordering.model.Order;
import com.larrystapsihan.ordering.service.AdminService;
import com.larrystapsihan.ordering.service.OrderService;
import com.larrystapsihan.ordering.ui.OnScreenKeyboard;
import com.larrystapsihan.ordering.ui.TouchMode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.geometry.Rectangle2D;
import javafx.stage.FileChooser;
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
    private static final int TABLE_COUNT = 12;       // how many table buttons to show

    // ---- widgets that refresh() redraws ----
    private final FlowPane menuGrid = new FlowPane(14, 14);
    private final VBox cartList = new VBox(0);
    private final Label cartTotal = new Label("₱0");
    private final Button finalizeBtn = new Button("Finalize Order");
    private final Label toast = new Label();
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
        // let the cards wrap to however wide the window is (more columns when fullscreen)
        menuGrid.prefWrapLengthProperty().bind(menuScroll.widthProperty().subtract(30));
        HBox layout = new HBox(20, menuScroll, cartPanel);
        HBox.setHgrow(menuScroll, Priority.ALWAYS);

        Button addItem = new Button("+  ADD NEW ITEM");
        addItem.getStyleClass().add("add-item-btn");
        addItem.setOnAction(e -> showAddItemDialog());
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

    private void renderMenu() {
        menuGrid.getChildren().clear();
        for (Dish dish : store.getMenu()) menuGrid.getChildren().add(buildMenuCard(dish));
        if (store.getMenu().isEmpty()) {
            Label empty = new Label("No menu items yet.\nThe admin can add them: Settings > Admin Login, "
                    + "then \"+ ADD NEW ITEM\" here.");
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
        tile.setOnMouseClicked(e -> showAddItemDialog());
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

        VBox card = new VBox(8, buildPhoto(dish.getPhotoUri(), PHOTO_W, PHOTO_H), name, price, add);
        card.getStyleClass().add("ticket");
        card.setPrefSize(CARD_W, CARD_H);
        return card;
    }

    private static final double CARD_W = 190, CARD_H = 250;
    private static final double PHOTO_W = 166, PHOTO_H = 110;

    /**
     * The dish photo cropped to fill a w x h box, or a "No photo" placeholder
     * when photoUri is null or the file can't be loaded.
     */
    private StackPane buildPhoto(String photoUri, double w, double h) {
        StackPane box = new StackPane();
        box.setPrefSize(w, h);
        box.setMinSize(w, h);
        box.setMaxSize(w, h);
        box.getStyleClass().add("photo-box");

        Image img = null;
        if (photoUri != null) {
            try { img = new Image(photoUri, 600, 600, true, true); } catch (RuntimeException ignored) { }
        }
        if (img != null && !img.isError() && img.getWidth() > 0) {
            ImageView iv = new ImageView(img);
            iv.setFitWidth(w);
            iv.setFitHeight(h);
            // crop to the box's aspect ratio (like CSS "cover") instead of stretching
            double boxRatio = w / h, imgRatio = img.getWidth() / img.getHeight();
            if (imgRatio > boxRatio) {
                double cw = img.getHeight() * boxRatio;
                iv.setViewport(new Rectangle2D((img.getWidth() - cw) / 2, 0, cw, img.getHeight()));
            } else {
                double ch = img.getWidth() / boxRatio;
                iv.setViewport(new Rectangle2D(0, (img.getHeight() - ch) / 2, img.getWidth(), ch));
            }
            box.getChildren().add(iv);
        } else {
            Label none = new Label("NO PHOTO");
            none.getStyleClass().add("photo-none");
            box.getChildren().add(none);
        }
        return box;
    }

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

        VBox panel = new VBox(12, heading, typeToggle, tableBox, cartList, payment, totalRow, finalizeBtn, toast);
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
        cart.clear();
        tableGroup.selectToggle(null);
        tableField.clear();
        refresh();   // finalizeOrder() refreshed before the cart was cleared, so redraw now
        toast.getStyleClass().setAll("toast-ok");
        toast.setText("Order #" + order.getId() + " sent to kitchen.");
    }

    // ============================================================ Add Item

    private void showAddItemDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Menu Item");
        dialog.getDialogPane().getStylesheets().setAll(view.getScene().getStylesheets());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().getStyleClass().add("root-bg");
        TouchMode.bind(dialog.getDialogPane());

        // ---- photo: preview + chooser (no photo -> "No photo" placeholder) ----
        String[] chosenPhoto = { null };                       // file: URI, or null
        StackPane[] preview = { buildPhoto(null, 240, 150) };
        VBox photoBox = new VBox(8);
        photoBox.setAlignment(Pos.CENTER);
        Button choosePhoto = new Button("Add Photo");
        choosePhoto.getStyleClass().add("punch-btn");
        Button removePhoto = new Button("Remove");
        removePhoto.getStyleClass().add("void-link");
        removePhoto.setVisible(false);
        removePhoto.setManaged(false);
        HBox photoButtons = new HBox(8, choosePhoto, removePhoto);
        photoButtons.setAlignment(Pos.CENTER);
        photoBox.getChildren().addAll(preview[0], photoButtons);

        Label error = new Label();
        error.getStyleClass().add("toast-error");
        error.setWrapText(true);

        choosePhoto.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Choose a photo");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
            File file = fc.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (file == null) return;
            String uri = copyPhoto(file);
            if (uri == null || new Image(uri, 10, 10, true, false).isError()) {
                error.setText("Couldn't read that image. Try a PNG or JPG.");
                return;
            }
            error.setText("");
            chosenPhoto[0] = uri;
            swapPreview(photoBox, preview, buildPhoto(uri, 240, 150));
            choosePhoto.setText("Change Photo");
            removePhoto.setVisible(true);
            removePhoto.setManaged(true);
        });
        removePhoto.setOnAction(e -> {
            chosenPhoto[0] = null;
            swapPreview(photoBox, preview, buildPhoto(null, 240, 150));
            choosePhoto.setText("Add Photo");
            removePhoto.setVisible(false);
            removePhoto.setManaged(false);
        });

        Label nameLabel = new Label("DISH NAME");
        nameLabel.getStyleClass().add("field-label");
        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Halo-Halo");
        nameField.getStyleClass().add("field-input");

        Label priceLabel = new Label("PRICE (₱)");
        priceLabel.getStyleClass().add("field-label");
        TextField priceField = new TextField();
        priceField.setPromptText("e.g. 85");
        priceField.getStyleClass().add("field-input");

        VBox form = new VBox(12,
                photoBox,
                new VBox(5, nameLabel, nameField),
                new VBox(5, priceLabel, priceField),
                error);
        form.setPrefWidth(280);

        // On-screen keyboard beside the form: full keyboard for the name, number pad for the price
        OnScreenKeyboard keyboard = new OnScreenKeyboard();
        keyboard.attachText(nameField, true);
        keyboard.attachNumber(priceField);

        HBox content = new HBox(24, form, keyboard);
        content.setPadding(new Insets(6, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        Platform.runLater(nameField::requestFocus);

        // Reject bad input here so the dialog stays open instead of closing on OK.
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText("Add to Menu");
        okBtn.addEventFilter(ActionEvent.ACTION, e -> {
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            Integer price = parsePositiveInt(priceField.getText());
            if (name.isEmpty() || price == null) {
                error.setText(name.isEmpty() ? "Enter a dish name." : "Enter a valid whole-number price.");
                e.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            store.addMenuItem(nameField.getText().trim(), parsePositiveInt(priceField.getText()), chosenPhoto[0]);
            // store.addMenuItem() fires a change event, which re-runs refresh().
        }
    }

    private void swapPreview(VBox photoBox, StackPane[] preview, StackPane next) {
        photoBox.getChildren().set(0, next);
        preview[0] = next;
    }

    /**
     * Copies the chosen image into ~/LarrysTapsihan/photos so the menu keeps
     * working if the original file is moved or deleted. Returns its file: URI,
     * or null if the copy failed.
     */
    private String copyPhoto(File source) {
        try {
            Path dir = Path.of(System.getProperty("user.home"), "LarrysTapsihan", "photos");
            Files.createDirectories(dir);
            Path target = dir.resolve(System.currentTimeMillis() + "_" + source.getName());
            Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            return target.toUri().toString();
        } catch (IOException ex) {
            return null;
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
}
