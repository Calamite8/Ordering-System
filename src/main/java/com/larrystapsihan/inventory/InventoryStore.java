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
        for (Map.Entry<String, Double> e : dish.getRecipe().entrySet()) {
            Ingredient ing = ingredientById(e.getKey());
            if (ing == null || ing.getQty() < e.getValue()) return false;
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

    private void fireChange() {
        for (Runnable r : listeners) r.run();
    }
}
