package com.larrystapsihan.ordering.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * A touch keyboard so nothing in the app needs a physical keyboard.
 *
 * Put one next to the form, then register each text box on it:
 *
 *   OnScreenKeyboard kb = new OnScreenKeyboard();
 *   kb.attachText(nameField, true);    // letters/digits; true = auto-capitalize words
 *   kb.attachNumber(priceField);       // number pad only
 *
 * It types into whichever registered box has focus and switches layout to
 * match it (full keyboard or number pad). The keys never take focus
 * themselves, so the text box keeps its cursor while you tap. A physical
 * keyboard still works too.
 *
 * The keyboard only shows while Touchscreen Mode is on (Settings); with it
 * off the keyboard is hidden and takes no space, so a physical keyboard is
 * used as normal.
 */
public final class OnScreenKeyboard extends VBox {

    private enum Mode { TEXT, NUMBER }

    private TextInputControl target;
    private Mode mode;
    private boolean autoCapitalize;
    private boolean shift;
    private final Map<Button, String> letterKeys = new LinkedHashMap<>();
    private Button shiftKey;

    public OnScreenKeyboard() {
        getStyleClass().add("keyboard");
        setSpacing(6);
        setAlignment(Pos.CENTER);
        setFocusTraversable(false);
        setMaxHeight(USE_PREF_SIZE);          // hug the keys instead of filling the page
        setMinHeight(USE_PREF_SIZE);
        visibleProperty().bind(TouchMode.enabledProperty());
        managedProperty().bind(visibleProperty());
    }

    /** Full keyboard for this box. autoCapitalize: capital letter at the start and after a space or hyphen. */
    public void attachText(TextInputControl field, boolean autoCapitalize) {
        attach(field, Mode.TEXT, autoCapitalize);
    }

    /** Number pad for this box (prices etc.). */
    public void attachNumber(TextInputControl field) {
        attach(field, Mode.NUMBER, false);
    }

    private void attach(TextInputControl field, Mode m, boolean auto) {
        field.focusedProperty().addListener((obs, was, is) -> { if (is) use(field, m, auto); });
        // a tap on the box also switches the keyboard to it (e.g. when the window itself is not focused yet)
        field.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> use(field, m, auto));
        if (target == null) use(field, m, auto);
    }

    private void use(TextInputControl field, Mode m, boolean auto) {
        target = field;
        autoCapitalize = auto;
        if (mode != m) { mode = m; build(); }
        updateShift();
    }

    // ------------------------------------------------------------ layouts

    private void build() {
        getChildren().clear();
        letterKeys.clear();
        shiftKey = null;
        if (mode == Mode.NUMBER) {
            addRow("1", "2", "3");
            addRow("4", "5", "6");
            addRow("7", "8", "9");
            HBox last = row(actionKey("Clear", 78, this::clearAll), charKey("0"), actionKey("⌫", 78, this::backspace));
            getChildren().add(last);
            return;
        }
        HBox r1 = row();
        for (String d : "1234567890".split("")) r1.getChildren().add(charKey(d));
        r1.getChildren().add(actionKey("⌫", 64, this::backspace));
        getChildren().add(r1);

        getChildren().add(letterRow("qwertyuiop"));
        getChildren().add(letterRow("asdfghjkl"));

        HBox r4 = row();
        shiftKey = actionKey("⇧", 64, () -> { shift = !shift; refreshLetters(); });
        r4.getChildren().add(shiftKey);
        for (String l : "zxcvbnm".split("")) r4.getChildren().add(letterKey(l));
        r4.getChildren().add(charKey("-"));
        getChildren().add(r4);

        HBox r5 = row(charKey("_"), charKey("."), charKey("@"),
                charKey(" ", "Space", 220), actionKey("Clear", 78, this::clearAll));
        getChildren().add(r5);
    }

    private HBox letterRow(String letters) {
        HBox r = row();
        for (String l : letters.split("")) r.getChildren().add(letterKey(l));
        return r;
    }

    private void addRow(String... chars) {
        HBox r = row();
        for (String c : chars) r.getChildren().add(charKey(c));
        getChildren().add(r);
    }

    private HBox row(Button... keys) {
        HBox r = new HBox(6, keys);
        r.setAlignment(Pos.CENTER);
        return r;
    }

    // ------------------------------------------------------------ keys

    private Button baseKey(String label, double width) {
        Button b = new Button(label);
        b.getStyleClass().add("key-btn");
        b.setFocusTraversable(false);          // keep the cursor in the text box
        b.setMinWidth(width);
        b.setPrefWidth(width);
        b.setMinHeight(46);
        HBox.setHgrow(b, Priority.NEVER);
        return b;
    }

    private Button charKey(String c) { return charKey(c, c, 46); }

    private Button charKey(String c, String label, double width) {
        Button b = baseKey(label, width);
        b.setOnAction(e -> type(c));
        return b;
    }

    private Button letterKey(String lower) {
        Button b = baseKey(lower, 46);
        letterKeys.put(b, lower);
        b.setOnAction(e -> type(shift ? lower.toUpperCase() : lower));
        return b;
    }

    private Button actionKey(String label, double width, Runnable action) {
        Button b = baseKey(label, width);
        b.getStyleClass().add("key-action");
        b.setOnAction(e -> action.run());
        return b;
    }

    // ------------------------------------------------------------ typing

    private void type(String s) {
        if (target == null) return;
        target.replaceSelection(s);
        updateShift();
    }

    private void backspace() {
        if (target == null) return;
        if (target.getSelection().getLength() > 0) target.replaceSelection("");
        else target.deletePreviousChar();
        updateShift();
    }

    private void clearAll() {
        if (target == null) return;
        target.clear();
        updateShift();
    }

    /** Capital for the next letter only at the start of a word (when autoCapitalize), otherwise lower case. */
    private void updateShift() {
        String t = target == null ? "" : target.getText();
        shift = autoCapitalize && (t.isEmpty() || t.endsWith(" ") || t.endsWith("-"));
        refreshLetters();
    }

    private void refreshLetters() {
        for (Map.Entry<Button, String> e : letterKeys.entrySet())
            e.getKey().setText(shift ? e.getValue().toUpperCase() : e.getValue());
        if (shiftKey != null) {
            shiftKey.getStyleClass().remove("key-on");
            if (shift) shiftKey.getStyleClass().add("key-on");
        }
    }
}
