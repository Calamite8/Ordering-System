package com.larrystapsihan.ordering.model;

/** A one-line entry in the sales log: which dish was punched and when. */
public record SaleRecord(String menuId, String name, String time) { }
