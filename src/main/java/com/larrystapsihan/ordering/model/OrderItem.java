package com.larrystapsihan.ordering.model;

/** One line inside an Order: a dish, how many, and the line total. */
public record OrderItem(String dishId, String name, int qty, int lineTotal) { }
