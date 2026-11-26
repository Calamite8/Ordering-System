package com.larrystapsihan.ordering.service;

import com.larrystapsihan.ordering.data.MenuStore;
import com.larrystapsihan.ordering.data.OrderRepository;
import com.larrystapsihan.ordering.model.Dish;
import com.larrystapsihan.ordering.model.Order;
import com.larrystapsihan.ordering.model.OrderItem;
import com.larrystapsihan.ordering.model.OrderStatus;
import com.larrystapsihan.ordering.model.VoidRecord;
import java.time.LocalDateTime;
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

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy hh:mm a");

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
     * Cashier order: turns the cart (dishId -> qty) into an Order that is paid
     * on the spot and goes straight to the kitchen.
     * Returns null if the cart is empty or holds an unknown dish.
     */
    public Order finalizeOrder(Map<String, Integer> cart, String type, String table, String payMethod) {
        Order order = createOrder(cart, type, table, payMethod);
        if (order == null) return null;
        order.setAmountPaid(order.getTotal());
        return register(order);
    }

    /**
     * Customer self-order: saved as "Waiting for Payment". It does NOT reach
     * the kitchen until the cashier confirms the payment.
     */
    public Order placeCustomerOrder(Map<String, Integer> cart, String type, String table) {
        Order order = createOrder(cart, type, table, "Cash");
        if (order == null) return null;
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        return register(order);
    }

    /** Orders waiting for payment, oldest first. */
    public List<Order> getPending() {
        List<Order> list = new ArrayList<>(orders.stream().filter(Order::isPending).toList());
        java.util.Collections.reverse(list);
        return list;
    }

    /**
     * The cashier took cash. Returns false (and changes nothing) if the order
     * is not waiting for payment or the amount is less than the total.
     * On success the order goes to the Kitchen Queue.
     */
    public boolean confirmPayment(int orderId, int amountPaid) {
        Order o = find(orderId);
        if (o == null || !o.isPending() || amountPaid < o.getTotal()) return false;
        o.setAmountPaid(amountPaid);
        o.setStatus(OrderStatus.IN_KITCHEN);
        if (repository != null) repository.updateStatus(o);
        store.fireChange();
        return true;
    }

    /** The kitchen finished the food: it can be picked up. */
    public void markReady(int orderId) {
        Order o = find(orderId);
        if (o == null || o.getStatus() != OrderStatus.IN_KITCHEN || o.isVoided()) return;
        o.setStatus(OrderStatus.READY);
        if (repository != null) repository.updateStatus(o);
        store.fireChange();
    }

    /** Throws away an unpaid customer order (the customer left). Kept in the void log. */
    public void cancelPending(int orderId) {
        Order o = find(orderId);
        if (o != null && o.isPending()) voidOrder(orderId, "Cancelled before payment");
    }

    private Order find(int orderId) {
        return orders.stream().filter(o -> o.getId() == orderId).findFirst().orElse(null);
    }

    private Order register(Order order) {
        orders.add(0, order);
        if (repository != null) repository.save(order);
        store.fireChange();
        return order;
    }

    private Order createOrder(Map<String, Integer> cart, String type, String table, String payMethod) {
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

        return new Order(orderCounter++, type, table, payMethod, items, total, timeNow());
    }

    /** Marks a kitchen-queue order as delivered. */
    public void markServed(int orderId) {
        orders.stream().filter(o -> o.getId() == orderId && o.isActive()).findFirst().ifPresent(o -> {
            o.setServed(true);
            if (repository != null) repository.updateStatus(o);
            store.fireChange();
        });
    }

    /** Marks every order on the Kitchen Queue (paid, not yet served) as served in one go. */
    public void clearActiveQueue() {
        for (Order o : orders) {
            if (o.isActive()) {
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
        return orders.stream().filter(o -> !o.isVoided() && o.getStatus() != OrderStatus.PENDING_PAYMENT)
                .mapToInt(Order::getTotal).sum();
    }

    /**
     * Reads an order/void time saved by timeNow() back into a date and time.
     * Returns null for older records that only saved the time of day.
     */
    public static LocalDateTime parseTime(String text) {
        try {
            return LocalDateTime.parse(text, TIME_FMT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String timeNow() {
        return LocalDateTime.now().format(TIME_FMT);
    }
}
