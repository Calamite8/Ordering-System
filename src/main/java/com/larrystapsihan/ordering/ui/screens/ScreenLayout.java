package com.larrystapsihan.ordering.ui.screens;

import com.larrystapsihan.ordering.service.AdminService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Small layout helpers shared by every screen, so each screen file only
 * contains what is specific to that screen.
 */
final class ScreenLayout {

    private ScreenLayout() { }

    /**
     * The standard page frame: title + note at the top-left, an optional
     * action button at the top-right, and the content underneath.
     */
    static BorderPane page(String title, String note, Node action, Node content) {
        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("section-title");
        Label noteLbl = new Label(note);
        noteLbl.getStyleClass().add("section-note");
        noteLbl.setWrapText(true);
        VBox titleBox = new VBox(4, titleLbl, noteLbl);

        Node header = titleBox;
        if (action != null) {
            HBox row = new HBox(titleBox, growSpacer(), action);
            row.setAlignment(Pos.CENTER_LEFT);
            header = row;
        }

        BorderPane pane = new BorderPane();
        pane.getStyleClass().add("root-bg");
        pane.setPadding(new Insets(18));
        pane.setTop(header);
        BorderPane.setMargin(header, new Insets(0, 0, 14, 0));
        pane.setCenter(content);
        return pane;
    }

    /** Shows the node (and gives it layout space) only while an admin is logged in. */
    static void adminOnly(Node node, AdminService admin) {
        node.visibleProperty().bind(admin.loggedInProperty());
        node.managedProperty().bind(node.visibleProperty());
    }

    /** A scroll pane with no background of its own, so the page color shows through. */
    static ScrollPane transparentScroll(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        return scroll;
    }

    /** An empty stretchy gap that pushes whatever follows it to the right of an HBox. */
    static Region growSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    /** A "label : value" row with the value pushed to the right edge. */
    static HBox leftRight(Node left, Node right) {
        HBox row = new HBox(left, growSpacer(), right);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
