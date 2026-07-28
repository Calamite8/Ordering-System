# Larry's Tapsihan — Inventory System (UI phase, JavaFX)

This is the **UI-only** build for the first roadmap item: the Inventory UI
(stock board, sale-ticket deduction, stock-in, reports), ported from
`inventory_prototype.html` to a JavaFX desktop app. Data is in-memory and
resets on restart — the Database task wires this to MySQL (+ fallback)
later.

## Project layout

```
InventorySystem/
├── pom.xml                      Maven project (NetBeans opens this natively)
├── src/main/java/com/larrystapsihan/inventory/
│   ├── Main.java                JavaFX Application entry point
│   ├── MainView.java            Header + TabPane wiring the 4 views together
│   ├── StockBoardView.java      Stock Board tab
│   ├── SaleView.java            Record a Sale tab
│   ├── StockInView.java         Stock In tab
│   ├── ReportsView.java         Reports tab
│   ├── InventoryStore.java      In-memory data + business logic (no UI/DB code)
│   ├── Ingredient.java, Dish.java, LogRecords.java   Plain model classes
└── src/main/resources/com/larrystapsihan/inventory/styles.css
```

`InventoryStore`, `Ingredient`, `Dish`, and the log records have **no
JavaFX imports** on purpose — when the Database task lands, only
`InventoryStore` needs to change (swap the in-memory `List`s for a
repository backed by MySQL / the fallback store); the views stay the same.

## Opening in NetBeans

1. NetBeans 12+ has built-in Maven support: **File → Open Project** →
   select the `InventorySystem` folder (the one with `pom.xml`).
2. NetBeans will resolve the JavaFX dependency from Maven Central on
   first open (needs internet once, then it's cached in `~/.m2`).
3. Right-click the project → **Run** (or `mvn javafx:run` in a terminal).

`nbactions.xml` remaps NetBeans' Run/Debug buttons to `javafx:run` for
this project. Without it, NetBeans' generic Maven "Run" fallback calls
`exec-maven-plugin:exec`, which needs its own `exec.mainClass`/
`exec.executable` setup and isn't JavaFX-aware — that's what the
`Failed to execute goal ... exec-maven-plugin:exec` error is. If you
ever see that error again, it means NetBeans picked its own default
run action instead of `nbactions.xml` (some NetBeans versions cache
this per-project under **Project Properties → Actions** — check that
"Run Project" is set to goal `javafx:run` there, or just run
`mvn javafx:run` from a terminal in the project folder).

## Editing the UI visually with Scene Builder

Scene Builder edits **FXML** files, not hand-written Java. Right now only
one tab — **Stock In** — has been converted to FXML, as a working
example; Stock Board, Record a Sale, and Reports are still plain Java
(`StockBoardView.java`, `SaleView.java`, `ReportsView.java`) and won't
open in Scene Builder until they're converted the same way.

**Setup (one-time):**

1. Download Scene Builder from
   [https://gluonhq.com/products/scene-builder/](https://gluonhq.com/products/scene-builder/)
   and install it.
2. In NetBeans: **Tools → Options → Java → JavaFX** tab → set "Scene
   Builder Home" to wherever you installed it.

**Editing workflow:**

1. In the Projects panel, find
   `src/main/resources/com/larrystapsihan/inventory/StockInView.fxml`.
2. Right-click it → **Open** (or **Edit** to see the raw XML). NetBeans
   launches Scene Builder with that file loaded.
3. Drag controls around, change spacing/alignment in the Layout
   inspector, add new fields, etc. Save in Scene Builder.
4. Back in NetBeans, re-run the project — the layout changes take
   effect immediately, no Java changes needed for layout-only edits.

**How FXML connects to code** (the pattern to repeat for the other
tabs):

- `StockInView.fxml` declares `fx:controller="...StockInController"`
  and gives controls `fx:id`s (`ingredientBox`, `qtyField`, `toast`,
  `logRows`) and an `onAction="#handleAddStock"` on the button.
- `StockInController.java` has `@FXML`-annotated fields with those
  exact names — FXMLLoader injects the matching control into each one
  — plus an `@FXML handleAddStock()` method the button calls.
- `MainView.java` loads the FXML with `FXMLLoader`, grabs the
  controller via `loader.getController()`, and calls
  `controller.setStore(store)` once to hand it the shared data (FXML
  controllers have a no-arg constructor, so there's no other way to
  pass constructor arguments in).
- Style classes referenced in the FXML (`stockin-form`, `field-label`,
  `add-btn`, etc.) must already exist in `styles.css` — Scene Builder
  will show a control as unstyled if you type a class name that isn't
  in the stylesheet.

If you'd like the remaining three tabs converted to FXML the same way,
just ask — it's the same three steps each time (FXML file, controller
class, one-line swap in `MainView`).

## Command line: run without packaging

```bash
mvn javafx:run
```

## Building a runnable jar

```bash
mvn clean package
```

This produces `target/InventorySystem.jar` plus `target/libs/*.jar`
(JavaFX's own jars, copied there by `maven-dependency-plugin`). The jar's
manifest points its classpath at `libs/`, so keep those two together.

### About the JavaFX platform classifier

Maven Central publishes JavaFX as OS-specific artifacts
(`javafx-controls-<version>-win.jar`, `-linux.jar`, `-mac.jar`). `mvn
javafx:run` resolves this automatically. For a **plain `mvn package`**
destined for `jpackage`, add the classifier matching the machine you're
building on to the `javafx-controls` dependency in `pom.xml`, e.g.:

```xml
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
    <version>${javafx.version}</version>
    <classifier>win</classifier> <!-- or linux / mac / mac-aarch64 -->
</dependency>
```

(Build separately per target OS — this is a normal JavaFX limitation,
not specific to this project.)

## Packaging with jpackage

Because this app references JavaFX from the classpath (no
`module-info.java`), `jpackage` does **not** need the separate JavaFX
jmods SDK — the JavaFX jars just ride along as ordinary classpath jars
inside the app image.

```bash
mvn clean package
jpackage ^
  --type app-image ^
  --name LarrysTapsihanInventory ^
  --input target ^
  --main-jar InventorySystem.jar ^
  --main-class com.larrystapsihan.inventory.Main ^
  --dest target/jpackage ^
  --app-version 1.0.0 ^
  --vendor "Larry's Tapsihan"
```

(Use `\` line continuations instead of `^` on macOS/Linux.) Swap
`--type app-image` for `exe`/`msi` (Windows), `dmg`/`pkg` (macOS), or
`deb`/`rpm` (Linux) once you're ready to produce an installer instead of
a raw app folder — those extra formats need platform-specific tools
(e.g. WiX on Windows) installed alongside the JDK's `jpackage`.

## What's intentionally NOT in this phase

- No database — see `InventoryStore`'s class-level note.
- No cashier order entry / kitchen tickets / receipts — that's the
  separate Order System task, meant to connect in later via the
  context-level DFD so a punched order triggers these same recipe-based
  deductions.
- No usability-testing instrumentation — that belongs to the UX task.
