package com.larrystapsihan.ordering.model;

/** A voided-order entry, kept for the record. */
public record VoidRecord(int orderId, String reason, String time) { }
