package com.ospreydcs.dp.gui.component;

import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Construction smoke tests for the reusable components not covered by their own instance
 * test classes: each constructor loads the component's FXML and wires its embedded controls,
 * so simple construction already catches broken component FXML ("Root value already
 * specified", missing fx:id targets).  Also pins down that the components are safe to use
 * before their dependencies are injected, which the views rely on at load time.
 */
public class ComponentConstructionSmokeTest {

    @Test
    public void providerDetailsComponentConstructsAndClears() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final ProviderDetailsComponent component = new ProviderDetailsComponent();
            component.setProviderName("provider-1");
            component.setProviderDescription("test provider");
            component.getProviderTags().add("tag-1");
            component.getProviderAttributes().add("key=value");
            assertEquals("provider-1", component.getProviderName());
            assertEquals("test provider", component.getProviderDescription());
            assertEquals(List.of("tag-1"), component.getProviderTags());
            assertEquals(List.of("key=value"), component.getProviderAttributes());

            component.clearProviderDetails();
            assertEquals("", component.getProviderName());
            assertEquals("", component.getProviderDescription());
            assertEquals(List.of(), component.getProviderTags());
            assertEquals(List.of(), component.getProviderAttributes());
        });
    }

    @Test
    public void queryPvsComponentIsInertBeforeDpApplicationInjection() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final QueryPvsComponent component = new QueryPvsComponent();
            // Pre-injection, add/remove must be no-ops rather than NPEs: the views construct
            // components during FXML load, before dependencies are injected.  Assert after
            // each call separately, so an add that mutated state cannot be masked by a
            // remove that undid it.
            assertEquals(List.of(), component.getPvNames());
            component.addPvName("pv-1");
            assertEquals(List.of(), component.getPvNames());
            component.removePvName("pv-1");
            assertEquals(List.of(), component.getPvNames());
        });
    }

    @Test
    public void subscriptionDetailsComponentConstructsEmpty() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final SubscriptionDetailsComponent component = new SubscriptionDetailsComponent();
            assertEquals(List.of(), component.getSubscriptions());
            component.clearSubscriptions();
            assertEquals(List.of(), component.getSubscriptions());
        });
    }
}
