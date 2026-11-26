package com.larrystapsihan.ordering.data;

import com.larrystapsihan.ordering.model.Dish;
import java.util.ArrayList;
import java.util.List;

/**
 * The menu: every dish (name, price, optional photo, category), the list of
 * food categories, and the change-listener list that tells the screens to
 * refresh.
 *
 * The menu starts EMPTY - the admin adds categories and items in
 * "Manage Menu". With a repository, everything is saved on this device and
 * loaded again at startup.
 *
 * Knows nothing about Orders or the UI. Screens register a listener with
 * addChangeListener() and are told to refresh whenever fireChange() runs.
 */
public class MenuStore {

    private final List<Dish> menu = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final OrderRepository repository;   // nullable - then nothing is saved

    /** An empty menu that is not saved anywhere. */
    public MenuStore() {
        this(null);
    }

    /** The saved menu is loaded now, and every change is saved. */
    public MenuStore(OrderRepository repository) {
        this.repository = repository;
        if (repository != null) {
            menu.addAll(repository.loadMenu());
            categories.addAll(repository.loadCategories());
        }
    }

    public List<Dish> getMenu() { return menu; }

    public List<String> getCategories() { return categories; }

    /** Finds a menu dish by id, or null. */
    public Dish dishById(String dishId) {
        for (Dish d : menu) {
            if (d.getId().equals(dishId)) return d;
        }
        return null;
    }

    // ============================================================ menu items

    public boolean addMenuItem(String name, int price) {
        return addMenuItem(name, price, null, "");
    }

    public boolean addMenuItem(String name, int price, String photoUri) {
        return addMenuItem(name, price, photoUri, "");
    }

    /**
     * Adds a new menu item. photoUri may be null (the card shows "No photo");
     * category may be "" for none. Returns false for an invalid name or price.
     */
    public boolean addMenuItem(String name, int price, String photoUri, String category) {
        if (name == null || name.isBlank() || price <= 0) return false;
        Dish dish = new Dish(uniqueId(name), name.trim(), price, photoUri, validCategory(category));
        menu.add(dish);
        if (repository != null) repository.saveDish(dish, menu.size() - 1);
        fireChange();
        return true;
    }

    /** Changes an existing item's name, price, photo and category. Returns false if invalid. */
    public boolean updateMenuItem(String id, String name, int price, String photoUri, String category) {
        int index = indexOf(id);
        if (index < 0 || name == null || name.isBlank() || price <= 0) return false;
        Dish updated = menu.get(index).with(name.trim(), price, photoUri, validCategory(category));
        menu.set(index, updated);
        if (repository != null) repository.saveDish(updated, index);
        fireChange();
        return true;
    }

    /** Removes an item from the menu. Receipts of past orders are not affected. */
    public boolean removeMenuItem(String id) {
        int index = indexOf(id);
        if (index < 0) return false;
        menu.remove(index);
        if (repository != null) {
            repository.deleteDish(id);
            for (int i = index; i < menu.size(); i++) repository.saveDish(menu.get(i), i);   // keep the order
        }
        fireChange();
        return true;
    }

    private int indexOf(String id) {
        for (int i = 0; i < menu.size(); i++) {
            if (menu.get(i).getId().equals(id)) return i;
        }
        return -1;
    }

    // ============================================================ categories

    /** Adds a food category. False if blank or it already exists (any capitalization). */
    public boolean addCategory(String name) {
        String n = clean(name);
        if (n.isEmpty() || hasCategory(n)) return false;
        categories.add(n);
        if (repository != null) repository.saveCategory(n, categories.size() - 1);
        fireChange();
        return true;
    }

    /** Renames a category; its items follow. False if invalid or the new name is taken. */
    public boolean renameCategory(String oldName, String newName) {
        String n = clean(newName);
        int index = categories.indexOf(oldName);
        if (index < 0 || n.isEmpty()) return false;
        if (!n.equalsIgnoreCase(oldName) && hasCategory(n)) return false;
        categories.set(index, n);
        for (int i = 0; i < menu.size(); i++) {
            Dish d = menu.get(i);
            if (d.getCategory().equals(oldName)) menu.set(i, d.with(d.getName(), d.getPrice(), d.getPhotoUri(), n));
        }
        if (repository != null) repository.renameCategory(oldName, n);
        fireChange();
        return true;
    }

    /** Deletes a category. Its items stay on the menu, with no category. */
    public boolean removeCategory(String name) {
        if (!categories.remove(name)) return false;
        for (int i = 0; i < menu.size(); i++) {
            Dish d = menu.get(i);
            if (d.getCategory().equals(name)) menu.set(i, d.with(d.getName(), d.getPrice(), d.getPhotoUri(), ""));
        }
        if (repository != null) {
            repository.deleteCategory(name);
            for (int i = 0; i < categories.size(); i++) repository.saveCategory(categories.get(i), i);
        }
        fireChange();
        return true;
    }

    private boolean hasCategory(String name) {
        for (String c : categories) if (c.equalsIgnoreCase(name)) return true;
        return false;
    }

    private String validCategory(String category) {
        return category != null && categories.contains(category) ? category : "";
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    // ============================================================ helpers

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
