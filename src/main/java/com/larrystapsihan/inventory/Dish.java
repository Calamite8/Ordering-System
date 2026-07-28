package com.larrystapsihan.inventory;

import java.util.LinkedHashMap;
import java.util.Map;

/** A menu item ("silog") with a recipe of ingredient-id to amount-per-order. */
public class Dish {

    private final String id;
    private final String name;
    private final int price;
    private final Map<String, Double> recipe;

    public Dish(String id, String name, int price, Map<String, Double> recipe) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.recipe = new LinkedHashMap<>(recipe);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getPrice() { return price; }
    public Map<String, Double> getRecipe() { return recipe; }
}
