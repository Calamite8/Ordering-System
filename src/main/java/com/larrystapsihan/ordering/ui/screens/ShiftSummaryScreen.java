package com.larrystapsihan.ordering.ui.screens;

import com.larrystapsihan.ordering.model.Order;
import com.larrystapsihan.ordering.model.OrderItem;
import com.larrystapsihan.ordering.model.VoidRecord;
import com.larrystapsihan.ordering.service.OrderService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Shift Summary page (admin only - its sidebar tab appears after Settings >
 * Admin Login). This is the system's sales report: pick Daily, Weekly or
 * Monthly and it shows the totals for that period, a summary of the customer
 * orders, the best-selling menu items and the voided orders.
 *
 * Voided orders are not counted as sales. Voiding itself is done from the
 * Receipts page.
 */
public class ShiftSummaryScreen implements Screen {

    /** The report periods. "start" is the first day included (up to today). */
    private enum Period {
        DAILY("Daily", "today"), WEEKLY("Weekly", "this week (Mon-Sun)"), MONTHLY("Monthly", "this month");

        final String label, phrase;
        Period(String label, String phrase) { this.label = label; this.phrase = phrase; }

        LocalDate start(LocalDate today) {
            return switch (this) {
                case DAILY -> today;
                case WEEKLY -> today.with(DayOfWeek.MONDAY);
                case MONTHLY -> today.withDayOfMonth(1);
            };
        }
    }

    private static final int BEST_SELLER_COUNT = 5;

    private final OrderService orderService;
    private final HBox statRow = new HBox(14);
    private final VBox bestRows = new VBox(0);
    private final VBox orderRows = new VBox(0);
    private final VBox voidRows = new VBox(0);
    private final Label periodNote = new Label();
    private final ToggleGroup periodGroup = new ToggleGroup();
    private final BorderPane view;
    private Period period = Period.DAILY;

    public ShiftSummaryScreen(OrderService orderService) {
        this.orderService = orderService;

        HBox periodBar = new HBox(8);
        for (Period p : Period.values()) {
            ToggleButton b = new ToggleButton(p.label);
            b.getStyleClass().addAll("toggle-btn", "category-chip");
            b.setToggleGroup(periodGroup);
            b.setSelected(p == period);
            b.setOnAction(e -> { period = p; refresh(); });
            periodBar.getChildren().add(b);
        }
        // tapping the selected period must not clear the choice
        periodGroup.selectedToggleProperty().addListener((obs, was, now) -> { if (now == null) periodGroup.selectToggle(was); });
        periodNote.getStyleClass().add("log-row-label");
        HBox top = new HBox(16, periodBar, periodNote);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(4, 0, 12, 0));

        statRow.setPadding(new Insets(0, 0, 14, 0));

        HBox panels = new HBox(16,
                logPanel("Order Summary", orderRows, 2),
                new VBox(16, logPanel("Best-Selling Items", bestRows, 0), logPanel("Voided Orders", voidRows, 0)));
        HBox.setHgrow(panels.getChildren().get(1), Priority.ALWAYS);

        view = ScreenLayout.page("Shift Summary",
                "Sales report by day, week or month: totals, customer orders and best-selling items.",
                null, ScreenLayout.transparentScroll(new VBox(0, top, statRow, panels)));
    }

    @Override
    public Node getView() { return view; }

    @Override
    public void refresh() {
        LocalDate today = LocalDate.now();
        LocalDate start = period.start(today);
        periodNote.setText("Showing " + period.phrase + ", from " + start + " to " + today);

        // Orders in this period, newest first. Older records with no date count as today.
        List<Order> inPeriod = new ArrayList<>();
        for (Order o : orderService.getOrders()) {
            if (!o.isPending() && inPeriod(OrderService.parseTime(o.getTime()), start, today)) inPeriod.add(o);
        }
        List<Order> sales = inPeriod.stream().filter(o -> !o.isVoided()).toList();
        int revenue = sales.stream().mapToInt(Order::getTotal).sum();
        long dineIn = sales.stream().filter(o -> o.getType().equals("Dine-in")).count();
        long takeOut = sales.size() - dineIn;
        List<VoidRecord> voids = new ArrayList<>();
        for (VoidRecord v : orderService.getVoidLog()) {
            if (inPeriod(OrderService.parseTime(v.time()), start, today)) voids.add(v);
        }

        statRow.getChildren().setAll(
                statBox("Orders", String.valueOf(sales.size())),
                statBox("Total Sales", "₱" + revenue),
                statBox("Average Order", "₱" + (sales.isEmpty() ? 0 : Math.round((double) revenue / sales.size()))),
                statBox("Dine-in / Take-out", dineIn + " / " + takeOut),
                statBox("Voided Orders", String.valueOf(voids.size())));
        for (Node box : statRow.getChildren()) HBox.setHgrow(box, Priority.ALWAYS);

        // ---- customer order summary ----
        orderRows.getChildren().clear();
        if (inPeriod.isEmpty()) orderRows.getChildren().add(emptyLabel("No orders " + period.phrase + "."));
        for (Order o : inPeriod) orderRows.getChildren().add(orderRow(o));

        // ---- best sellers (by quantity sold, voided orders excluded) ----
        Map<String, int[]> tally = new LinkedHashMap<>();   // name -> {qty, sales}
        for (Order o : sales) {
            for (OrderItem it : o.getItems()) {
                int[] t = tally.computeIfAbsent(it.name(), k -> new int[2]);
                t[0] += it.qty();
                t[1] += it.lineTotal();
            }
        }
        List<Map.Entry<String, int[]>> ranked = new ArrayList<>(tally.entrySet());
        ranked.sort((a, b) -> b.getValue()[0] != a.getValue()[0]
                ? Integer.compare(b.getValue()[0], a.getValue()[0])
                : Integer.compare(b.getValue()[1], a.getValue()[1]));
        bestRows.getChildren().clear();
        if (ranked.isEmpty()) bestRows.getChildren().add(emptyLabel("No sales " + period.phrase + "."));
        for (int i = 0; i < Math.min(BEST_SELLER_COUNT, ranked.size()); i++) {
            var e = ranked.get(i);
            Label rank = new Label(String.valueOf(i + 1));
            rank.getStyleClass().add("rank-num");
            Label name = new Label(e.getKey());
            name.getStyleClass().add("rank-name");
            HBox.setHgrow(name, Priority.ALWAYS);
            name.setMaxWidth(Double.MAX_VALUE);
            Label qty = new Label(e.getValue()[0] + " sold · ₱" + e.getValue()[1]);
            qty.getStyleClass().add("log-row-label");
            HBox row = new HBox(10, rank, name, qty);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(5, 0, 5, 0));
            bestRows.getChildren().add(row);
        }

        // ---- voided orders ----
        voidRows.getChildren().clear();
        if (voids.isEmpty()) voidRows.getChildren().add(emptyLabel("No voided orders " + period.phrase + "."));
        for (VoidRecord v : voids) {
            Label left = new Label("#" + v.orderId() + " — " + v.reason());
            left.getStyleClass().add("log-row-label");
            Label right = new Label(v.time());
            right.getStyleClass().add("log-row-label");
            HBox row = ScreenLayout.leftRight(left, right);
            row.setPadding(new Insets(4, 0, 4, 0));
            voidRows.getChildren().add(row);
        }
    }

    // ---- building blocks ----

    /** True if the time falls from start through today; a time with no date (older record) counts as today. */
    private static boolean inPeriod(LocalDateTime t, LocalDate start, LocalDate today) {
        if (t == null) return true;
        LocalDate d = t.toLocalDate();
        return !d.isBefore(start) && !d.isAfter(today);
    }

    private VBox logPanel(String title, VBox rows, int grow) {
        Label heading = new Label(title);
        heading.getStyleClass().add("log-panel-title");
        VBox panel = new VBox(8, heading, rows);
        panel.getStyleClass().add("log-panel");
        if (grow > 0) { panel.setPrefWidth(560); HBox.setHgrow(panel, Priority.ALWAYS); }
        else panel.setMaxWidth(Double.MAX_VALUE);
        return panel;
    }

    private Label emptyLabel(String text) {
        Label empty = new Label(text);
        empty.getStyleClass().add("log-empty");
        return empty;
    }

    private VBox statBox(String label, String value) {
        Label l = new Label(label);
        l.getStyleClass().add("stat-label");
        Label v = new Label(value);
        v.getStyleClass().add("stat-value");
        VBox box = new VBox(4, l, v);
        box.getStyleClass().add("stat-box");
        return box;
    }

    private HBox orderRow(Order o) {
        Label id = new Label("#" + o.getId());
        id.getStyleClass().add("rank-num");
        String meta = o.getType() + (o.getTable() != null && !o.getTable().isBlank() ? " · T" + o.getTable() : "")
                + " — ₱" + o.getTotal();
        Label metaLbl = new Label(meta);
        metaLbl.getStyleClass().add("rank-name");
        Label subLbl = new Label(o.getTime() + " · " + o.getPayMethod() + (o.isVoided() ? " · VOIDED" : ""));
        subLbl.getStyleClass().add("log-row-label");
        VBox box = new VBox(2, metaLbl, subLbl);
        HBox row = new HBox(10, id, box);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 0, 6, 0));
        return row;
    }
}
