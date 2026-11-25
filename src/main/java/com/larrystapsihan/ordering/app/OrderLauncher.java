package com.larrystapsihan.ordering.app;

/**
 * The jar's Main-Class. A plain class that just calls OrderMain.main(),
 * because "java -jar" refuses to start a Main-Class that extends
 * javafx.application.Application ("JavaFX runtime components are missing").
 */
public class OrderLauncher {
    public static void main(String[] args) {
        OrderMain.main(args);
    }
}
