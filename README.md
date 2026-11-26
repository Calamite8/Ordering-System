# Larry's Tapsihan - Order System

JavaFX + Maven. One project, one `pom.xml`. The app opens straight onto the
Order System (New Order, Kitchen Queue, Receipts, Settings). There is no
startup login: staff just take orders, and the owner unlocks the admin tools
from **Settings > Admin Login**.

## Run

    mvn clean package        # builds target/OrderSystem.jar (fat jar)
    mvn javafx:run           # run from Maven / the NetBeans Run button
    java -jar target/OrderSystem.jar

Admin account: the first time, open **Settings** and create the admin
username/password (the password is stored hashed, never as text). After that, Settings shows
**Admin Login**. Admin mode locks again with **Lock Admin**, or after 5
minutes without activity.

| | Staff (default) | Admin (after login) |
|---|---|---|
| New Order, Kitchen Queue (Mark Served), Receipts (view) | yes | yes |
| Print Receipt (after an order, and on every receipt card) | yes | yes |
| Manage Menu tab: add / edit / remove categories, menu items, prices, photos | - | yes |
| + Add New Item (on the New Order page) | - | yes |
| Void Order (on a receipt), Clear Queue, Clear Receipts | - | yes |

There are no report screens (no shift summary or sales reports).

## Printable receipts

After **Finalize Order** a **Print Receipt #101** button appears under the
cart; every card on the **Receipts** page also has a **Print** button. Both
open the normal print dialog. Pick a receipt printer or a normal printer; on
Windows choose **Microsoft Print to PDF** to save the receipt as a PDF. The
receipt shows the shop name, order number, date and time, dine-in/take-out,
table, items, total, "Cash" and a thank-you line. To change the shop name or
the thank-you line, edit the two constants at the top of `ui/ReceiptPrinter.java`.

## Managing the menu (admin)

Settings > Admin Login, then open the **Manage Menu** tab:

- **Food Categories**: + Add Category, Rename, Delete (deleting a category keeps its items; they just have no category).
- **Menu Items**: + Add Item, Edit (name, price, photo, category), Remove (old receipts are not affected).

On **New Order**, category buttons (All, each category, Other) filter the menu.
They only appear once at least one category exists.

## Your data (all stored on this computer)

Nothing needs a server or an internet connection. Everything is saved under
`C:\Users\<you>\LarrysTapsihan\`:

| What | Where |
|---|---|
| Menu, orders, voids, admin account | `ordering.db` (SQLite - open it with "DB Browser for SQLite" if you want to look inside) |
| Dish photos | `photos\` |
| Touchscreen Mode choice | `settings.properties` |

Back up or move the app's data by copying that folder. The menu starts empty -
the admin adds the categories and items. (A menu saved by an older version is
upgraded automatically.)

## Touchscreen Mode (Settings, OFF by default)

Off: normal buttons, a typed table number, and your physical keyboard.
On: bigger buttons, the on-screen keyboard and table-number buttons.

## Where is everything?

Source root: `src/main/java/com/larrystapsihan/ordering/`

    app/        STARTUP - opens the window
    model/      DATA SHAPES - plain objects, no logic
    data/       STORAGE - the menu and the local SQLite data file
    service/    LOGIC - finalize/serve/void an order, and the admin account
    ui/         WINDOWS - the main shell and the sidebar
    ui/screens/ PAGES - one file per page of the Order System

| File | What it does |
|---|---|
| **app/** | |
| `OrderMain.java` | The program's start. Opens the maximized window on the Order System and opens the local data file. **The data file location (`DB_FILE`) is at the top.** |
| `OrderLauncher.java` | The jar's entry point. Only calls `OrderMain`; exists so `java -jar` works. Don't edit. |
| **model/** | |
| `Dish.java` | A menu item: name, price, optional photo, category. |
| `Order.java` | A finalized order: items, type, table, payment, total, served/voided. |
| `OrderItem.java` | One line inside an order (dish, qty, line total). |
| `VoidRecord.java` | One entry in the void log. |
| **data/** | |
| `MenuStore.java` | The menu and its food categories: add / edit / remove items and categories. Starts empty (no sample items); every change is saved on this device and loaded again at startup. |
| `OrderRepository.java` | All saved data in one SQLite file on this device (`~/LarrysTapsihan/ordering.db`): menu items, categories, orders, voids, admin account. Creates (and upgrades) its own tables. |
| **service/** | |
| `OrderService.java` | Finalize an order, mark served, void, clear queue, clear history. |
| `AdminService.java` | The admin account: create, log in, lock (hashed password). Screens bind to its `loggedInProperty()`. |
| **ui/** | |
| `ReceiptPrinter.java` | Builds the printable receipt and sends it to the printer (print dialog). **Shop name and thank-you line are constants at the top.** |
| `OnScreenKeyboard.java` | Touch keyboard (full keyboard or number pad) used beside the item/category forms and the Settings admin form, so no physical keyboard is needed. Register a text box with `attachText(...)` / `attachNumber(...)`. |
| `TouchMode.java` | The Touchscreen Mode setting (Settings page): adds the `touch-mode` style class to the window and dialogs, and remembers the choice in `~/LarrysTapsihan/settings.properties`. |
| `OrderController.java` | The Order System window: puts the sidebar on the left and the chosen page in the center; hides the Manage Menu tab unless admin; the 5-minute admin idle lock. **Add a new page here.** |
| `NavSidebar.java` | The sidebar (icon + name, Settings pinned at the bottom). |
| **ui/screens/** | |
| `Screen.java` | The contract every page follows (`getView()` + `refresh()`). |
| `ScreenLayout.java` | Shared layout helpers (page title/header, transparent scroll, spacer). |
| `NewOrderScreen.java` | New Order page (table picker `TABLE_COUNT`, cash only): category buttons, menu grid, cart, Finalize, Print Receipt, **+ Add New Item** (admin only). |
| `ManageMenuScreen.java` | Manage Menu page (admin only): categories and menu items, add / edit / remove. |
| `MenuDialogs.java` | The Add/Edit Item dialog (photo, name, price, category), the category-name prompt and the confirm box. |
| `Photos.java` | Dish photo box ("No photo" placeholder) and copying chosen photos into `~/LarrysTapsihan/photos`. |
| `KitchenScreen.java` | Kitchen Queue page: open tickets, Mark Served, Clear Queue (admin). |
| `ReceiptsScreen.java` | Receipts page: one card per order with Print; Void Order and Clear Receipts (admin). |
| `SettingsScreen.java` | Settings page: create admin account / Admin Login / Lock Admin / change credentials. |

Resources: `src/main/resources/com/larrystapsihan/ordering/ui/`

| File | What it does |
|---|---|
| `styles.css` | All colors and styling. Has a contents list at the top (buttons, fields, cards, admin card, sidebar, touchscreen mode). |
| `OrderView.fxml` | Empty shell for the Order System window (built in `OrderController`). |

Project root: `pom.xml` (dependencies + build), `nbactions.xml` (makes NetBeans' Run button use `javafx:run`).

## "I want to change..."

| Change | Edit |
|---|---|
| Menu items, prices, categories | In the app: admin > Manage Menu. Stored in the `menu_items` and `categories` tables of the data file. |
| Receipt layout / shop name | `ui/ReceiptPrinter.java` |
| Void reasons | `ui/screens/ReceiptsScreen.java` (`VOID_REASONS`) |
| Admin username / password | Settings > Admin Login (or delete `~/LarrysTapsihan/admin.properties` / the `admin_account` row to start over) |
| Which actions are admin-only | the screen file, via `ScreenLayout.adminOnly(node, admin)` |
| Touchscreen button sizes | `styles.css` section 8 (`.touch-mode ...`) |
| Admin idle-lock time | `ui/OrderController.java` (`ADMIN_IDLE_LIMIT`) |
| Where data is saved | `app/OrderMain.java` (`DB_FILE` at the top) |
| Colors or button looks | `styles.css` |
| What the New Order page shows | `ui/screens/NewOrderScreen.java` |
| What happens when an order is finalized/voided | `service/OrderService.java` |
| Sidebar icons or order | `ui/OrderController.java` (`setStore`) |
| Add a whole new page | new class in `ui/screens/` implementing `Screen`, then register it in `OrderController.setStore` |

## How the pieces connect

    OrderMain -> OrderController
    OrderController -> NavSidebar + the Screens (+ AdminService: Settings logs in, the other screens show/hide admin tools)
    Screens -> OrderService -> MenuStore (menu)
                            -> OrderRepository (local SQLite file)

When the menu or orders change, `MenuStore` tells `OrderController`, which
calls `refresh()` on every screen.
