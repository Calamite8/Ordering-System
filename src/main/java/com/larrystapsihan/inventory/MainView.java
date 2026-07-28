package com.larrystapsihan.inventory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
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

        LoadedTab<StockBoardController> board = loadTab("StockBoardView.fxml", c -> c.setStore(store));
        LoadedTab<SaleController> sale = loadTab("SaleView.fxml", c -> c.setStore(store));
        LoadedTab<StockInController> stockIn = loadTab("StockInView.fxml", c -> c.setStore(store));
        LoadedTab<ReportsController> reports = loadTab("ReportsView.fxml", c -> c.setStore(store));

        Runnable refreshAll = () -> {
            board.controller().refresh();
            sale.controller().refresh();
            stockIn.controller().refresh();
            reports.controller().refresh();
        };
        store.addChangeListener(refreshAll);

        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(
                tab("Stock Board", board.root()),
                tab("Record a Sale", sale.root()),
                tab("Stock In", stockIn.root()),
                tab("Reports", reports.root()));
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> refreshAll.run());

        setCenter(tabs);
    }

    private static <T> LoadedTab<T> loadTab(String fxml, Consumer<T> init) {
        FXMLLoader loader = new FXMLLoader(MainView.class.getResource(fxml));
        try {
            Parent root = loader.load();
            T controller = loader.getController();
            init.accept(controller);
            return new LoadedTab<>(root, controller);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + fxml, e);
        }
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
        header.getStyleClass().add("header-box");

        return header;
    }

    private record LoadedTab<T>(Parent root, T controller) { }
}
