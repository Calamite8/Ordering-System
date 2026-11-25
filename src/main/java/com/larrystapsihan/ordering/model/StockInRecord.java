package com.larrystapsihan.ordering.model;

/** A one-line entry in the stock-in log: a delivery of an ingredient and when it arrived. */
public record StockInRecord(String name, double qty, String unit, String time) { }
