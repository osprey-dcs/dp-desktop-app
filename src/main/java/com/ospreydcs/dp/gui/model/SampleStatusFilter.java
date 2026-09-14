package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.client.QueryClient;
import com.ospreydcs.dp.grpc.v1.query.SampleStatusSelector;

import java.util.ArrayList;
import java.util.List;

/**
 * An optional restriction of a Query API V2 sample query to samples carrying (or not carrying) a
 * matching sample status -- the app-side counterpart of {@code QuerySpec.sampleStatusSelector}.
 *
 * <p>This is the join between the query view and the Sample Status API: the statuses the demo
 * generator writes, and the ones the Sample Status Explore view displays, are the same records this
 * filters on.  A status labels a sample only by <strong>exact {@code (pvName, timestamp)} equality
 * at nanosecond precision</strong>, so most samples in a typical archive are unlabeled.
 *
 * <p><strong>That is what makes the mode more than a polarity switch.</strong>  The two modes differ
 * in how they treat samples with NO status in the selected domain and layers, and the difference is
 * not recoverable from the mode names:
 *
 * <table border="1">
 *   <caption>How each mode treats an unlabeled sample</caption>
 *   <tr><th>Mode</th><th>Labeled + code matches</th><th>Unlabeled</th></tr>
 *   <tr><td>{@code INCLUDE}</td><td>returned</td><td><strong>excluded</strong></td></tr>
 *   <tr><td>{@code EXCLUDE}</td><td>dropped</td><td><strong>returned</strong></td></tr>
 * </table>
 *
 * <p>So the two are not complements over a partially-labeled archive, and picking the wrong one is
 * silent: {@code INCLUDE} over a sparsely labeled PV returns a nearly empty table that reads as "no
 * data in this window" rather than as "almost nothing here is labeled".  {@link #describe()} states
 * the unlabeled behavior in words for exactly this reason, and {@link Mode} carries it in its label.
 *
 * <p><strong>A filtered-out sample becomes a missing value, not a missing row.</strong>  The server
 * blanks it at its {@code (PV, timestamp)} position in the {@code ColumnTable}; a timestamp is
 * dropped entirely only when every selected PV is filtered out at it.  So a filtered query normally
 * yields the same row count with blanks in it, which the results table already renders as genuinely
 * missing (V2 encodes it as an unset {@code DataValue} oneof).
 *
 * <p><strong>Accepted by the sample-oriented methods only.</strong>  The server rejects this
 * selector on {@code queryBuckets()} / {@code queryBucketsStream()}, which return buckets whole and
 * cannot represent per-sample filtering -- which is why the client's {@code QueryBucketsParams} omits
 * the field entirely rather than carrying one the server would refuse.  This app queries samples, so
 * the restriction is not reachable here, but it is why the filter lives on the samples call.
 *
 * <p><strong>Domain and mode are both required by the server; layers and codes are optional.</strong>
 * An empty layers list selects every layer in the domain, and an empty status-code list matches a
 * status with any code -- which is how "labeled at all" is expressed without enumerating a domain's
 * codes, something the MLDP itself does not know since the domain registry is unimplemented.
 *
 * <p>Instances are immutable, so a cancelled modal leaves nothing half-applied.
 */
public final class SampleStatusFilter {

    /**
     * Which samples survive the filter.
     *
     * <p>The labels name the effect on <em>unlabeled</em> samples, because that is the half of the
     * behavior the proto enum names omit and the half that decides whether a result reads as empty.
     */
    public enum Mode {
        INCLUDE_MATCHING("Only matching samples (drops unlabeled samples)",
                SampleStatusSelector.Mode.MODE_INCLUDE_MATCHING),
        EXCLUDE_MATCHING("All but matching samples (keeps unlabeled samples)",
                SampleStatusSelector.Mode.MODE_EXCLUDE_MATCHING);

        private final String label;
        private final SampleStatusSelector.Mode protoMode;

        Mode(String label, SampleStatusSelector.Mode protoMode) {
            this.label = label;
            this.protoMode = protoMode;
        }

        public String getLabel() {
            return label;
        }

        public SampleStatusSelector.Mode getProtoMode() {
            return protoMode;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final boolean active;
    private final String domain;
    private final List<String> layers;
    private final List<Integer> statusCodes;
    private final Mode mode;

    private SampleStatusFilter(
            boolean active, String domain, List<String> layers, List<Integer> statusCodes, Mode mode) {
        this.active = active;
        this.domain = domain == null || domain.isBlank() ? null : domain.trim();
        this.layers = layers == null ? List.of() : List.copyOf(layers);
        this.statusCodes = statusCodes == null ? List.of() : List.copyOf(statusCodes);
        this.mode = mode == null ? Mode.INCLUDE_MATCHING : mode;
    }

    /** The filter that restricts nothing -- the default, and the state the Query Editor starts in. */
    public static SampleStatusFilter none() {
        return new SampleStatusFilter(false, null, null, null, null);
    }

    public static SampleStatusFilter of(
            String domain, List<String> layers, List<Integer> statusCodes, Mode mode) {
        return new SampleStatusFilter(true, domain, layers, statusCodes, mode);
    }

    public boolean isActive() { return active; }
    public String getDomain() { return domain; }
    public List<String> getLayers() { return layers; }
    public List<Integer> getStatusCodes() { return statusCodes; }
    public Mode getMode() { return mode; }

    /**
     * Whether an active filter carries everything the server requires.
     *
     * <p>Domain is the only client-checkable rule: the server rejects a blank one with
     * "sampleStatusSelector.domain must be specified", and a blank field is an unfilled control
     * rather than an intent.  Mode always has a value because the enum has no unset arm -- the
     * proto's {@code MODE_UNSPECIFIED} is deliberately not mapped, so the "rejected for an
     * unspecified mode" case is unrepresentable rather than merely avoided.
     */
    public boolean isComplete() {
        return !active || domain != null;
    }

    /**
     * The selector params to hand to {@code QuerySamplesParams}, or null when inactive.
     *
     * <p>Unlike {@link ConfigurationFilter#toCriteria()}, null here is unambiguous: the params
     * record carries a single nullable selector rather than a list whose emptiness has two
     * meanings, so there is no empty form for the server to reject.
     */
    public QueryClient.SampleStatusSelectorParams toSelectorParams() {
        if (!active) {
            return null;
        }
        return new QueryClient.SampleStatusSelectorParams(
                domain,
                layers.isEmpty() ? null : new ArrayList<>(layers),
                statusCodes.isEmpty() ? null : new ArrayList<>(statusCodes),
                mode.getProtoMode());
    }

    /**
     * The one-line summary shown in the Query Editor and in status messages.
     *
     * <p>States what happens to unlabeled samples, because that is what decides whether a sparse
     * result means "no data" or "nothing labeled", and the mode name alone does not say.
     */
    public String describe() {
        if (!active) {
            return "all samples (no status filter)";
        }
        // An active filter whose domain is still blank is a reachable, transient state -- the
        // dialog describes what it holds while it is being edited.  Rendering it as the literal
        // "null" would read as a domain by that name rather than as an unfilled field.
        final String domainText = domain == null ? "an unspecified domain" : "domain " + domain;
        final String scope = layers.isEmpty()
                ? domainText + " (all layers)"
                : domainText + ", layer" + (layers.size() == 1 ? " " : "s ")
                        + String.join("/", layers);
        final String codes = statusCodes.isEmpty()
                ? "any status code"
                : "status code" + (statusCodes.size() == 1 ? " " : "s ") + join(statusCodes);
        return mode == Mode.INCLUDE_MATCHING
                ? "only samples with " + codes + " in " + scope + " (unlabeled samples dropped)"
                : "all samples except those with " + codes + " in " + scope
                        + " (unlabeled samples kept)";
    }

    private static String join(List<Integer> values) {
        final List<String> parts = new ArrayList<>();
        for (Integer value : values) {
            parts.add(String.valueOf(value));
        }
        return String.join("/", parts);
    }
}
