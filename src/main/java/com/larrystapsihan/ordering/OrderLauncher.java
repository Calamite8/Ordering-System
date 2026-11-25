package com.larrystapsihan.ordering;

/**
 * Plain entry point used only for the packaged jar (see this module's
 * pom.xml launcher.class / maven-jar-plugin config). Same reasoning as
 * com.larrystapsihan.inventory.Launcher in the app module: a jar's
 * Main-Class can't extend javafx.application.Application directly or
 * "java -jar" refuses to start it without a module path.
 */
public class OrderLauncher {
    public static void main(String[] args) {
        OrderMain.main(args);
    }
}
