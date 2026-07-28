package com.larrystapsihan.inventory;

/** A stock item on the board (e.g. Rice, Egg, Cooking Oil). */
public class Ingredient {

    public enum Status { FRESH, WARN, CRITICAL }

    private final String id;
    private final String name;
    private final String unit;
    private double qty;
    private final double threshold;

    public Ingredient(String id, String name, String unit, double qty, double threshold) {
        this.id = id;
        this.name = name;
        this.unit = unit;
        this.qty = qty;
        this.threshold = threshold;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getUnit() { return unit; }
    public double getQty() { return qty; }
    public double getThreshold() { return threshold; }

    void setQty(double qty) { this.qty = qty; }

    /** Mirrors statusOf() in the prototype: <= threshold is critical, <= 2x threshold is warn. */
    public Status getStatus() {
        if (qty <= threshold) return Status.CRITICAL;
        if (qty <= threshold * 2) return Status.WARN;
        return Status.FRESH;
    }

    /** Gauge fill percentage, capped at 100 (matches (qty / (threshold*3)) * 100 in the prototype). */
    public double getGaugePercent() {
        double pct = (qty / (threshold * 3)) * 100.0;
        return Math.min(100.0, Math.max(0.0, pct));
    }
}
