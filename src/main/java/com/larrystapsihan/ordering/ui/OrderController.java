package com.larrystapsihan.ordering.ui;

import com.larrystapsihan.ordering.data.MenuStore;
import com.larrystapsihan.ordering.data.OrderRepository;
import com.larrystapsihan.ordering.service.AdminService;
import com.larrystapsihan.ordering.service.OrderService;
import com.larrystapsihan.ordering.ui.screens.KitchenScreen;
import com.larrystapsihan.ordering.ui.screens.NewOrderScreen;
import com.larrystapsihan.ordering.ui.screens.ReceiptsScreen;
import com.larrystapsihan.ordering.ui.screens.Screen;
import com.larrystapsihan.ordering.ui.screens.SettingsScreen;
import com.larrystapsihan.ordering.ui.screens.ManageMenuScreen;
import java.util.List;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.util.Duration;

/**
 * The Order System window shell (OrderView.fxml): the sidebar on the left
 * and whichever screen is selected in the center. It creates the screens
 * and wires them to the sidebar - the screens themselves live in
 * ui/screens/, one file each.
 *
 * It also owns the admin lock: the Manage Menu tab only appears while an
 * admin is logged in (Settings), and admin mode locks itself after a few
 * minutes without any mouse or keyboard activity.
 *
 * Call order from OrderMain: setRepository() first (optional), then setStore().
 */
public class OrderController {

    @FXML private BorderPane root;

    private OrderRepository repository;   // optional - null means no database
    private List<Screen> screens = List.of();

    /** Optional: save order history to this database. Call before setStore(). */
    public void setRepository(OrderRepository repository) {
        this.repository = repository;
    }

    /** How long admin mode stays unlocked with no mouse/keyboard activity. */
    private static final Duration ADMIN_IDLE_LIMIT = Duration.minutes(5);

    /** Builds the screens and sidebar. Call once, after the optional setter above. */
    public void setStore(MenuStore store) {
        TouchMode.bind(root);
        OrderService orderService = new OrderService(store, repository);
        AdminService admin = new AdminService(repository);

        Screen newOrder = new NewOrderScreen(store, orderService, admin);
        Screen kitchen = new KitchenScreen(orderService, admin);
        Screen receipts = new ReceiptsScreen(orderService, admin);
        Screen manage = new ManageMenuScreen(store);
        Screen settings = new SettingsScreen(admin);
        screens = List.of(newOrder, kitchen, receipts, manage, settings);

        NavSidebar sidebar = new NavSidebar(
                List.of(
                        new NavSidebar.Item("🛒", "New Order", () -> show(newOrder)),
                        new NavSidebar.Item("🍳", "Kitchen Queue", () -> show(kitchen)),
                        new NavSidebar.Item("🧾", "Receipts", () -> show(receipts)),
                        // admin only: appears after Settings > Admin Login
                        new NavSidebar.Item("📋", "Manage Menu", () -> show(manage), admin.loggedInProperty())),
                new NavSidebar.Item("⚙", "Settings", () -> show(settings)));
        root.setLeft(sidebar);

        // Locking admin while on Manage Menu would leave a hidden page on screen.
        admin.loggedInProperty().addListener((obs, was, isNow) -> {
            if (!isNow && root.getCenter() == manage.getView()) sidebar.select("New Order");
        });
        installIdleLock(admin);

        show(newOrder);
        refresh();
    }

    /** Locks admin mode after ADMIN_IDLE_LIMIT with no mouse or keyboard activity. */
    private void installIdleLock(AdminService admin) {
        PauseTransition idle = new PauseTransition(ADMIN_IDLE_LIMIT);
        idle.setOnFinished(e -> admin.logout());
        admin.loggedInProperty().addListener((obs, was, isNow) -> {
            if (isNow) idle.playFromStart(); else idle.stop();
        });
        root.addEventFilter(MouseEvent.ANY, e -> { if (admin.isLoggedIn()) idle.playFromStart(); });
        root.addEventFilter(KeyEvent.ANY, e -> { if (admin.isLoggedIn()) idle.playFromStart(); });
    }

    /** Re-draws every screen. OrderMain registers this as the store's change listener. */
    public void refresh() {
        for (Screen s : screens) s.refresh();
    }

    private void show(Screen screen) {
        root.setCenter(screen.getView());
    }
}
