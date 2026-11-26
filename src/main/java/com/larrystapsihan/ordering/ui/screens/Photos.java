package com.larrystapsihan.ordering.ui.screens;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

/** Dish-photo helpers shared by the menu cards and the item dialog. */
final class Photos {

    private Photos() { }

    /**
     * The dish photo cropped to fill a w x h box, or a "No photo" placeholder
     * when photoUri is null or the file can't be loaded.
     */
    static StackPane box(String photoUri, double w, double h) {
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
            if (w < 100) none.setStyle("-fx-font-size: 9px;");
            box.getChildren().add(none);
        }
        return box;
    }

    /**
     * Copies the chosen image into ~/LarrysTapsihan/photos so the menu keeps
     * working if the original file is moved or deleted. Returns its file: URI,
     * or null if the copy failed.
     */
    static String copy(File source) {
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
}
