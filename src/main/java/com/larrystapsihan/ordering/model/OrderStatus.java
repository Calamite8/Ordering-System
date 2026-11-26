package com.larrystapsihan.ordering.model;

/**
 * Where an order is in its life. Customer self-orders start at PENDING_PAYMENT
 * and wait at the counter; cashier orders and paid orders start at IN_KITCHEN.
 */
public enum OrderStatus {
    PENDING_PAYMENT("Waiting for Payment"),
    IN_KITCHEN("In Kitchen"),
    READY("Ready"),
    SERVED("Served");

    private final String label;

    OrderStatus(String label) { this.label = label; }

    public String label() { return label; }

    /** Reads a saved status; anything unknown (or blank, from an older database) is null. */
    public static OrderStatus fromSaved(String text) {
        if (text == null) return null;
        for (OrderStatus s : values()) if (s.name().equals(text)) return s;
        return null;
    }
}
