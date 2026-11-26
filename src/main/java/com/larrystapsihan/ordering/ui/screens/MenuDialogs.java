package com.larrystapsihan.ordering.ui.screens;

import com.larrystapsihan.ordering.data.MenuStore;
import com.larrystapsihan.ordering.model.Dish;
import com.larrystapsihan.ordering.ui.OnScreenKeyboard;
import com.larrystapsihan.ordering.ui.TouchMode;
import java.io.File;
import java.util.Optional;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** The dialogs the admin uses to add or edit menu items and categories. */
final class MenuDialogs {

    private MenuDialogs() { }

    private static final String NO_CATEGORY = "(No category)";

    private static Dialog<ButtonType> newDialog(Node owner, String title) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().getStylesheets().setAll(owner.getScene().getStylesheets());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().getStyleClass().add("root-bg");
        TouchMode.bind(dialog.getDialogPane());
        return dialog;
    }

    static Integer parsePositiveInt(String text) {
        if (text == null) return null;
        try {
            int value = Integer.parseInt(text.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Add (existing == null) or edit a menu item: photo, name, price, category.
     * Saves to the store when OK is pressed with valid input.
     */
    static void itemDialog(Node owner, MenuStore store, Dish existing) {
        boolean editing = existing != null;
        Dialog<ButtonType> dialog = newDialog(owner, editing ? "Edit Menu Item" : "Add Menu Item");

        // ---- photo: preview + chooser (no photo -> "No photo" placeholder) ----
        String[] chosenPhoto = { editing ? existing.getPhotoUri() : null };   // file: URI, or null
        VBox photoBox = new VBox(8);
        photoBox.setAlignment(Pos.CENTER);
        Button choosePhoto = new Button(chosenPhoto[0] == null ? "Add Photo" : "Change Photo");
        choosePhoto.getStyleClass().add("punch-btn");
        Button removePhoto = new Button("Remove");
        removePhoto.getStyleClass().add("void-link");
        removePhoto.setVisible(chosenPhoto[0] != null);
        removePhoto.setManaged(chosenPhoto[0] != null);
        HBox photoButtons = new HBox(8, choosePhoto, removePhoto);
        photoButtons.setAlignment(Pos.CENTER);
        photoBox.getChildren().addAll(Photos.box(chosenPhoto[0], 240, 150), photoButtons);

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
            String uri = Photos.copy(file);
            if (uri == null || new Image(uri, 10, 10, true, false).isError()) {
                error.setText("Couldn't read that image. Try a PNG or JPG.");
                return;
            }
            error.setText("");
            chosenPhoto[0] = uri;
            photoBox.getChildren().set(0, Photos.box(uri, 240, 150));
            choosePhoto.setText("Change Photo");
            removePhoto.setVisible(true);
            removePhoto.setManaged(true);
        });
        removePhoto.setOnAction(e -> {
            chosenPhoto[0] = null;
            photoBox.getChildren().set(0, Photos.box(null, 240, 150));
            choosePhoto.setText("Add Photo");
            removePhoto.setVisible(false);
            removePhoto.setManaged(false);
        });

        Label nameLabel = new Label("DISH NAME");
        nameLabel.getStyleClass().add("field-label");
        TextField nameField = new TextField(editing ? existing.getName() : "");
        nameField.setPromptText("e.g. Halo-Halo");
        nameField.getStyleClass().add("field-input");

        Label priceLabel = new Label("PRICE (₱)");
        priceLabel.getStyleClass().add("field-label");
        TextField priceField = new TextField(editing ? String.valueOf(existing.getPrice()) : "");
        priceField.setPromptText("e.g. 85");
        priceField.getStyleClass().add("field-input");

        Label catLabel = new Label("CATEGORY");
        catLabel.getStyleClass().add("field-label");
        ComboBox<String> category = new ComboBox<>();
        category.getItems().add(NO_CATEGORY);
        category.getItems().addAll(store.getCategories());
        category.setMaxWidth(Double.MAX_VALUE);
        category.setValue(editing && !existing.getCategory().isEmpty() ? existing.getCategory() : NO_CATEGORY);

        VBox form = new VBox(12,
                photoBox,
                new VBox(5, nameLabel, nameField),
                new VBox(5, priceLabel, priceField),
                new VBox(5, catLabel, category),
                error);
        form.setPrefWidth(280);

        // On-screen keyboard beside the form (Touchscreen Mode only)
        OnScreenKeyboard keyboard = new OnScreenKeyboard();
        keyboard.attachText(nameField, true);
        keyboard.attachNumber(priceField);

        HBox content = new HBox(24, form, keyboard);
        content.setPadding(new Insets(6, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        Platform.runLater(nameField::requestFocus);

        // Reject bad input here so the dialog stays open instead of closing on OK.
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText(editing ? "Save Changes" : "Add to Menu");
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
            String cat = NO_CATEGORY.equals(category.getValue()) ? "" : category.getValue();
            String name = nameField.getText().trim();
            int price = parsePositiveInt(priceField.getText());
            if (editing) store.updateMenuItem(existing.getId(), name, price, chosenPhoto[0], cat);
            else store.addMenuItem(name, price, chosenPhoto[0], cat);
            // the store fires a change event, which re-runs every screen's refresh().
        }
    }

    /**
     * Asks for a short text (a category name). Returns the trimmed text, or
     * null if cancelled or left blank.
     */
    static String textPrompt(Node owner, String title, String label, String initial, String okText) {
        Dialog<ButtonType> dialog = newDialog(owner, title);
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        TextField field = new TextField(initial == null ? "" : initial);
        field.getStyleClass().add("field-input");
        field.setPromptText("e.g. Rice Meals");
        VBox form = new VBox(5, l, field);
        form.setPrefWidth(280);
        OnScreenKeyboard keyboard = new OnScreenKeyboard();
        keyboard.attachText(field, true);
        HBox content = new HBox(24, form, keyboard);
        dialog.getDialogPane().setContent(content);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText(okText);
        Platform.runLater(field::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return null;
        String text = field.getText() == null ? "" : field.getText().trim();
        return text.isEmpty() ? null : text;
    }

    /** A yes/no confirmation. Returns true if the admin chose the OK button. */
    static boolean confirm(Node owner, String title, String message, String okText) {
        Dialog<ButtonType> dialog = newDialog(owner, title);
        Label msg = new Label(message);
        msg.setWrapText(true);
        msg.setPrefWidth(340);
        msg.getStyleClass().add("field-label");
        StackPane box = new StackPane(msg);
        box.setPadding(new Insets(4));
        dialog.getDialogPane().setContent(box);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText(okText);
        Optional<ButtonType> result = dialog.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}
