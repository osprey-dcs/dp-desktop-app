package com.ospreydcs.dp.gui;

import javafx.application.Application;

/**
 * Launcher for the desktop application.
 *
 * The java launcher refuses to start a main class that extends javafx.application.Application when
 * JavaFX is on the classpath rather than the module path, failing before any application code runs.
 * This class does not extend Application, so it sidesteps that check and is the entry point used by
 * the javafx-maven-plugin and the shaded jar manifest.
 */
public class DpDesktopApplicationRunner {

    public static void main(String[] args) {
        Application.launch(DpDesktopApplication.class, args);
    }
}
