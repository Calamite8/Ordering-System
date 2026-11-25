package com.larrystapsihan.ordering.ui.screens;

import javafx.scene.Node;

/**
 * One page of the Order System (shown in the center when its sidebar icon
 * is clicked). To add a new page: implement this, then register it in
 * OrderController.setStore().
 */
public interface Screen {

    /** The page's root node. Built once; the controller swaps it into the window. */
    Node getView();

    /** Re-draw from current data. Called whenever the menu or orders change. */
    void refresh();
}
