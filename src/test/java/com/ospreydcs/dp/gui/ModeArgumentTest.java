package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.gui.config.AppMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The {@code --mode=} command-line argument, which exists so an IDE run configuration or a launcher
 * script can select the mode without the {@code -D} form that {@code mvn javafx:run} silently drops.
 *
 * <p>These assert the TRANSLATION only -- that the argument lands in the system property
 * {@code AppConfiguration} reads.  Interpreting the value is {@link AppMode}'s job and is covered by
 * {@code AppConfigurationTest}; the point of routing through the property rather than parsing here
 * is that there is one resolution path, so the typo rule cannot drift between them.
 */
public class ModeArgumentTest {

    private String saved;

    @BeforeEach
    public void captureProperty() {
        saved = System.getProperty(DpDesktopApplication.DP_MODE_PROPERTY);
        System.clearProperty(DpDesktopApplication.DP_MODE_PROPERTY);
    }

    @AfterEach
    public void restoreProperty() {
        if (saved == null) {
            System.clearProperty(DpDesktopApplication.DP_MODE_PROPERTY);
        } else {
            System.setProperty(DpDesktopApplication.DP_MODE_PROPERTY, saved);
        }
    }

    @Test
    @DisplayName("--mode=deployment sets the property AppConfiguration reads")
    public void testModeApplied() {
        DpDesktopApplication.applyModeParameter("deployment");
        assertEquals("deployment", System.getProperty(DpDesktopApplication.DP_MODE_PROPERTY));
    }

    @Test
    @DisplayName("no --mode leaves the property untouched")
    public void testNoModeLeavesPropertyUnset() {
        DpDesktopApplication.applyModeParameter(null);
        assertNull(System.getProperty(DpDesktopApplication.DP_MODE_PROPERTY));
        DpDesktopApplication.applyModeParameter("   ");
        assertNull(System.getProperty(DpDesktopApplication.DP_MODE_PROPERTY),
                "a blank argument is an unfilled option, not a request for a mode");
    }

    /**
     * An explicit {@code -D} is the documented mechanism and the one a deployment install scripts.
     * A launcher that always appended {@code --mode=demo} must not override what an operator set on
     * the command line -- and specifically must not silently turn a deployment launch into a demo.
     */
    @Test
    @DisplayName("an explicit -D wins over the argument")
    public void testSystemPropertyWins() {
        System.setProperty(DpDesktopApplication.DP_MODE_PROPERTY, "deployment");
        DpDesktopApplication.applyModeParameter("demo");
        assertEquals("deployment", System.getProperty(DpDesktopApplication.DP_MODE_PROPERTY),
                "the argument must not override an explicitly set system property");
    }

    /**
     * The value is passed through rather than parsed here, so the whole of AppMode's resolution --
     * including the rule that an unrecognized value becomes DEMO rather than DEPLOYMENT -- applies
     * to the command line too.  A second parser in the launcher would be free to drift from that,
     * and the drift that matters is a typo becoming a connection attempt against production.
     */
    @Test
    @DisplayName("a misspelled --mode resolves to demo, never deployment")
    public void testTypoResolvesToDemo() {
        DpDesktopApplication.applyModeParameter("deploymnt");
        final String applied = System.getProperty(DpDesktopApplication.DP_MODE_PROPERTY);
        assertEquals("deploymnt", applied, "the raw value is passed through, not pre-parsed");
        assertEquals(AppMode.DEMO, AppMode.fromConfigValue(applied),
                "a typo must resolve to DEMO -- resolving it to DEPLOYMENT would turn a misspelling "
                        + "into a connection attempt against whatever the connect strings name");
    }

    @Test
    @DisplayName("case and surrounding whitespace survive the round trip")
    public void testCaseAndWhitespace() {
        DpDesktopApplication.applyModeParameter("  DePloyMent  ");
        assertEquals(AppMode.DEPLOYMENT,
                AppMode.fromConfigValue(System.getProperty(DpDesktopApplication.DP_MODE_PROPERTY)));
    }
}
