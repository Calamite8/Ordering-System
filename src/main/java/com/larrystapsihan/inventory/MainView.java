package com.larrystapsihan.inventory;

import java.io.IOException;
import java.io.UncheckedIOException;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/** Top-level layout: brand header + a TabPane holding the four sections. */
public class MainView extends BorderPane {

    public MainView(InventoryStore store) {
        getStyleClass().add("root-bg");
        setTop(buildHeader());

        // InventoryStore.punchTicket()/addStock() already notify every registered
        // change-listener, so each view's own "onDataChanged" hook can be a no-op -
        // the shared refreshAll listener below is what keeps every tab in sync.
        Runnable noOp = () -> { };
        StockBoardView board = new StockBoardView(store);
        SaleView sale = new SaleView(store, noOp);
        ReportsView reports = new ReportsView(store);

        // Stock In is now FXML (edit StockInView.fxml in Scene Builder); the
        // other three tabs are still hand-coded Java for now.
        FXMLLoader stockInLoader = new FXMLLoader(
                getClass().getResource("StockInView.fxml"));
        Parent stockInRoot;
        try {
            stockInRoot = stockInLoader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load StockInView.fxml", e);
        }
        StockInController stockInController = stockInLoader.getController();
        stockInController.setStore(store);

        Runnable refreshAll = () -> {
            board.refresh();
            sale.refresh();
            stockInController.refresh();
            reports.refresh();
        };
        store.addChangeListener(refreshAll);

        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(
                tab("Stock Board", board),
                tab("Record a Sale", sale),
                tab("Stock In", stockInRoot),
                tab("Reports", reports));
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> refreshAll.run());

        setCenter(tabs);
    }

    private Tab tab(String title, Node content) {
        Tab t = new Tab(title, content);
        t.setClosable(false);
        return t;
    }

    private VBox buildHeader() {
        Text brand = new Text("LARRY'S");
        brand.getStyleClass().add("brand-mark");
        Text brandAccent = new Text(" TAPSIHAN");
        brandAccent.getStyleClass().addAll("brand-mark", "brand-accent");
        TextFlow brandLine = new TextFlow(brand, brandAccent);
        
        
        Label sub = new Label("INVENTORY SYSTEM \u00B7 STOCK BOARD");
        sub.getStyleClass().add("brand-sub");
        VBox header = new VBox(6, brandLine, sub);
        header.setPadding(new Insets(20, 22, 16, 22));
        // Moved the inline style to a clean CSS class
        header.getStyleClass().add("header-box");

        return header;

    }
}
