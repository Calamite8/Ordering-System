package com.larrystapsihan.ordering;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC-backed persistence for order history (orders, their line items, and
 * void records). Requires a MySQL driver (mysql-connector-j) on the
 * runtime classpath - declared as a runtime-scope dependency in
 * ordering-app's pom.xml.
 *
 * If no database is reachable, OrderMain catches the SQLException from the
 * constructor and runs with in-memory-only order tracking instead (see
 * OrderService's no-repository constructor) - a database is optional, not
 * required, to run the app.
 *
 * Schema (created automatically on first connect if missing):
 *   orders(id, type, table_no, pay_method, total, order_time, served, voided)
 *   order_items(order_id, dish_id, name, qty, line_total)
 *   order_voids(order_id, reason, void_time)
 */
public class OrderRepository {

    private final String url;
    private final String user;
    private final String password;

    public OrderRepository(String url, String user, String password) throws SQLException {
        this.url = url;
        this.user = user;
        this.password = password;
        try (Connection c = connect()) {
            createSchema(c);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private void createSchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS orders (
                        id INT PRIMARY KEY,
                        type VARCHAR(20) NOT NULL,
                        table_no VARCHAR(10),
                        pay_method VARCHAR(20) NOT NULL,
                        total INT NOT NULL,
                        order_time VARCHAR(20) NOT NULL,
                        served BOOLEAN NOT NULL DEFAULT FALSE,
                        voided BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS order_items (
                        order_id INT NOT NULL,
                        dish_id VARCHAR(40) NOT NULL,
                        name VARCHAR(80) NOT NULL,
                        qty INT NOT NULL,
                        line_total INT NOT NULL,
                        FOREIGN KEY (order_id) REFERENCES orders(id)
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS order_voids (
                        order_id INT NOT NULL,
                        reason VARCHAR(200) NOT NULL,
                        void_time VARCHAR(20) NOT NULL
                    )
                    """);
        }
    }

    /** Inserts a newly finalized order and its line items. */
    public void save(Order order) {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO orders (id, type, table_no, pay_method, total, order_time, served, voided) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, order.getId());
                ps.setString(2, order.getType());
                ps.setString(3, order.getTable());
                ps.setString(4, order.getPayMethod());
                ps.setInt(5, order.getTotal());
                ps.setString(6, order.getTime());
                ps.setBoolean(7, order.isServed());
                ps.setBoolean(8, order.isVoided());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO order_items (order_id, dish_id, name, qty, line_total) VALUES (?, ?, ?, ?, ?)")) {
                for (OrderItem item : order.getItems()) {
                    ps.setInt(1, order.getId());
                    ps.setString(2, item.dishId());
                    ps.setString(3, item.name());
                    ps.setInt(4, item.qty());
                    ps.setInt(5, item.lineTotal());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save order #" + order.getId(), e);
        }
    }

    /** Updates just the served/voided flags for an order already saved. */
    public void updateStatus(Order order) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE orders SET served = ?, voided = ? WHERE id = ?")) {
            ps.setBoolean(1, order.isServed());
            ps.setBoolean(2, order.isVoided());
            ps.setInt(3, order.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update order #" + order.getId(), e);
        }
    }

    public void saveVoid(VoidRecord v) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO order_voids (order_id, reason, void_time) VALUES (?, ?, ?)")) {
            ps.setInt(1, v.orderId());
            ps.setString(2, v.reason());
            ps.setString(3, v.time());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save void record for order #" + v.orderId(), e);
        }
    }

    /** Loads every saved order (with its items), most recent first. */
    public List<Order> loadOrders() {
        Map<Integer, List<OrderItem>> itemsByOrder = new LinkedHashMap<>();
        try (Connection c = connect();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT order_id, dish_id, name, qty, line_total FROM order_items")) {
            while (rs.next()) {
                itemsByOrder.computeIfAbsent(rs.getInt("order_id"), k -> new ArrayList<>())
                        .add(new OrderItem(rs.getString("dish_id"), rs.getString("name"),
                                rs.getInt("qty"), rs.getInt("line_total")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load order items", e);
        }

        List<Order> orders = new ArrayList<>();
        try (Connection c = connect();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id, type, table_no, pay_method, total, order_time, served, voided "
                             + "FROM orders ORDER BY id DESC")) {
            while (rs.next()) {
                int id = rs.getInt("id");
                Order order = new Order(id, rs.getString("type"), rs.getString("table_no"),
                        rs.getString("pay_method"), itemsByOrder.getOrDefault(id, List.of()),
                        rs.getInt("total"), rs.getString("order_time"));
                order.setServed(rs.getBoolean("served"));
                order.setVoided(rs.getBoolean("voided"));
                orders.add(order);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load orders", e);
        }
        return orders;
    }

    public List<VoidRecord> loadVoidLog() {
        List<VoidRecord> log = new ArrayList<>();
        try (Connection c = connect();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT order_id, reason, void_time FROM order_voids ORDER BY void_time DESC")) {
            while (rs.next()) {
                log.add(new VoidRecord(rs.getInt("order_id"), rs.getString("reason"), rs.getString("void_time")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load void log", e);
        }
        return log;
    }

    /** Wipes all saved order history - used by the "Clear Receipts" action. */
    public void clearAll() {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM order_items");
            st.executeUpdate("DELETE FROM order_voids");
            st.executeUpdate("DELETE FROM orders");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear order history", e);
        }
    }
}
