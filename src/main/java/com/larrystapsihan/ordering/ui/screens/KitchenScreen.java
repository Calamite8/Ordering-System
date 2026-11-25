package com.larrystapsihan.ordering.ui.screens;

import com.larrystapsihan.ordering.model.Order;
import com.larrystapsihan.ordering.model.OrderItem;
import com.larrystapsihan.ordering.service.AdminService;
import com.larrystapsihan.ordering.service.OrderService;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/**
 * Kitchen Queue page: one ticket card per order that is not yet served or
 * voided, each with a "Mark Served" button, plus "Clear Queue" to mark
 * every open ticket served at once (admin only).
 */
public class KitchenScreen implements Screen {

    private final OrderService orderService;
    private final FlowPane ticketGrid = new FlowPane(16, 16);
    private final BorderPane view;

    public KitchenScreen(OrderService orderService, AdminService admin) {
        this.orderService = orderService;

        Button clearQueue = new Button("Clear Queue");
        clearQueue.getStyleClass().add("void-link");
        clearQueue.setOnAction(e -> orderService.clearActiveQueue());
        ScreenLayout.adminOnly(clearQueue, admin);

        view = ScreenLayout.page("Kitchen Queue",
                "Tickets waiting to be served. Mark served once the order leaves the kitchen.",
                clearQueue, ScreenLayout.transparentScroll(ticketGrid));
    }

    @Override
    public Node getView() { return view; }

    @Override
    public void refresh() {
        ticketGrid.getChildren().clear();
        List<Order> active = orderService.getOrders().stream()
                .filter(o -> !o.isServed() && !o.isVoided()).toList();
        if (active.isEmpty()) {
            Label empty = new Label("No active tickets.");
            empty.getStyleClass().add("log-empty");
            ticketGrid.getChildren().add(empty);
            return;
        }
        for (Order o : active) ticketGrid.getChildren().add(buildTicket(o));
    }

    private VBox buildTicket(Order o) {
        Label header = new Label("Order #" + o.getId() + " · " + o.getType()
                + (o.getTable() != null ? " · T" + o.getTable() : ""));
        header.getStyleClass().add("ticket-name");
        header.setWrapText(true);
        Label time = new Label(o.getTime());
        time.getStyleClass().add("ticket-uses");

        VBox items = new VBox(2);
        for (OrderItem it : o.getItems()) {
            Label line = new Label(it.qty() + "× " + it.name());
            line.getStyleClass().add("ticket-uses");
            items.getChildren().add(line);
        }

        Button serve = new Button("Mark Served");
        serve.getStyleClass().add("add-btn");
        serve.setMaxWidth(Double.MAX_VALUE);
        serve.setOnAction(e -> orderService.markServed(o.getId()));

        VBox ticket = new VBox(6, header, time, items, serve);
        ticket.getStyleClass().add("ticket");
        ticket.setPrefWidth(220);
        return ticket;
    }
}
