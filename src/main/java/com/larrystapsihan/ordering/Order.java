package com.larrystapsihan.ordering;

import java.util.List;

/**
 * A finalized cashier order: one or more dishes, dine-in or take-out,
 * tracked from "sent to kitchen" through "served" and (optionally) voided.
 * Deducting stock for the order's dishes happens through
 * InventoryStore.punchTicket() in the inventory module - this class only
 * holds order-level bookkeeping (type, table, payment, status).
 */
public class Order {

    private final int id;
    private final String type;      // "Dine-in" or "Take-out"
    private final String table;     // table number, or null for take-out
    private final String payMethod; // "Cash" or "E-wallet"
    private final List<OrderItem> items;
    private final int total;
    private final String time;

    private boolean served = false;
    private boolean voided = false;

    public Order(int id, String type, String table, String payMethod,
                 List<OrderItem> items, int total, String time) {
        this.id = id;
        this.type = type;
        this.table = table;
        this.payMethod = payMethod;
        this.items = items;
        this.total = total;
        this.time = time;
    }

    public int getId() { return id; }
    public String getType() { return type; }
    public String getTable() { return table; }
    public String getPayMethod() { return payMethod; }
    public List<OrderItem> getItems() { return items; }
    public int getTotal() { return total; }
    public String getTime() { return time; }

    public boolean isServed() { return served; }
    public void setServed(boolean served) { this.served = served; }

    public boolean isVoided() { return voided; }
    public void setVoided(boolean voided) { this.voided = voided; }
}
