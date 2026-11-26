package com.larrystapsihan.ordering.data;

import com.larrystapsihan.ordering.model.Dish;
import com.larrystapsihan.ordering.model.Order;
import com.larrystapsihan.ordering.model.OrderItem;
import com.larrystapsihan.ordering.model.OrderStatus;
import com.larrystapsihan.ordering.model.VoidRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * All saved data lives here, in one SQLite file on this device
 * (~/LarrysTapsihan/ordering.db - no server, nothing to install):
 *
 *   menu_items     the menu (name, price, photo)
 *   orders         every finalized order
 *   order_items    the dishes inside each order
 *   order_voids    the void log
 *   admin_account  the hashed admin login
 *
 * The tables are created automatically the first time the file is opened.
 * Optional in the sense that OrderMain falls back to in-memory-only if the
 * file cannot be opened. Needs the sqlite-jdbc driver at runtime (pom.xml).
 */
public class OrderRepository {

    private final String url;

    public OrderRepository(Path dbFile) throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");   // make sure the driver is registered (also inside the fat jar)
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite driver (sqlite-jdbc) is missing from the classpath", e);
        }
        try {
            Files.createDirectories(dbFile.toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new SQLException("Cannot create the data folder: " + e.getMessage(), e);
        }
        this.url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        try (Connection c = connect()) {
            createSchema(c);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url);
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
            // databases saved by an older version have no status / amount_paid columns yet
            boolean hasStatus = false;
            try (ResultSet cols = st.executeQuery("PRAGMA table_info(orders)")) {
                while (cols.next()) if ("status".equals(cols.getString("name"))) hasStatus = true;
            }
            if (!hasStatus) {
                st.executeUpdate("ALTER TABLE orders ADD COLUMN status TEXT NOT NULL DEFAULT ''");
                st.executeUpdate("ALTER TABLE orders ADD COLUMN amount_paid INTEGER NOT NULL DEFAULT 0");
            }
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
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS menu_items (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        price INTEGER NOT NULL,
                        photo TEXT,
                        sort_order INTEGER NOT NULL,
                        category TEXT NOT NULL DEFAULT ''
                    )
                    """);
            // menus saved by an older version have no category column yet
            boolean hasCategory = false;
            try (ResultSet cols = st.executeQuery("PRAGMA table_info(menu_items)")) {
                while (cols.next()) if ("category".equals(cols.getString("name"))) hasCategory = true;
            }
            if (!hasCategory) st.executeUpdate("ALTER TABLE menu_items ADD COLUMN category TEXT NOT NULL DEFAULT ''");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS categories (
                        name TEXT PRIMARY KEY,
                        sort_order INTEGER NOT NULL
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS admin_account (
                        id INT PRIMARY KEY,
                        username VARCHAR(60) NOT NULL,
                        salt VARCHAR(60) NOT NULL,
                        password_hash VARCHAR(100) NOT NULL
                    )
                    """);
        }
    }

    /** The whole saved menu, in the order the items were added. */
    public List<Dish> loadMenu() {
        List<Dish> menu = new ArrayList<>();
        try (Connection c = connect();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, price, photo, category FROM menu_items ORDER BY sort_order")) {
            while (rs.next()) {
                menu.add(new Dish(rs.getString("id"), rs.getString("name"), rs.getInt("price"),
                        rs.getString("photo"), rs.getString("category")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load the menu", e);
        }
        return menu;
    }

    /** Saves a menu item (adds it, or replaces the one with the same id). */
    public void saveDish(Dish dish, int sortOrder) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "REPLACE INTO menu_items (id, name, price, photo, sort_order, category) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, dish.getId());
            ps.setString(2, dish.getName());
            ps.setInt(3, dish.getPrice());
            ps.setString(4, dish.getPhotoUri());
            ps.setInt(5, sortOrder);
            ps.setString(6, dish.getCategory());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save menu item " + dish.getName(), e);
        }
    }


    /** Removes a menu item. Past orders keep their own copy of the name and price. */
    public void deleteDish(String id) {
        run("DELETE FROM menu_items WHERE id = ?", id);
    }

    /** The saved food categories, in order. */
    public List<String> loadCategories() {
        List<String> list = new ArrayList<>();
        try (Connection c = connect();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM categories ORDER BY sort_order")) {
            while (rs.next()) list.add(rs.getString(1));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load categories", e);
        }
        return list;
    }

    public void saveCategory(String name, int sortOrder) {
        run("REPLACE INTO categories (name, sort_order) VALUES (?, ?)", name, sortOrder);
    }

    /** Renames a category and moves its items to the new name. */
    public void renameCategory(String oldName, String newName) {
        run("UPDATE categories SET name = ? WHERE name = ?", newName, oldName);
        run("UPDATE menu_items SET category = ? WHERE category = ?", newName, oldName);
    }

    /** Deletes a category; its items stay on the menu with no category. */
    public void deleteCategory(String name) {
        run("DELETE FROM categories WHERE name = ?", name);
        run("UPDATE menu_items SET category = '' WHERE category = ?", name);
    }

    private void run(String sql, Object... args) {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Database update failed", e);
        }
    }

    /** Inserts a newly finalized order and its line items. */
    public void save(Order order) {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO orders (id, type, table_no, pay_method, total, order_time, served, voided, status, amount_paid) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, order.getId());
                ps.setString(2, order.getType());
                ps.setString(3, order.getTable());
                ps.setString(4, order.getPayMethod());
                ps.setInt(5, order.getTotal());
                ps.setString(6, order.getTime());
                ps.setBoolean(7, order.isServed());
                ps.setBoolean(8, order.isVoided());
                ps.setString(9, order.getStatus().name());
                ps.setInt(10, order.getAmountPaid());
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
                     "UPDATE orders SET served = ?, voided = ?, status = ?, amount_paid = ? WHERE id = ?")) {
            ps.setBoolean(1, order.isServed());
            ps.setBoolean(2, order.isVoided());
            ps.setString(3, order.getStatus().name());
            ps.setInt(4, order.getAmountPaid());
            ps.setInt(5, order.getId());
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
                     "SELECT id, type, table_no, pay_method, total, order_time, served, voided, status, amount_paid "
                             + "FROM orders ORDER BY id DESC")) {
            while (rs.next()) {
                int id = rs.getInt("id");
                Order order = new Order(id, rs.getString("type"), rs.getString("table_no"),
                        rs.getString("pay_method"), itemsByOrder.getOrDefault(id, List.of()),
                        rs.getInt("total"), rs.getString("order_time"));
                OrderStatus saved = OrderStatus.fromSaved(rs.getString("status"));
                // orders from an older version have no status: they were paid on the spot
                order.setStatus(saved != null ? saved
                        : rs.getBoolean("served") ? OrderStatus.SERVED : OrderStatus.IN_KITCHEN);
                int paid = rs.getInt("amount_paid");
                order.setAmountPaid(paid == 0 && order.getStatus() != OrderStatus.PENDING_PAYMENT ? order.getTotal() : paid);
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
                     "SELECT order_id, reason, void_time FROM order_voids ORDER BY rowid DESC")) {
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

    /** The saved admin account as {username, salt, hash}, or null if none exists yet. */
    public String[] loadAdmin() {
        try (Connection c = connect();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT username, salt, password_hash FROM admin_account WHERE id = 1")) {
            return rs.next()
                    ? new String[] { rs.getString("username"), rs.getString("salt"), rs.getString("password_hash") }
                    : null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load admin account", e);
        }
    }

    /** Creates or replaces the (single) admin account. The password is already hashed. */
    public void saveAdmin(String username, String salt, String hash) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "REPLACE INTO admin_account (id, username, salt, password_hash) VALUES (1, ?, ?, ?)")) {
            ps.setString(1, username);
            ps.setString(2, salt);
            ps.setString(3, hash);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save admin account", e);
        }
    }
}
