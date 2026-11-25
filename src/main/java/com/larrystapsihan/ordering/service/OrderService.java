package com.larrystapsihan.ordering.service;

import com.larrystapsihan.ordering.data.MenuStore;
import com.larrystapsihan.ordering.data.OrderRepository;
import com.larrystapsihan.ordering.model.Dish;
import com.larrystapsihan.ordering.model.Order;
import com.larrystapsihan.ordering.model.OrderItem;
import com.larrystapsihan.ordering.model.VoidRecord;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The order logic: finalize a cart into an Order, mark served, void,
 * clear the queue, clear history. Prices dishes through MenuStore and
 * mirrors every change to OrderRepository when a database is connected.
 *
 * No JavaFX in here - the screens call it, it never calls them.
 */
public class OrderService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    private final MenuStore store;
    private final OrderRepository repository; // nullable - no database is a valid mode
    private final List<Order> orders = new ArrayList<>();
    private final List<VoidRecord> voidLog = new ArrayList<>();
    private int orderCounter = 101;

    /** In-memory only - no order history persistence. */
    public OrderService(MenuStore store) {
        this(store, null);
    }

    /** With a repository, existing order history is loaded immediately and every change is saved. */
    public OrderService(MenuStore store, OrderRepository repository) {
        this.store = store;
        this.repository = repository;
        if (repository != null) {
            orders.addAll(repository.loadOrders());
            voidLog.addAll(repository.loadVoidLog());
            orderCounter = orders.stream().mapToInt(Order::getId).max().orElse(100) + 1;
        }
    }

    public List<Order> getOrders() { return orders; }
    public List<VoidRecord> getVoidLog() { return voidLog; }
    public boolean isPersistent() { return repository != null; }

    /**
     * Turns the cart (dishId -> qty) into an Order with a new order number.
     * Returns null if the cart is empty or holds an unknown dish.
     */
    public Order finalizeOrder(Map<String, Integer> cart, String type, String table, String payMethod) {
        List<OrderItem> items = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, Integer> e : cart.entrySet()) {
            if (e.getValue() <= 0) continue;
            Dish dish = store.dishById(e.getKey());
            if (dish == null) return null;
            int lineTotal = dish.getPrice() * e.getValue();
            items.add(new OrderItem(dish.getId(), dish.getName(), e.getValue(), lineTotal));
            total += lineTotal;
        }
        if (items.isEmpty()) return null;

        Order order = new Order(orderCounter++, type, table, payMethod, items, total, timeNow());
        orders.add(0, order);
        if (repository != null) repository.save(order);
        store.fireChange();
        return order;
    }

    /** Marks a kitchen-queue order as delivered. */
    public void markServed(int orderId) {
        orders.stream().filter(o -> o.getId() == orderId).findFirst().ifPresent(o -> {
            o.setServed(true);
            if (repository != null) repository.updateStatus(o);
            store.fireChange();
        });
    }

    /** Marks every currently active (not yet served, not voided) order as served in one go. */
    public void clearActiveQueue() {
        for (Order o : orders) {
            if (!o.isServed() && !o.isVoided()) {
                o.setServed(true);
                if (repository != null) repository.updateStatus(o);
            }
        }
        store.fireChange();
    }

    /** Voids an order for accounting purposes. */
    public void voidOrder(int orderId, String reason) {
        orders.stream().filter(o -> o.getId() == orderId).findFirst().ifPresent(o -> {
            o.setVoided(true);
            VoidRecord v = new VoidRecord(orderId, reason, timeNow());
            voidLog.add(0, v);
            if (repository != null) {
                repository.updateStatus(o);
                repository.saveVoid(v);
            }
            store.fireChange();
        });
    }

    /** Wipes all order/receipt/void history (in memory, and in the database if connected). Starts a new shift. */
    public void clearHistory() {
        orders.clear();
        voidLog.clear();
        orderCounter = 101;
        if (repository != null) repository.clearAll();
        store.fireChange();
    }

    public int orderRevenueToday() {
        return orders.stream().filter(o -> !o.isVoided()).mapToInt(Order::getTotal).sum();
    }

    private String timeNow() {
        return LocalTime.now().format(TIME_FMT);
    }
}
