package com.larrystapsihan.ordering.ui;

import com.larrystapsihan.ordering.model.Order;
import com.larrystapsihan.ordering.model.OrderItem;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Scale;
import javafx.stage.Window;

/**
 * Printable receipts. buildReceipt() makes a plain black-on-white, receipt-
 * shaped node (about 80 mm wide); print() sends it to a printer chosen in the
 * normal print dialog. On Windows, "Microsoft Print to PDF" in that dialog
 * saves the receipt as a PDF instead.
 *
 * To change the shop name or the thank-you line, edit the two constants.
 */
public final class ReceiptPrinter {

    private ReceiptPrinter() { }

    public static final String SHOP_NAME = "Larry's Tapsihan";
    public static final String FOOTER = "Thank you! Please come again.";

    private static final double WIDTH = 226;   // points: roughly an 80 mm receipt roll

    /** The receipt as a node (also used for the print-preview-sized card). */
    public static VBox buildReceipt(Order o) {
        VBox box = new VBox(3);
        box.setPadding(new Insets(14, 12, 14, 12));
        box.setPrefWidth(WIDTH);
        box.setMinWidth(WIDTH);
        box.setMaxWidth(WIDTH);
        box.setStyle("-fx-background-color: white;");

        box.getChildren().add(centered(SHOP_NAME, 15, true));
        box.getChildren().add(centered("OFFICIAL RECEIPT", 10, false));
        box.getChildren().add(dashes());

        box.getChildren().add(pair("Order No.", "#" + o.getId(), false));
        box.getChildren().add(pair("Date", o.getTime(), false));
        box.getChildren().add(pair("Type", o.getType(), false));
        if (o.getTable() != null && !o.getTable().isBlank()) {
            box.getChildren().add(pair("Table", o.getTable(), false));
        }
        box.getChildren().add(dashes());

        for (OrderItem it : o.getItems()) {
            box.getChildren().add(pair(it.qty() + " x " + it.name(), "₱" + it.lineTotal(), false));
        }
        box.getChildren().add(dashes());

        box.getChildren().add(pair("TOTAL", "₱" + o.getTotal(), true));
        box.getChildren().add(pair("Payment", o.getPayMethod(), false));
        if (o.isVoided()) {
            box.getChildren().add(dashes());
            box.getChildren().add(centered("*** VOIDED ORDER ***", 12, true));
        }
        box.getChildren().add(dashes());
        box.getChildren().add(centered(FOOTER, 10, false));
        return box;
    }

    /**
     * Opens the print dialog and prints the receipt. Shows a short message
     * instead if this computer has no printer.
     */
    public static void print(Order o, Window owner) {
        PrinterJob job;
        try {
            job = PrinterJob.createPrinterJob();
        } catch (RuntimeException ex) {
            job = null;
        }
        if (job == null) {
            message(owner, "No printer found",
                    "This computer has no printer set up. Install or connect a printer "
                            + "(or use \"Microsoft Print to PDF\") and try again.");
            return;
        }
        if (!job.showPrintDialog(owner)) {
            job.cancelJob();
            return;
        }
        VBox receipt = buildReceipt(o);
        PageLayout layout = job.getJobSettings().getPageLayout();
        // A4/Letter pages get a bigger receipt so it stays readable; roll paper prints at 1:1.
        double scale = layout.getPrintableWidth() > 400 ? 1.6 : Math.min(1, layout.getPrintableWidth() / WIDTH);
        receipt.getTransforms().add(new Scale(scale, scale));
        boolean ok = job.printPage(layout, receipt);
        if (ok) job.endJob();
        else {
            job.cancelJob();
            message(owner, "Print failed", "The receipt could not be sent to the printer.");
        }
    }

    // ---- building blocks ----

    private static void message(Window owner, String title, String text) {
        Alert a = new Alert(AlertType.INFORMATION);
        a.initOwner(owner);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(text);
        if (owner != null && owner.getScene() != null) {
            a.getDialogPane().getStylesheets().setAll(owner.getScene().getStylesheets());
        }
        TouchMode.bind(a.getDialogPane());
        a.showAndWait();
    }

    private static Label text(String s, double size, boolean bold) {
        Label l = new Label(s);
        l.setFont(Font.font("Monospaced", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        l.setTextFill(Color.BLACK);
        l.setWrapText(true);
        return l;
    }

    private static Label centered(String s, double size, boolean bold) {
        Label l = text(s, size, bold);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setAlignment(Pos.CENTER);
        l.setTextAlignment(TextAlignment.CENTER);
        return l;
    }

    private static Label dashes() {
        Label l = text("- - - - - - - - - - - - - - - - - -", 9, false);
        l.setWrapText(false);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setAlignment(Pos.CENTER);
        return l;
    }

    private static HBox pair(String left, String right, boolean bold) {
        Label l = text(left, bold ? 13 : 11, bold);
        Label r = text(right, bold ? 13 : 11, bold);
        r.setWrapText(false);
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox.setHgrow(l, Priority.SOMETIMES);
        HBox row = new HBox(6, l, gap, r);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }
}
