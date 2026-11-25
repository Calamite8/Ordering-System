package com.larrystapsihan.ordering;

import com.larrystapsihan.inventory.Dish;
import com.larrystapsihan.inventory.InventoryStore;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Order-domain state and operations, split out of InventoryStore so that
 * ordering can depend on inventory without inventory needing to know about
 * Order/OrderItem/VoidRecord (which would create a circular module
 * dependency between the two Maven modules).
 *
 * Deduction itself is never reimplemented here - finalizeOrder validates
 * with store.canFulfill(dish, qty) and deducts with store.punchTicket(id),
 * the exact same path "Record a Sale" uses, so Reports stays accurate
 * regardless of which tab a sale came from.
 *
 * Persistence (repository) is optional - see the no-repository constructor.
 * When present, order history is loaded from it on construction and every
 * mutation is mirrored to it; when absent, everything is in-memory only.
 */
public class OrderService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    private final InventoryStore store;
    private final OrderRepository repository; // nullable - no database is a valid mode
    private final List<Order> orders = new ArrayList<>();
    private final List<VoidRecord> voidLog = new ArrayList<>();
    private int orderCounter = 101;

    /** In-memory only - no order history persistence. */
    public OrderService(InventoryStore store) {
        this(store, null);
    }

    /** With a repository, existing order history is loaded immediately and every change is saved. */
    public OrderService(InventoryStore store, OrderRepository repository) {
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
     * Validates the whole cart can be fulfilled, then deducts stock for every
     * item via store.punchTicket() once per unit ordered. Returns null if any
     * item's quantity exceeds current stock (nothing is deducted in that case).
     */
    public Order finalizeOrder(Map<String, Integer> cart, String type, String table, String payMethod) {
        List<OrderItem> items = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, Integer> e : cart.entrySet()) {
            if (e.getValue() <= 0) continue;
            Dish dish = store.dishById(e.getKey());
            if (dish == null || !store.canFulfill(dish, e.getValue())) return null;
        }
        for (Map.Entry<String, Integer> e : cart.entrySet()) {
            if (e.getValue() <= 0) continue;
            Dish dish = store.dishById(e.getKey());
            int lineTotal = dish.getPrice() * e.getValue();
            for (int i = 0; i < e.getValue(); i++) store.punchTicket(dish.getId());
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

    /** Voids an order for accounting purposes. Stock already deducted is NOT restored. */
    public void requestVoid(int orderId, String reason) {
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
