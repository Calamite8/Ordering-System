package com.larrystapsihan.inventory;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds all in-memory state for the prototype (ingredients, menu, sales log,
 * stock-in log) and the operations that mutate it. This is a direct port of
 * the JS data/logic in inventory_prototype.html.
 *
 * This class intentionally knows nothing about Orders - that logic lives in
 * the "ordering" module's OrderService, which depends on this class (not
 * the other way around) to avoid a circular module dependency.
 *
 * NOTE: this is UI-only, in-memory state. It resets every time the app
 * restarts. Wiring this to MySQL (with a fallback store) is the next
 * roadmap item - see the Database task.
 */
public class InventoryStore {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    private final List<Ingredient> ingredients = new ArrayList<>();
    private final List<Dish> menu = new ArrayList<>();
    private final List<SaleRecord> salesLog = new ArrayList<>();
    private final List<StockInRecord> stockInLog = new ArrayList<>();
    private final Map<String, Integer> salesCount = new LinkedHashMap<>();

    private final List<Runnable> listeners = new ArrayList<>();

    public InventoryStore() {
        seedIngredients();
        seedMenu();
    }

    private void seedIngredients() {
        ingredients.add(new Ingredient("rice", "Rice", "kg", 25, 8));
        ingredients.add(new Ingredient("tapa", "Cured Beef (Tapa)", "kg", 6, 2));
        ingredients.add(new Ingredient("porkchop", "Pork Chop", "kg", 5, 2));
        ingredients.add(new Ingredient("hotdog", "Hotdog", "pcs", 60, 20));
        ingredients.add(new Ingredient("bangus", "Milkfish (Bangus)", "pcs", 10, 3));
        ingredients.add(new Ingredient("cornedbeef", "Corned Beef", "kg", 4, 1.5));
        ingredients.add(new Ingredient("egg", "Egg", "pcs", 120, 30));
        ingredients.add(new Ingredient("garlic", "Garlic", "kg", 3, 1));
        ingredients.add(new Ingredient("cookingoil", "Cooking Oil", "L", 8, 2));
        ingredients.add(new Ingredient("vinegar", "Vinegar", "L", 4, 1));
        ingredients.add(new Ingredient("onion", "Onion", "kg", 4, 1));
        ingredients.add(new Ingredient("longganisa", "Longganisa", "pcs", 40, 12));
        ingredients.add(new Ingredient("chicken", "Fried Chicken", "pcs", 20, 6));
    }

    private void seedMenu() {
        menu.add(new Dish("tapsilog", "Tapsilog", 95, Map.of(
                "rice", 0.25, "egg", 1.0, "tapa", 0.15, "garlic", 0.02, "cookingoil", 0.02)));
        menu.add(new Dish("porksilog", "Porksilog", 90, Map.of(
                "rice", 0.25, "egg", 1.0, "porkchop", 0.2, "garlic", 0.02, "cookingoil", 0.02)));
        menu.add(new Dish("hotsilog", "Hotsilog", 65, Map.of(
                "rice", 0.25, "egg", 1.0, "hotdog", 2.0, "cookingoil", 0.01)));
        menu.add(new Dish("bangsilog", "Bangsilog", 85, Map.of(
                "rice", 0.25, "egg", 1.0, "bangus", 1.0, "vinegar", 0.02, "garlic", 0.02)));
        menu.add(new Dish("cornsilog", "Cornsilog", 75, Map.of(
                "rice", 0.25, "egg", 1.0, "cornedbeef", 0.15, "onion", 0.03, "garlic", 0.02)));
        menu.add(new Dish("longsilog", "Longsilog", 80, Map.of(
                "rice", 0.25, "egg", 1.0, "longganisa", 2.0, "garlic", 0.02)));
        menu.add(new Dish("chicksilog", "Chicksilog", 110, Map.of(
                "rice", 0.25, "egg", 1.0, "chicken", 1.0, "garlic", 0.02, "cookingoil", 0.03)));

        // Flat-fee add-ons: an empty recipe means canFulfill()/punchTicket() always
        // succeed and touch no ingredient stock - no special-casing needed elsewhere.
        menu.add(new Dish("extrarice", "Extra Rice", 20, Map.of()));
        menu.add(new Dish("bottledwater", "Bottled Water", 20, Map.of()));
        menu.add(new Dish("icedteapitcher", "Iced Tea (Pitcher)", 60, Map.of()));
    }

    public List<Ingredient> getIngredients() { return ingredients; }
    public List<Dish> getMenu() { return menu; }
    public List<SaleRecord> getSalesLog() { return salesLog; }
    public List<StockInRecord> getStockInLog() { return stockInLog; }
    public Map<String, Integer> getSalesCount() { return salesCount; }

    public Ingredient ingredientById(String id) {
        for (Ingredient ing : ingredients) {
            if (ing.getId().equals(id)) return ing;
        }
        return null;
    }

    public boolean canFulfill(Dish dish) {
        return canFulfill(dish, 1);
    }

    /** Same check as {@link #canFulfill(Dish)}, scaled up for an order-cart quantity. */
    public boolean canFulfill(Dish dish, int qty) {
        for (Map.Entry<String, Double> e : dish.getRecipe().entrySet()) {
            Ingredient ing = ingredientById(e.getKey());
            if (ing == null || ing.getQty() < e.getValue() * qty) return false;
        }
        return true;
    }

    /** Deducts a dish's recipe from stock and logs the sale. Returns false if stock was insufficient. */
    public boolean punchTicket(String dishId) {
        Dish dish = menu.stream().filter(m -> m.getId().equals(dishId)).findFirst().orElse(null);
        if (dish == null || !canFulfill(dish)) return false;

        for (Map.Entry<String, Double> e : dish.getRecipe().entrySet()) {
            Ingredient ing = ingredientById(e.getKey());
            double updated = Math.round((ing.getQty() - e.getValue()) * 1000.0) / 1000.0;
            ing.setQty(updated);
        }
        salesLog.add(0, new SaleRecord(dish.getId(), dish.getName(), timeNow()));
        salesCount.merge(dishId, 1, Integer::sum);
        fireChange();
        return true;
    }

    /** Finds a menu dish by id, or null. Used by ordering's OrderService to price/validate a cart. */
    public Dish dishById(String dishId) {
        return menu.stream().filter(m -> m.getId().equals(dishId)).findFirst().orElse(null);
    }

    /**
     * Adds a new flat-fee menu item at runtime (e.g. via the Order System's
     * "Add Item" button) - same pattern as the seeded extras (Extra Rice,
     * Bottled Water): an empty recipe, so it's always available and never
     * touches ingredient stock. Returns false for an invalid name or price.
     */
    public boolean addMenuItem(String name, int price) {
        if (name == null || name.isBlank() || price <= 0) return false;
        String id = slugify(name);
        menu.add(new Dish(id, name.trim(), price, Map.of()));
        fireChange();
        return true;
    }

    private String slugify(String name) {
        String base = name.trim().toLowerCase().replaceAll("[^a-z0-9]+", "");
        if (base.isEmpty()) base = "item";
        String id = base;
        int suffix = 2;
        while (idInUse(id)) {
            id = base + suffix++;
        }
        return id;
    }

    private boolean idInUse(String id) {
        for (Dish d : menu) {
            if (d.getId().equals(id)) return true;
        }
        return false;
    }

    /** Adds delivered stock to an ingredient and logs it. Returns false for an invalid quantity. */
    public boolean addStock(String ingredientId, double qty) {
        if (qty <= 0) return false;
        Ingredient ing = ingredientById(ingredientId);
        if (ing == null) return false;
        double updated = Math.round((ing.getQty() + qty) * 1000.0) / 1000.0;
        ing.setQty(updated);
        stockInLog.add(0, new StockInRecord(ing.getName(), qty, ing.getUnit(), timeNow()));
        fireChange();
        return true;
    }

    public int totalTicketsToday() { return salesLog.size(); }

    public int totalRevenueToday() {
        int total = 0;
        for (SaleRecord s : salesLog) {
            Dish d = menu.stream().filter(m -> m.getId().equals(s.menuId())).findFirst().orElse(null);
            if (d != null) total += d.getPrice();
        }
        return total;
    }

    public int lowStockCount() {
        int count = 0;
        for (Ingredient ing : ingredients) {
            if (ing.getStatus() != Ingredient.Status.FRESH) count++;
        }
        return count;
    }

    private String timeNow() {
        return LocalTime.now().format(TIME_FMT);
    }

    public void addChangeListener(Runnable r) { listeners.add(r); }

    /**
     * Public so the ordering module can notify all tabs to refresh after an
     * order-only state change (e.g. markServed/requestVoid) that doesn't
     * itself touch ingredient stock.
     */
    public void fireChange() {
        for (Runnable r : listeners) r.run();
    }
}
