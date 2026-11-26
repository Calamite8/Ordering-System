package com.larrystapsihan.ordering.service;

import com.larrystapsihan.ordering.data.OrderRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * The admin account: create it, log in, lock. Cashiers never log in - the
 * app opens straight to ordering - and admin-only actions (add menu items,
 * clear queue/receipts, void orders, Manage Menu) appear only while
 * loggedInProperty() is true.
 *
 * The password is never stored as text - only a salted PBKDF2 hash. It is
 * saved on this device in the local database (admin_account table) and, if
 * that can't be opened, in ~/LarrysTapsihan/admin.properties.
 *
 * No JavaFX nodes in here, only the observable loggedIn flag the UI binds to.
 */
public class AdminService {

    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int ITERATIONS = 120_000;
    private static final Path FILE =
            Path.of(System.getProperty("user.home"), "LarrysTapsihan", "admin.properties");

    private final OrderRepository repository;   // nullable - no database is a valid mode
    private final ReadOnlyBooleanWrapper loggedIn = new ReadOnlyBooleanWrapper(false);

    private String username;   // null until an admin account exists
    private String salt;       // Base64
    private String hash;       // Base64

    public AdminService(OrderRepository repository) {
        this.repository = repository;
        load();
    }

    public boolean hasAdmin() { return username != null; }
    public String getUsername() { return username; }
    public boolean isLoggedIn() { return loggedIn.get(); }
    public ReadOnlyBooleanProperty loggedInProperty() { return loggedIn.getReadOnlyProperty(); }

    /**
     * Creates the admin account (or, while logged in, replaces its credentials)
     * and logs in. Returns an error message, or null on success.
     */
    public String saveCredentials(String newUsername, String password, String confirm) {
        if (hasAdmin() && !isLoggedIn()) return "Log in as admin first.";
        String name = newUsername == null ? "" : newUsername.trim();
        if (name.isEmpty()) return "Enter a username.";
        if (password == null || password.length() < MIN_PASSWORD_LENGTH)
            return "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.";
        if (!password.equals(confirm)) return "Passwords do not match.";

        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        this.username = name;
        this.salt = Base64.getEncoder().encodeToString(saltBytes);
        this.hash = hash(password, saltBytes);
        save();
        loggedIn.set(true);
        return null;
    }

    /** Returns true (and unlocks the admin tools) if the credentials match. */
    public boolean login(String user, String password) {
        if (!hasAdmin() || user == null || password == null) return false;
        byte[] expected = Base64.getDecoder().decode(hash);
        byte[] actual = Base64.getDecoder().decode(hash(password, Base64.getDecoder().decode(salt)));
        boolean ok = user.trim().equals(username) && MessageDigest.isEqual(expected, actual);
        if (ok) loggedIn.set(true);
        return ok;
    }

    /** Locks the admin tools again. */
    public void logout() { loggedIn.set(false); }

    // ------------------------------------------------------------ hashing

    private static String hash(String password, byte[] saltBytes) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, ITERATIONS, 256);
            byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available", e);
        }
    }

    // ------------------------------------------------------------ storage

    private void load() {
        String[] row = null;
        if (repository != null) {
            try { row = repository.loadAdmin(); } catch (RuntimeException e) { row = null; }
        }
        if (row == null && Files.exists(FILE)) {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(FILE)) {
                p.load(in);
                if (p.getProperty("hash") != null)
                    row = new String[] { p.getProperty("username"), p.getProperty("salt"), p.getProperty("hash") };
            } catch (IOException ignored) { }
        }
        if (row != null) { username = row[0]; salt = row[1]; hash = row[2]; }
    }

    private void save() {
        if (repository != null) {
            try { repository.saveAdmin(username, salt, hash); return; } catch (RuntimeException ignored) { }
        }
        Properties p = new Properties();
        p.setProperty("username", username);
        p.setProperty("salt", salt);
        p.setProperty("hash", hash);
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Larry's Tapsihan admin account (password is hashed)");
            }
        } catch (IOException e) {
            System.err.println("Could not save the admin account: " + e.getMessage());
        }
    }
}
