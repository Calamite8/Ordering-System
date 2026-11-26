package com.larrystapsihan.ordering.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.value.ObservableBooleanValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The icon sidebar on the left of the Order System. Each entry shows only
 * its icon; hovering fades its label in. The main entries stack at the top
 * and one entry (Settings) is pinned to the bottom.
 *
 * (A real JavaFX TabPane on the left rotates its labels sideways, which is
 * why this is a custom control.)
 */
public final class NavSidebar extends VBox {

    /**
     * One sidebar entry: an icon, a hover label, what happens when it is clicked,
     * and (optionally) a flag that decides whether the entry is shown at all.
     */
    public record Item(String icon, String label, Runnable onSelect, ObservableBooleanValue visibleWhen) {
        /** An entry that is always shown. */
        public Item(String icon, String label, Runnable onSelect) {
            this(icon, label, onSelect, null);
        }
    }

    private final ToggleGroup group = new ToggleGroup();
    private final Map<String, ToggleButton> buttonsByLabel = new HashMap<>();
    private final Map<String, Item> itemsByLabel = new HashMap<>();
    private final Map<String, Label> badgesByLabel = new HashMap<>();

    /**
     * @param topItems   entries stacked from the top; the first one starts selected
     * @param bottomItem entry pinned to the bottom (e.g. Settings)
     */
    public NavSidebar(List<Item> topItems, Item bottomItem) {
        super(4);
        getStyleClass().add("nav-sidebar");
        setPrefWidth(190);
        // Touchscreen Mode: bigger text needs a wider bar so "Kitchen Queue" is not cut off
        TouchMode.enabledProperty().addListener((obs, was, is) -> setPrefWidth(is ? 240 : 190));
        if (TouchMode.isEnabled()) setPrefWidth(240);
        setPadding(new Insets(14, 0, 14, 0));

        boolean first = true;
        for (Item item : topItems) {
            ToggleButton btn = buildButton(item);
            btn.setSelected(first);
            first = false;
            getChildren().add(btn);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        getChildren().addAll(spacer, buildButton(bottomItem));

        // Clicking the already-selected icon would un-highlight everything; keep one selected.
        group.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) old.setSelected(true);
        });
    }

    /** Selects the entry with this label and runs its action (used to leave a page that just got hidden). */
    public void select(String label) {
        ToggleButton btn = buttonsByLabel.get(label);
        if (btn == null) return;
        btn.setSelected(true);
        itemsByLabel.get(label).onSelect().run();
    }

    /** Shows a small count next to an entry's name (e.g. orders waiting); 0 hides it. */
    public void setBadge(String label, int count) {
        Label badge = badgesByLabel.get(label);
        if (badge == null) return;
        badge.setText(String.valueOf(count));
        badge.setVisible(count > 0);
        badge.setManaged(count > 0);
    }

    private ToggleButton buildButton(Item item) {
        Label icon = new Label(item.icon());
        icon.getStyleClass().add("nav-icon");
        Label text = new Label(item.label());
        text.getStyleClass().add("nav-text");

        Label badge = new Label();
        badge.getStyleClass().add("nav-badge");
        badge.setVisible(false);
        badge.setManaged(false);
        badgesByLabel.put(item.label(), badge);

        HBox content = new HBox(12, icon, text, badge);
        content.setAlignment(Pos.CENTER_LEFT);

        ToggleButton btn = new ToggleButton();
        btn.setGraphic(content);
        btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        btn.getStyleClass().add("nav-btn");
        btn.setToggleGroup(group);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);

        if (item.visibleWhen() != null) {
            btn.visibleProperty().bind(item.visibleWhen());
            btn.managedProperty().bind(btn.visibleProperty());
        }
        buttonsByLabel.put(item.label(), btn);
        itemsByLabel.put(item.label(), item);

        btn.setOnAction(e -> item.onSelect().run());
        return btn;
    }

}
