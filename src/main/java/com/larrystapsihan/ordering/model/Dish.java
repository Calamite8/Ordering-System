package com.larrystapsihan.ordering.model;

/**
 * A menu item (e.g. Tapsilog): id, name, price and an optional photo.
 * photoUri is a file: URI, or null for "no photo" (the menu card then shows
 * a "No photo" placeholder).
 */
public class Dish {

    private final String id;
    private final String name;
    private final int price;
    private final String photoUri;

    public Dish(String id, String name, int price) {
        this(id, name, price, null);
    }

    public Dish(String id, String name, int price, String photoUri) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.photoUri = photoUri;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getPrice() { return price; }
    public String getPhotoUri() { return photoUri; }
}
