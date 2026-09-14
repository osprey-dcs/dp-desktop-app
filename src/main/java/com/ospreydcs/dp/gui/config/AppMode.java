package com.ospreydcs.dp.gui.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Which set of gRPC targets the application runs against.
 *
 * <p>{@link #DEMO} starts a self-contained in-process service ecosystem backed by the local
 * {@code dp-demo} MongoDB database.  {@link #DEPLOYMENT} connects to already-running remote
 * services and never constructs a MongoDB client at all.
 *
 * <p>The mode also decides which features are available; see the mode matrix in
 * {@code plan/tickets/4/plan.md}.
 */
public enum AppMode {

    DEMO,
    DEPLOYMENT;

    private static final Logger logger = LogManager.getLogger();

    /** The mode used when configuration says nothing, or says something unrecognized. */
    public static final AppMode DEFAULT = DEMO;

    /**
     * Resolves a configured mode value, defaulting to {@link #DEMO}.
     *
     * <p>An unrecognized value resolves to DEMO rather than to DEPLOYMENT, and that direction is
     * deliberate: resolving a typo to DEPLOYMENT would turn a misspelling into a connection
     * attempt against whatever hosts the connect strings name, while resolving it to DEMO fails
     * visibly and harmlessly.  The value is matched case-insensitively and trimmed, since it can
     * arrive from a YAML file or a {@code -Ddp.DpDesktopApp.mode=...} command line.
     */
    public static AppMode fromConfigValue(String value) {

        if (value == null || value.isBlank()) {
            return DEFAULT;
        }

        final String normalized = value.trim().toUpperCase();
        for (AppMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }

        logger.warn("unrecognized application mode: '{}', using {}", value, DEFAULT);
        return DEFAULT;
    }

    public boolean isDemo() {
        return this == DEMO;
    }

    public boolean isDeployment() {
        return this == DEPLOYMENT;
    }
}
