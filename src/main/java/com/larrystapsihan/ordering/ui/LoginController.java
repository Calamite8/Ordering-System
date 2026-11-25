package com.larrystapsihan.ordering.ui;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;

/**
 * Login screen (LoginView.fxml). Builds the kraft-paper sign-in card in
 * code and checks the hardcoded placeholder credentials. On success it calls
 * the callback OrderMain registered with setOnLoginSuccess().
 */
public class LoginController {

    @FXML private BorderPane root;

    private TextField usernameField;
    private PasswordField passwordField;
    private TextField passwordVisibleField;
    private Label toast;
    private String role = "Cashier";

    private Runnable onLoginSuccess;

    /** Called after a successful (prototype-level) sign-in. */
    public void setOnLoginSuccess(Runnable callback) {
        this.onLoginSuccess = callback;
    }

    @FXML
    private void initialize() {
        VBox shell = new VBox(26, buildBrand(), buildCard(), buildFootnote());
        shell.setAlignment(Pos.CENTER);
        shell.setMaxWidth(380);
        shell.setPadding(new Insets(20));

        StackPane center = new StackPane(shell);
        center.getStyleClass().add("root-bg");
        root.setCenter(center);
    }

    private VBox buildBrand() {
        Text brand = new Text("LARRY'S");
        brand.getStyleClass().add("brand-mark");
        Text accent = new Text(" TAPSIHAN");
        accent.getStyleClass().addAll("brand-mark", "brand-accent");
        TextFlow brandLine = new TextFlow(brand, accent);
        brandLine.setTextAlignment(TextAlignment.CENTER);

        Label sub = new Label("STAFF SIGN-IN");
        sub.getStyleClass().add("brand-sub");

        VBox box = new VBox(6, brandLine, sub);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private VBox buildCard() {
        Label title = new Label("Welcome back");
        title.getStyleClass().add("login-title");
        Label note = new Label("Sign in to continue to the Order System.");
        note.getStyleClass().add("login-note");
        note.setWrapText(true);

        ToggleGroup roleGroup = new ToggleGroup();
        ToggleButton cashier = new ToggleButton("Cashier");
        ToggleButton admin = new ToggleButton("Admin");
        cashier.getStyleClass().add("login-role-btn");
        admin.getStyleClass().add("login-role-btn");
        cashier.setToggleGroup(roleGroup);
        admin.setToggleGroup(roleGroup);
        cashier.setSelected(true);
        cashier.setMaxWidth(Double.MAX_VALUE);
        admin.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(cashier, Priority.ALWAYS);
        HBox.setHgrow(admin, Priority.ALWAYS);
        cashier.setOnAction(e -> role = "Cashier");
        admin.setOnAction(e -> role = "Admin");
        HBox roleToggle = new HBox(8, cashier, admin);

        Label userLabel = new Label("USERNAME");
        userLabel.getStyleClass().add("login-field-label");
        usernameField = new TextField();
        usernameField.setPromptText("e.g. juan.delacruz");
        usernameField.getStyleClass().add("login-field-input");

        Label passLabel = new Label("PASSWORD");
        passLabel.getStyleClass().add("login-field-label");

        passwordField = new PasswordField();
        passwordField.setPromptText("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022");
        passwordField.getStyleClass().add("login-field-input");

        passwordVisibleField = new TextField();
        passwordVisibleField.setPromptText("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022");
        passwordVisibleField.getStyleClass().add("login-field-input");
        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());

        passwordField.managedProperty().bind(passwordField.visibleProperty());
        passwordVisibleField.managedProperty().bind(passwordVisibleField.visibleProperty());
        passwordVisibleField.setVisible(false);

        Button togglePass = new Button("SHOW");
        togglePass.getStyleClass().add("login-toggle-pass");
        togglePass.setOnAction(e -> {
            boolean showingPlain = passwordVisibleField.isVisible();
            passwordVisibleField.setVisible(!showingPlain);
            passwordField.setVisible(showingPlain);
            togglePass.setText(showingPlain ? "SHOW" : "HIDE");
        });

        StackPane passStack = new StackPane(passwordField, passwordVisibleField);
        HBox.setHgrow(passStack, Priority.ALWAYS);
        HBox passRow = new HBox(6, passStack, togglePass);
        passRow.setAlignment(Pos.CENTER_LEFT);

        CheckBox remember = new CheckBox("Remember me");
        remember.getStyleClass().add("login-remember");
        Hyperlink forgot = new Hyperlink("Forgot password?");
        forgot.getStyleClass().add("login-forgot-link");
        forgot.setOnAction(e -> {
            toast.getStyleClass().setAll("toast-error");
            toast.setText("Ask an admin to reset your password.");
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox rememberRow = new HBox(remember, spacer, forgot);
        rememberRow.setAlignment(Pos.CENTER_LEFT);

        Button signIn = new Button("Sign In");
        signIn.getStyleClass().add("punch-btn");
        signIn.setMaxWidth(Double.MAX_VALUE);
        signIn.setDefaultButton(true);
        signIn.setOnAction(e -> handleSignIn());

        toast = new Label();
        toast.setWrapText(true);

        VBox fields = new VBox(14,
                title, note, roleToggle,
                new VBox(5, userLabel, usernameField),
                new VBox(5, passLabel, passRow),
                rememberRow, signIn, toast);
        fields.getStyleClass().add("login-card");
        fields.setPrefWidth(340);
        return fields;
    }

    private Label buildFootnote() {
        Label footnote = new Label("Larry's Tapsihan Order System \u00B7 v1.0");
        footnote.getStyleClass().add("login-footnote");
        return footnote;
    }

    // Hardcoded placeholder credentials - there's no user table/real auth yet.
    // Swap this for a real credential check once accounts exist.
    private static final String PLACEHOLDER_USER = "Ordealingsin";
    private static final String PLACEHOLDER_PASS = "Staffsyst";

    private void handleSignIn() {
        String u = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String p = passwordField.getText() == null ? "" : passwordField.getText();
        if (u.isEmpty() || p.isEmpty()) {
            toast.getStyleClass().setAll("toast-error");
            toast.setText("Enter both username and password.");
            return;
        }
        if (!u.equals(PLACEHOLDER_USER) || !p.equals(PLACEHOLDER_PASS)) {
            toast.getStyleClass().setAll("toast-error");
            toast.setText("Invalid username or password.");
            return;
        }
        toast.getStyleClass().setAll("toast-ok");
        toast.setText("Signed in as " + role + " \u2014 loading system\u2026");
        if (onLoginSuccess != null) onLoginSuccess.run();
    }
}
