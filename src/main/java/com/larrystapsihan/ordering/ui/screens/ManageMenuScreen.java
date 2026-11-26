package com.larrystapsihan.ordering.ui.screens;

import com.larrystapsihan.ordering.data.MenuStore;
import com.larrystapsihan.ordering.model.Dish;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Manage Menu page (admin only - the tab appears after Settings > Admin Login).
 * The admin adds, edits and removes food categories (left) and menu items
 * with their prices, photos and categories (right).
 */
public class ManageMenuScreen implements Screen {

    private final MenuStore store;
    private final VBox categoryRows = new VBox(0);
    private final VBox itemRows = new VBox(0);
    private final javafx.scene.layout.BorderPane view;

    public ManageMenuScreen(MenuStore store) {
        this.store = store;

        Button addCategory = new Button("+ Add Category");
        addCategory.getStyleClass().add("punch-btn");
        addCategory.setOnAction(e -> {
            String name = MenuDialogs.textPrompt(addCategory, "Add Category", "CATEGORY NAME", "", "Add Category");
            if (name != null && !store.addCategory(name)) {
                MenuDialogs.confirm(addCategory, "Category not added",
                        "\"" + name + "\" already exists.", "OK");
            }
        });

        Button addItem = new Button("+ Add Item");
        addItem.getStyleClass().add("punch-btn");
        addItem.setOnAction(e -> MenuDialogs.itemDialog(addItem, store, null));

        VBox categoryPanel = panel("Food Categories", addCategory, categoryRows);
        categoryPanel.setPrefWidth(330);
        categoryPanel.setMinWidth(330);
        categoryPanel.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        VBox itemPanel = panel("Menu Items", addItem, itemRows);
        HBox.setHgrow(itemPanel, Priority.ALWAYS);
        HBox panels = new HBox(16, categoryPanel, itemPanel);
        panels.setAlignment(Pos.TOP_LEFT);

        view = ScreenLayout.page("Manage Menu",
                "Add, edit or remove food categories, menu items and prices. Changes show on the order screen right away.",
                null, ScreenLayout.transparentScroll(panels));
    }

    @Override
    public Node getView() { return view; }

    @Override
    public void refresh() {
        // ---- categories ----
        categoryRows.getChildren().clear();
        if (store.getCategories().isEmpty()) {
            categoryRows.getChildren().add(empty("No categories yet."));
        }
        for (String cat : store.getCategories()) {
            long count = store.getMenu().stream().filter(d -> d.getCategory().equals(cat)).count();
            Label name = new Label(cat);
            name.getStyleClass().add("rank-name");
            Label meta = new Label(count + (count == 1 ? " item" : " items"));
            meta.getStyleClass().add("log-row-label");
            VBox text = new VBox(2, name, meta);
            HBox.setHgrow(text, Priority.ALWAYS);

            Button rename = new Button("Rename");
            rename.getStyleClass().add("void-link");
            rename.setOnAction(e -> {
                String n = MenuDialogs.textPrompt(view, "Rename Category", "CATEGORY NAME", cat, "Save");
                if (n != null && !store.renameCategory(cat, n)) {
                    MenuDialogs.confirm(view, "Not renamed", "\"" + n + "\" already exists.", "OK");
                }
            });
            Button remove = new Button("Delete");
            remove.getStyleClass().add("void-link");
            remove.setOnAction(e -> {
                if (MenuDialogs.confirm(view, "Delete Category",
                        "Delete \"" + cat + "\"? Its " + count + " item(s) stay on the menu with no category.",
                        "Delete")) {
                    store.removeCategory(cat);
                }
            });
            categoryRows.getChildren().add(row(text, rename, remove));
        }

        // ---- items ----
        itemRows.getChildren().clear();
        if (store.getMenu().isEmpty()) {
            itemRows.getChildren().add(empty("No menu items yet. Press \"+ Add Item\"."));
        }
        for (Dish d : store.getMenu()) {
            Label name = new Label(d.getName());
            name.getStyleClass().add("rank-name");
            Label meta = new Label((d.getCategory().isEmpty() ? "No category" : d.getCategory())
                    + "  ·  ₱" + d.getPrice());
            meta.getStyleClass().add("log-row-label");
            VBox text = new VBox(2, name, meta);
            HBox.setHgrow(text, Priority.ALWAYS);

            Button edit = new Button("Edit");
            edit.getStyleClass().add("punch-btn");
            edit.setOnAction(e -> MenuDialogs.itemDialog(view, store, d));
            Button remove = new Button("Remove");
            remove.getStyleClass().add("void-link");
            remove.setOnAction(e -> {
                if (MenuDialogs.confirm(view, "Remove Item",
                        "Remove \"" + d.getName() + "\" from the menu? Old receipts are not affected.", "Remove")) {
                    store.removeMenuItem(d.getId());
                }
            });
            itemRows.getChildren().add(row(Photos.box(d.getPhotoUri(), 64, 44), text, edit, remove));
        }
    }

    private HBox row(Node... children) {
        HBox row = new HBox(10, children);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 0, 6, 0));
        return row;
    }

    private Label empty(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("log-empty");
        return l;
    }

    private VBox panel(String title, Button action, VBox rows) {
        Label heading = new Label(title);
        heading.getStyleClass().add("log-panel-title");
        HBox head = ScreenLayout.leftRight(heading, action);
        VBox panel = new VBox(10, head, rows);
        panel.getStyleClass().add("log-panel");
        return panel;
    }
}
