package com.larrystapsihan.ordering.ui.screens;

import com.larrystapsihan.ordering.model.Order;
import com.larrystapsihan.ordering.model.OrderItem;
import com.larrystapsihan.ordering.model.OrderStatus;
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
 * Kitchen Queue page: one ticket card per paid order that is not yet
 * served, each with a "Mark Ready" and then a "Mark Served" button, plus "Clear Queue" to mark
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
                "Paid orders being prepared. Mark ready when the food is done, served when it is handed over.",
                clearQueue, ScreenLayout.transparentScroll(ticketGrid));
    }

    @Override
    public Node getView() { return view; }

    @Override
    public void refresh() {
        ticketGrid.getChildren().clear();
        List<Order> active = orderService.getOrders().stream()
                .filter(Order::isActive).toList();
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

        // In Kitchen -> Mark Ready -> (customer board shows it as ready) -> Mark Served
        boolean ready = o.getStatus() == OrderStatus.READY;
        Label state = new Label(ready ? "READY FOR PICKUP" : "PREPARING");
        state.getStyleClass().add(ready ? "ticket-ready" : "ticket-uses");
        Button action = new Button(ready ? "Mark Served" : "Mark Ready");
        action.getStyleClass().add("add-btn");
        action.setMaxWidth(Double.MAX_VALUE);
        action.setOnAction(e -> {
            if (ready) orderService.markServed(o.getId()); else orderService.markReady(o.getId());
        });

        VBox ticket = new VBox(6, header, time, state, items, action);
        ticket.getStyleClass().add("ticket");
        ticket.setPrefWidth(220);
        return ticket;
    }
}
