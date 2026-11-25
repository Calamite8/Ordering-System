package com.larrystapsihan.ordering;

/** A line item inside a finalized Order. */
record OrderItem(String dishId, String name, int qty, int lineTotal) { }

/** A voided-order entry (kept for the record; the sale itself is not reversed). */
record VoidRecord(int orderId, String reason, String time) { }
