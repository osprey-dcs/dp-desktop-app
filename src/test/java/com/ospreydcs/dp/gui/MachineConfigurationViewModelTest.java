package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.result.ApiResultStatus;
import com.ospreydcs.dp.client.result.GetConfigurationActivationApiResult;
import com.ospreydcs.dp.client.result.SaveConfigurationActivationApiResult;
import com.ospreydcs.dp.grpc.v1.common.ConfigurationActivation;
import com.ospreydcs.dp.gui.component.AttributesListComponent;
import com.ospreydcs.dp.gui.component.TagsListComponent;
import com.ospreydcs.dp.gui.model.ConfigurationActivationDetail;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the parts of MachineConfigurationViewModel that are reachable without a backend:
 * form clearing, the controller-owned temporal reset callback, and the activation section gate.
 *
 * The save paths themselves need a DpApplication and are covered by the end-to-end run against a
 * live service ecosystem, not here.  What these tests do pin down is the state handling around
 * those calls, which is where the reset and list-scoping defects lived.
 *
 * Bodies run on the FX thread because the ViewModel's properties and observable lists are the
 * same ones a live view binds to.
 */
public class MachineConfigurationViewModelTest {

    /**
     * A ViewModel with all four list components attached, as the controller wires it.
     */
    private static MachineConfigurationViewModel viewModelWithComponents(
            TagsListComponent configurationTags,
            AttributesListComponent configurationAttributes,
            TagsListComponent activationTags,
            AttributesListComponent activationAttributes
    ) {
        final MachineConfigurationViewModel viewModel = new MachineConfigurationViewModel();
        viewModel.setConfigurationTagsComponent(configurationTags);
        viewModel.setConfigurationAttributesComponent(configurationAttributes);
        viewModel.setActivationTagsComponent(activationTags);
        viewModel.setActivationAttributesComponent(activationAttributes);
        return viewModel;
    }

    @Test
    public void resetFormClearsEveryFieldAndComponent() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final TagsListComponent configurationTags = new TagsListComponent();
            final AttributesListComponent configurationAttributes = new AttributesListComponent();
            final TagsListComponent activationTags = new TagsListComponent();
            final AttributesListComponent activationAttributes = new AttributesListComponent();

            final MachineConfigurationViewModel viewModel = viewModelWithComponents(
                    configurationTags, configurationAttributes, activationTags, activationAttributes);

            viewModel.configurationNameProperty().set("beamline-a");
            viewModel.categoryProperty().set("beamline");
            viewModel.configurationDescriptionProperty().set("a description");
            viewModel.parentConfigurationNameProperty().set("facility");
            viewModel.configurationModifiedByProperty().set("someone");
            viewModel.clientActivationIdProperty().set("activation-1");
            viewModel.activationDescriptionProperty().set("an activation");
            viewModel.activationModifiedByProperty().set("someone else");

            configurationTags.addTag("config-tag");
            configurationAttributes.addAttribute("config-key", "config-value");
            activationTags.addTag("activation-tag");
            activationAttributes.addAttribute("activation-key", "activation-value");

            viewModel.resetForm();

            assertEquals("", viewModel.configurationNameProperty().get());
            assertEquals("", viewModel.categoryProperty().get());
            assertEquals("", viewModel.configurationDescriptionProperty().get());
            assertEquals("", viewModel.parentConfigurationNameProperty().get());
            assertEquals("", viewModel.configurationModifiedByProperty().get());
            assertEquals("", viewModel.clientActivationIdProperty().get());
            assertEquals("", viewModel.activationDescriptionProperty().get());
            assertEquals("", viewModel.activationModifiedByProperty().get());

            assertTrue(configurationTags.getTags().isEmpty(), "configuration tags not cleared");
            assertTrue(configurationAttributes.getAttributes().isEmpty(),
                    "configuration attributes not cleared");
            assertTrue(activationTags.getTags().isEmpty(), "activation tags not cleared");
            assertTrue(activationAttributes.getAttributes().isEmpty(),
                    "activation attributes not cleared");
        });
    }

    /**
     * The date pickers and time spinners are owned by the controller, so the ViewModel can only
     * clear them by calling back into it.  Reset must make that call: without it the next
     * activation silently reuses the previous interval's time of day.
     */
    @Test
    public void resetFormInvokesTheTemporalFieldsResetCallback() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final MachineConfigurationViewModel viewModel = new MachineConfigurationViewModel();
            final AtomicInteger resetCallCount = new AtomicInteger();
            viewModel.setActivationTemporalFieldsReset(resetCallCount::incrementAndGet);

            viewModel.resetForm();

            assertEquals(1, resetCallCount.get(),
                    "resetForm() must clear the controller-owned date and time controls");
        });
    }

    @Test
    public void resetFormClearsTheSessionActivationListAndRedisablesTheSection() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final MachineConfigurationViewModel viewModel = new MachineConfigurationViewModel();

            viewModel.getActivations().add(new ConfigurationActivationDetail(
                    "activation-1", "beamline-a", Instant.EPOCH, Instant.EPOCH.plusSeconds(3600)));
            viewModel.savedConfigurationNameProperty().set("beamline-a");
            viewModel.configurationSavedProperty().set(true);

            viewModel.resetForm();

            assertTrue(viewModel.getActivations().isEmpty(), "session activation list not cleared");
            assertEquals("", viewModel.savedConfigurationNameProperty().get());
            assertFalse(viewModel.configurationSavedProperty().get(),
                    "activation section must be re-disabled after a reset");
        });
    }

    /**
     * addActivation() refuses to call the service before a configuration has been saved, which is
     * the same condition the disabled activation section expresses in the UI.
     */
    @Test
    public void addActivationIsRefusedBeforeAConfigurationIsSaved() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final MachineConfigurationViewModel viewModel = new MachineConfigurationViewModel();
            // No DpApplication is injected: reaching the service call would NPE, so a clean
            // status message is itself the assertion that the guard fired first.
            viewModel.addActivation(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600));

            assertEquals("DpApplication not initialized", viewModel.statusMessageProperty().get());
            assertTrue(viewModel.getActivations().isEmpty());
        });
    }

    // =====================================================================================
    // Activation id collision check (issue #36)
    //
    // The two-stage check is the point of these tests: stage one matches the session list on the
    // FX thread, stage two asks the server from the background task.  What is worth pinning is
    // the ORDERING and the SKIPS - a refactor that turns every add into a round trip, or that
    // lets an unconfirmed overwrite through, is exactly the regression these catch.
    // =====================================================================================

    /**
     * A DpApplication that answers the two calls addActivation() makes, without a service
     * ecosystem.  The implicit constructor leaves the ApiClient null, so nothing here touches
     * gRPC; every overridden method must therefore avoid calling super.
     */
    private static final class FakeDpApplication extends DpApplication {

        private final GetConfigurationActivationApiResult getResult;
        private final AtomicInteger getCallCount = new AtomicInteger();
        private final AtomicInteger saveCallCount = new AtomicInteger();
        private final AtomicReference<String> savedActivationId = new AtomicReference<>();

        FakeDpApplication(GetConfigurationActivationApiResult getResult) {
            this.getResult = getResult;
        }

        @Override
        public GetConfigurationActivationApiResult getConfigurationActivationById(String clientActivationId) {
            getCallCount.incrementAndGet();
            return getResult;
        }

        @Override
        public SaveConfigurationActivationApiResult saveConfigurationActivation(
                String clientActivationId,
                String configurationName,
                Instant startTime,
                Instant endTime,
                String description,
                List<String> tags,
                Map<String, String> attributeMap,
                String modifiedBy
        ) {
            saveCallCount.incrementAndGet();
            savedActivationId.set(clientActivationId);
            return new SaveConfigurationActivationApiResult(clientActivationId);
        }
    }

    private static GetConfigurationActivationApiResult notFound() {
        return new GetConfigurationActivationApiResult(
                true, "no ConfigurationActivation found", ApiResultStatus.REJECT);
    }

    private static GetConfigurationActivationApiResult found() {
        return new GetConfigurationActivationApiResult(ConfigurationActivation.newBuilder().build());
    }

    private static GetConfigurationActivationApiResult serviceError() {
        return new GetConfigurationActivationApiResult(
                true, "UNAVAILABLE: io exception", ApiResultStatus.ERROR);
    }

    /**
     * A ViewModel gated open for activations, with the fake application injected.
     */
    private static MachineConfigurationViewModel activationReadyViewModel(FakeDpApplication application) {
        final MachineConfigurationViewModel viewModel = new MachineConfigurationViewModel();
        viewModel.setDpApplication(application);
        viewModel.savedConfigurationNameProperty().set("beamline-a");
        viewModel.configurationSavedProperty().set(true);
        return viewModel;
    }

    /**
     * Runs addActivation() and returns once its outcome has been applied.
     *
     * The save runs on a daemon thread and reports back through Platform.runLater, so neither the
     * call returning nor the thread finishing means the ViewModel has settled.  isSaving goes false
     * in that same runLater as every status and list update, so waiting on it waits for all of
     * them.
     */
    private static void addActivationAndAwait(
            MachineConfigurationViewModel viewModel, Instant start, Instant end) throws Exception {

        final CountDownLatch settled = new CountDownLatch(1);

        FxToolkitSupport.runOnFxThread(() -> {
            viewModel.isSavingProperty().addListener((observable, wasSaving, isSaving) -> {
                if (!isSaving) {
                    settled.countDown();
                }
            });
            viewModel.addActivation(start, end);
        });

        assertTrue(settled.await(30, TimeUnit.SECONDS), "addActivation did not settle");

        // One more FX-thread round trip, so anything queued behind the settling runLater has run.
        FxToolkitSupport.runOnFxThread(() -> { });
    }

    /**
     * A blank id asks the server to generate one, so there is nothing that can collide and no
     * reason to spend a round trip asking.
     */
    @Test
    public void aBlankActivationIdSkipsTheServerCollisionCheck() throws Exception {
        final FakeDpApplication application = new FakeDpApplication(found());
        final MachineConfigurationViewModel viewModel = activationReadyViewModel(application);
        viewModel.clientActivationIdProperty().set("");

        addActivationAndAwait(viewModel, Instant.EPOCH, Instant.EPOCH.plusSeconds(3600));

        assertEquals(0, application.getCallCount.get(),
                "a blank activation id must not be looked up on the server");
        assertEquals(1, application.saveCallCount.get(), "the save must still go ahead");
    }

    /**
     * Stage one short-circuits stage two.  This pins the ordering: without it a refactor could
     * turn every add into a round trip, and could ask the user the same question twice for a
     * record the session already knows about.
     */
    @Test
    public void anIdMatchedInTheSessionListSkipsTheServerCheck() throws Exception {
        final FakeDpApplication application = new FakeDpApplication(found());
        final MachineConfigurationViewModel viewModel = activationReadyViewModel(application);

        final AtomicInteger confirmations = new AtomicInteger();
        viewModel.setActivationOverwriteConfirmation(id -> {
            confirmations.incrementAndGet();
            return true;
        });

        FxToolkitSupport.runOnFxThread(() -> viewModel.getActivations().add(
                new ConfigurationActivationDetail(
                        "activation-1", "beamline-a", Instant.EPOCH, Instant.EPOCH.plusSeconds(60))));
        viewModel.clientActivationIdProperty().set("activation-1");

        addActivationAndAwait(viewModel, Instant.EPOCH, Instant.EPOCH.plusSeconds(3600));

        assertEquals(0, application.getCallCount.get(),
                "an id already in the session list must not be looked up on the server");
        assertEquals(1, confirmations.get(), "the user must be asked exactly once");
        assertEquals(1, application.saveCallCount.get());
    }

    /**
     * The case this ticket exists for: an id absent from the session list but present on the
     * server.  Before the server check this replaced the record silently.
     */
    @Test
    public void anIdFoundOnlyOnTheServerRaisesTheConfirmation() throws Exception {
        final FakeDpApplication application = new FakeDpApplication(found());
        final MachineConfigurationViewModel viewModel = activationReadyViewModel(application);

        final AtomicReference<String> confirmedId = new AtomicReference<>();
        viewModel.setActivationOverwriteConfirmation(id -> {
            confirmedId.set(id);
            return true;
        });
        viewModel.clientActivationIdProperty().set("activation-from-a-previous-session");

        addActivationAndAwait(viewModel, Instant.EPOCH, Instant.EPOCH.plusSeconds(3600));

        assertEquals(1, application.getCallCount.get());
        assertEquals("activation-from-a-previous-session", confirmedId.get(),
                "a record known only to the server must still be confirmed before replacing");
        assertEquals(1, application.saveCallCount.get(), "confirming must let the save proceed");
    }

    @Test
    public void decliningTheServerSideCollisionAbortsTheSave() throws Exception {
        final FakeDpApplication application = new FakeDpApplication(found());
        final MachineConfigurationViewModel viewModel = activationReadyViewModel(application);
        viewModel.setActivationOverwriteConfirmation(id -> false);
        viewModel.clientActivationIdProperty().set("activation-from-a-previous-session");

        addActivationAndAwait(viewModel, Instant.EPOCH, Instant.EPOCH.plusSeconds(3600));

        assertEquals(0, application.saveCallCount.get(), "a declined overwrite must not be saved");
        assertTrue(viewModel.getActivations().isEmpty(),
                "a declined overwrite must not appear in the session list");
        assertEquals("Add cancelled: existing activation not replaced",
                viewModel.statusMessageProperty().get());
    }

    /**
     * A rejection is how the server reports "no such record", so the save proceeds with no dialog.
     * Branching on isReject() rather than isError() is what makes this distinguishable from the
     * service-failure case below.
     */
    @Test
    public void aRejectedLookupMeansNoSuchRecordAndTheSaveProceeds() throws Exception {
        final FakeDpApplication application = new FakeDpApplication(notFound());
        final MachineConfigurationViewModel viewModel = activationReadyViewModel(application);

        final AtomicInteger confirmations = new AtomicInteger();
        viewModel.setActivationOverwriteConfirmation(id -> {
            confirmations.incrementAndGet();
            return true;
        });
        viewModel.clientActivationIdProperty().set("a-brand-new-id");

        addActivationAndAwait(viewModel, Instant.EPOCH, Instant.EPOCH.plusSeconds(3600));

        assertEquals(1, application.getCallCount.get());
        assertEquals(0, confirmations.get(), "a not-found lookup must not raise a dialog");
        assertEquals(1, application.saveCallCount.get());
        assertEquals("a-brand-new-id", application.savedActivationId.get());
    }

    /**
     * An unreachable service is NOT a not-found.  Proceeding here would reproduce the silent
     * replacement precisely when the system is unhealthy, so the save aborts - matching what the
     * configuration save already does for the same class of failure.
     */
    @Test
    public void aFailedLookupAbortsTheSaveRatherThanOverwritingBlind() throws Exception {
        final FakeDpApplication application = new FakeDpApplication(serviceError());
        final MachineConfigurationViewModel viewModel = activationReadyViewModel(application);

        final AtomicInteger confirmations = new AtomicInteger();
        viewModel.setActivationOverwriteConfirmation(id -> {
            confirmations.incrementAndGet();
            return true;
        });
        viewModel.clientActivationIdProperty().set("an-id");

        addActivationAndAwait(viewModel, Instant.EPOCH, Instant.EPOCH.plusSeconds(3600));

        assertEquals(0, confirmations.get(),
                "an unverifiable check must not be turned into a user decision here");
        assertEquals(0, application.saveCallCount.get(),
                "the save must not be attempted when the collision check failed");
        assertTrue(viewModel.statusMessageProperty().get()
                        .startsWith("Save failed: could not check for an existing activation"),
                "the status must say the check failed, not that the save failed: "
                        + viewModel.statusMessageProperty().get());
    }

    /**
     * A null result is as unusable as a failed one, and is handled the same way rather than
     * falling through to an NPE or to a blind overwrite.
     */
    @Test
    public void aNullLookupResultAbortsTheSave() throws Exception {
        final FakeDpApplication application = new FakeDpApplication(null);
        final MachineConfigurationViewModel viewModel = activationReadyViewModel(application);
        viewModel.clientActivationIdProperty().set("an-id");

        addActivationAndAwait(viewModel, Instant.EPOCH, Instant.EPOCH.plusSeconds(3600));

        assertEquals(0, application.saveCallCount.get());
        assertEquals("Save failed: could not check for an existing activation",
                viewModel.statusMessageProperty().get());
    }

    /**
     * With no confirmation handler wired the save proceeds rather than deadlocking - the same
     * fallback the configuration path takes, and the reason a missing handler is logged as a
     * warning rather than silently swallowed.
     */
    @Test
    public void aMissingConfirmationHandlerProceedsRatherThanDeadlocking() throws Exception {
        final FakeDpApplication application = new FakeDpApplication(found());
        final MachineConfigurationViewModel viewModel = activationReadyViewModel(application);
        viewModel.clientActivationIdProperty().set("an-id");

        addActivationAndAwait(viewModel, Instant.EPOCH, Instant.EPOCH.plusSeconds(3600));

        assertEquals(1, application.saveCallCount.get());
    }

    /**
     * A save that replaced an existing record must show the replacement in place of the stale row,
     * not beside it.  The server-side collision path reaches the same reconciliation the
     * session-local one already used, so this pins that it was not bypassed.
     */
    @Test
    public void aServerSideReplacementReconcilesTheSessionListInPlace() throws Exception {
        final FakeDpApplication application = new FakeDpApplication(found());
        final MachineConfigurationViewModel viewModel = activationReadyViewModel(application);
        viewModel.setActivationOverwriteConfirmation(id -> true);

        FxToolkitSupport.runOnFxThread(() -> viewModel.getActivations().add(
                new ConfigurationActivationDetail(
                        "other-activation", "beamline-a", Instant.EPOCH, Instant.EPOCH.plusSeconds(60))));
        viewModel.clientActivationIdProperty().set("server-side-id");

        addActivationAndAwait(viewModel, Instant.EPOCH.plusSeconds(7200),
                Instant.EPOCH.plusSeconds(10800));

        assertEquals(2, viewModel.getActivations().size(),
                "an id absent from the session list is a new row, not a replacement");

        // Saving the same id again replaces the row rather than adding a third.
        viewModel.clientActivationIdProperty().set("server-side-id");
        addActivationAndAwait(viewModel, Instant.EPOCH.plusSeconds(20000),
                Instant.EPOCH.plusSeconds(30000));

        assertEquals(2, viewModel.getActivations().size(),
                "re-saving an id already listed must reconcile in place, not append");
        assertNotNull(viewModel.getActivations().stream()
                .filter(activation -> "server-side-id".equals(activation.clientActivationId))
                .findFirst().orElse(null));
    }
}
