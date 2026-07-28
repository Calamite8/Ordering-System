package com.larrystapsihan.inventory;

/** A punched sale ticket entry. */
record SaleRecord(String menuId, String name, String time) { }

/** A stock delivery entry. */
record StockInRecord(String name, double qty, String unit, String time) { }
