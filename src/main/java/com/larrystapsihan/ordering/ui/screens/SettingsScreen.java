package com.larrystapsihan.ordering.ui.screens;

import com.larrystapsihan.ordering.service.AdminService;
import com.larrystapsihan.ordering.ui.OnScreenKeyboard;
import com.larrystapsihan.ordering.ui.TouchMode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Settings page (the gear icon at the bottom of the sidebar). Its one job is
 * the admin lock, and the card changes with the state:
 *
 *   no admin account yet -> "Create Admin Account" (username + password)
 *   admin exists, locked -> "Admin Login"
 *   admin logged in      -> "Admin Mode" (Lock Admin, change credentials)
 *
 * Logging in unlocks the admin-only actions elsewhere (see AdminService).
 */
public class SettingsScreen implements Screen {

    private final AdminService admin;
    private final BorderPane view;
    private OnScreenKeyboard keyboard;   // rebuilt with each card; every field registers on it

    public SettingsScreen(AdminService admin) {
        this.admin = admin;

        view = new BorderPane();
        view.getStyleClass().add("root-bg");
        view.setPadding(new Insets(18));

        Label title = new Label("Settings");
        title.getStyleClass().add("section-title");
        Label note = new Label("Admin login unlocks menu editing, Clear Queue, Clear Receipts, "
                + "voiding orders and the Shift Summary.");
        note.getStyleClass().add("section-note");
        note.setWrapText(true);
        VBox header = new VBox(4, title, note);
        header.setPadding(new Insets(0, 0, 14, 0));
        view.setTop(header);

        admin.loggedInProperty().addListener((obs, was, isNow) -> refresh());
        refresh();
    }

    @Override
    public Node getView() { return view; }

    /** Rebuilds the card for the current admin state. */
    @Override
    public void refresh() {
        keyboard = new OnScreenKeyboard();
        VBox card = !admin.hasAdmin() ? buildCreateCard()
                : admin.isLoggedIn() ? buildAdminModeCard()
                : buildLoginCard();
        VBox left = new VBox(18, card, buildTouchModeCard());
        left.setMaxHeight(Region.USE_PREF_SIZE);
        HBox content = new HBox(24, left, keyboard);   // touch keyboard beside the forms
        content.setAlignment(Pos.TOP_LEFT);
        BorderPane.setAlignment(content, Pos.TOP_LEFT);
        view.setCenter(content);
    }

    // ------------------------------------------------------------ the three cards

    private VBox buildLoginCard() {
        TextField user = field("e.g. owner");
        PasswordField pass = passwordField();
        Label message = new Label();
        message.setWrapText(true);

        Runnable submit = () -> {
            if (admin.login(user.getText(), pass.getText())) return;   // card rebuilds itself
            pass.clear();
            error(message, "Invalid username or password.");
        };
        Button login = new Button("Admin Login");
        login.getStyleClass().add("punch-btn");
        login.setMaxWidth(Double.MAX_VALUE);
        login.setDefaultButton(true);
        login.setOnAction(e -> submit.run());

        return card("Admin Login", "Sign in to unlock the admin tools.",
                labeled("USERNAME", user), labeled("PASSWORD", pass), login, message);
    }

    /** First-run card: create the admin account. */
    private VBox buildCreateCard() {
        return card("Create Admin Account",
                "No admin yet. Choose a username and password for the owner.",
                credentialsForm(true));
    }

    /** The username/password/confirm fields and Save button (create on first run, change while logged in). */
    private VBox credentialsForm(boolean firstRun) {
        TextField user = field("e.g. owner");
        if (!firstRun) user.setText(admin.getUsername());
        PasswordField pass = passwordField();
        PasswordField confirm = passwordField();
        Label message = new Label();
        message.setWrapText(true);

        Button save = new Button(firstRun ? "Create Admin Account" : "Save New Credentials");
        save.getStyleClass().add("punch-btn");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(e -> {
            String err = admin.saveCredentials(user.getText(), pass.getText(), confirm.getText());
            if (err != null) { error(message, err); return; }
            pass.clear();
            confirm.clear();
            if (firstRun) return;                      // logged in -> card rebuilds itself
            message.getStyleClass().setAll("toast-ok");
            message.setText("Credentials updated.");
        });

        return new VBox(12, labeled("USERNAME", user), labeled("PASSWORD", pass),
                labeled("CONFIRM PASSWORD", confirm), save, message);
    }

    private VBox buildAdminModeCard() {
        Button lock = new Button("Lock Admin");
        lock.getStyleClass().add("void-link");
        lock.setOnAction(e -> admin.logout());

        Label unlocked = new Label("Admin tools are unlocked. They lock again after 5 minutes "
                + "without activity, or when you press Lock Admin.");
        unlocked.getStyleClass().add("admin-note");
        unlocked.setWrapText(true);

        Label changeTitle = new Label("Change Credentials");
        changeTitle.getStyleClass().add("admin-title");
        changeTitle.setPadding(new Insets(10, 0, 0, 0));

        return card("Admin Mode", "Signed in as " + admin.getUsername() + ".",
                unlocked, lock, changeTitle, credentialsForm(false));
    }

    /** The Touchscreen Mode switch (always available - staff use it too). */
    private VBox buildTouchModeCard() {
        ToggleButton toggle = new ToggleButton();
        toggle.getStyleClass().add("toggle-btn");
        toggle.setMaxWidth(Double.MAX_VALUE);
        toggle.setSelected(TouchMode.isEnabled());
        Runnable label = () -> toggle.setText(toggle.isSelected()
                ? "ON  -  tap to turn off"
                : "OFF  -  tap to turn on");
        label.run();
        toggle.setOnAction(e -> { TouchMode.setEnabled(toggle.isSelected()); label.run(); });
        return card("Touchscreen Mode",
                "For a touchscreen: bigger buttons, always-visible tab names, the on-screen keyboard and table-number buttons. Turn it off to use a physical keyboard.", toggle);
    }

    // ------------------------------------------------------------ small helpers

    private VBox card(String title, String note, Node... rows) {
        Label t = new Label(title);
        t.getStyleClass().add("admin-title");
        Label n = new Label(note);
        n.getStyleClass().add("admin-note");
        n.setWrapText(true);
        VBox card = new VBox(12, t, n);
        card.getChildren().addAll(rows);
        card.getStyleClass().add("admin-card");
        card.setMaxWidth(360);
        card.setPrefWidth(360);
        card.setMaxHeight(Region.USE_PREF_SIZE);   // hug the content instead of filling the page
        return card;
    }

    private VBox labeled(String label, Node input) {
        Label l = new Label(label);
        l.getStyleClass().add("admin-field-label");
        return new VBox(5, l, input);
    }

    private TextField field(String prompt) {
        TextField f = new TextField();
        f.setPromptText(prompt);
        f.getStyleClass().add("admin-field-input");
        keyboard.attachText(f, false);
        return f;
    }

    private PasswordField passwordField() {
        PasswordField f = new PasswordField();
        f.setPromptText("••••••");
        f.getStyleClass().add("admin-field-input");
        keyboard.attachText(f, false);
        return f;
    }

    private void error(Label message, String text) {
        message.getStyleClass().setAll("toast-error");
        message.setText(text);
    }
}
