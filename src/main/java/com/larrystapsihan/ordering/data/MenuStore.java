package com.larrystapsihan.ordering.data;

import com.larrystapsihan.ordering.model.Dish;
import java.util.ArrayList;
import java.util.List;

/**
 * The menu: every dish with its name, price and optional photo, plus the
 * change-listener list that tells the screens to refresh.
 *
 * The menu starts EMPTY - the admin adds items with "+ Add New Item". With a
 * repository, items are saved on this device and loaded again at startup.
 *
 * Knows nothing about Orders or the UI. Screens register a listener with
 * addChangeListener() and are told to refresh whenever fireChange() runs.
 */
public class MenuStore {

    private final List<Dish> menu = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final OrderRepository repository;   // nullable - then the menu is not saved

    /** An empty menu that is not saved anywhere. */
    public MenuStore() {
        this(null);
    }

    /** The saved menu is loaded now, and every new item is saved. */
    public MenuStore(OrderRepository repository) {
        this.repository = repository;
        if (repository != null) menu.addAll(repository.loadMenu());
    }

    public List<Dish> getMenu() { return menu; }

    /** Finds a menu dish by id, or null. */
    public Dish dishById(String dishId) {
        for (Dish d : menu) {
            if (d.getId().equals(dishId)) return d;
        }
        return null;
    }

    public boolean addMenuItem(String name, int price) {
        return addMenuItem(name, price, null);
    }

    /**
     * Adds a new menu item (the "Add New Item" button). photoUri may be null,
     * in which case the card shows "No photo". Returns false for an invalid
     * name or price.
     */
    public boolean addMenuItem(String name, int price, String photoUri) {
        if (name == null || name.isBlank() || price <= 0) return false;
        Dish dish = new Dish(uniqueId(name), name.trim(), price, photoUri);
        menu.add(dish);
        if (repository != null) repository.saveDish(dish, menu.size() - 1);
        fireChange();
        return true;
    }

    /** "Halo-Halo" -> "halohalo"; adds 2, 3... if that id is already used. */
    private String uniqueId(String name) {
        String base = name.trim().toLowerCase().replaceAll("[^a-z0-9]+", "");
        if (base.isEmpty()) base = "item";
        String id = base;
        int suffix = 2;
        while (dishById(id) != null) {
            id = base + suffix++;
        }
        return id;
    }

    public void addChangeListener(Runnable r) { listeners.add(r); }

    /** Tells every registered screen to refresh (menu or order changed). */
    public void fireChange() {
        for (Runnable r : listeners) r.run();
    }
}
