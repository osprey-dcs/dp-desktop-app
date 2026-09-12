package com.ospreydcs.dp.gui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for DpApplication.accumulatePages(), the transparent paging helper behind queryDataSets()
 * and queryAnnotations().
 *
 * The helper is static and takes its pages through functions precisely so it can be tested without
 * a service ecosystem -- the page loop is where the edge cases live (a cap reached exactly on a
 * page boundary, a server returning a token with an empty page, a page overshooting the cap), and
 * getting truncation wrong reintroduces the silent-wrong-total bug it exists to fix.
 */
public class DpApplicationPagingTest {

    /**
     * A stub page: the records it carries and the token pointing at the next one.
     */
    private record Page(List<String> records, String nextPageToken) {}

    /**
     * Builds a fake server holding the given records, handing them out in pages of pageSize and
     * recording the tokens it was asked for.
     */
    private static class FakePagedSource {

        private final List<String> records;
        private final int pageSize;
        final List<String> requestedTokens = new ArrayList<>();

        FakePagedSource(List<String> records, int pageSize) {
            this.records = records;
            this.pageSize = pageSize;
        }

        Page fetch(String pageToken) {
            requestedTokens.add(pageToken);
            final int start = (pageToken == null) ? 0 : Integer.parseInt(pageToken);
            final int end = Math.min(start + pageSize, records.size());
            final String nextToken = (end < records.size()) ? String.valueOf(end) : "";
            return new Page(records.subList(start, end), nextToken);
        }
    }

    private static List<String> records(int count) {
        return IntStream.range(0, count).mapToObj(i -> "r" + i).collect(Collectors.toList());
    }

    private static DpApplication.PagedResult<String> accumulate(FakePagedSource source, int cap) {
        return DpApplication.accumulatePages(
                source::fetch, Page::nextPageToken, Page::records, cap);
    }

    @Test
    public void singlePageIsReturnedWhole() {
        FakePagedSource source = new FakePagedSource(records(3), 10);

        DpApplication.PagedResult<String> result = accumulate(source, 100);

        assertEquals(records(3), result.records);
        assertFalse(result.truncated);
        // one request, and the first page must be requested with a null token
        assertEquals(1, source.requestedTokens.size());
        assertNull(source.requestedTokens.get(0));
    }

    @Test
    public void multiplePagesAreAccumulatedInOrder() {
        FakePagedSource source = new FakePagedSource(records(25), 10);

        DpApplication.PagedResult<String> result = accumulate(source, 100);

        assertEquals(records(25), result.records);
        assertFalse(result.truncated);
        assertEquals(3, source.requestedTokens.size());
    }

    @Test
    public void emptyResultIsNotTruncated() {
        FakePagedSource source = new FakePagedSource(List.of(), 10);

        DpApplication.PagedResult<String> result = accumulate(source, 100);

        assertEquals(List.of(), result.records);
        assertFalse(result.truncated);
    }

    @Test
    public void accumulationStopsAtTheCapAndReportsTruncation() {
        FakePagedSource source = new FakePagedSource(records(100), 10);

        DpApplication.PagedResult<String> result = accumulate(source, 25);

        assertEquals(25, result.records.size());
        assertEquals(records(25), result.records);
        assertTrue(result.truncated);
    }

    /**
     * A cap landing exactly on a page boundary is the case most easily got wrong: the page fit, so
     * nothing was dropped mid-page, but the server still offers a next page -- which means records
     * remain and the result IS truncated.
     */
    @Test
    public void capReachedExactlyOnAPageBoundaryWithMoreAvailableIsTruncated() {
        FakePagedSource source = new FakePagedSource(records(50), 10);

        DpApplication.PagedResult<String> result = accumulate(source, 20);

        assertEquals(20, result.records.size());
        assertTrue(result.truncated);
        // it must not fetch a page it has no room for
        assertEquals(2, source.requestedTokens.size());
    }

    /**
     * The mirror of the above: the record count equals the cap exactly and there is no next page,
     * so the result is complete.  Reporting this as truncated would tell the user records are
     * missing when none are.
     */
    @Test
    public void resultExactlyFillingTheCapWithNoMorePagesIsNotTruncated() {
        FakePagedSource source = new FakePagedSource(records(20), 10);

        DpApplication.PagedResult<String> result = accumulate(source, 20);

        assertEquals(20, result.records.size());
        assertFalse(result.truncated);
    }

    /**
     * A server may return a next page token alongside an empty page.  The loop must follow the
     * token rather than treat the empty page as the end.
     */
    @Test
    public void emptyPageWithATokenIsFollowed() {
        List<Page> pages = List.of(
                new Page(List.of(), "1"),
                new Page(List.of("r0"), ""));

        DpApplication.PagedResult<String> result = DpApplication.accumulatePages(
                pageToken -> pages.get(pageToken == null ? 0 : Integer.parseInt(pageToken)),
                Page::nextPageToken,
                Page::records,
                100);

        assertEquals(List.of("r0"), result.records);
        assertFalse(result.truncated);
    }

    @Test
    public void nullNextPageTokenEndsTheQuery() {
        DpApplication.PagedResult<String> result = DpApplication.accumulatePages(
                pageToken -> new Page(List.of("r0"), null),
                Page::nextPageToken,
                Page::records,
                100);

        assertEquals(List.of("r0"), result.records);
        assertFalse(result.truncated);
    }

    @Test
    public void nullRecordListOnAPageIsToleratedRatherThanThrowing() {
        DpApplication.PagedResult<String> result = DpApplication.accumulatePages(
                pageToken -> new Page(null, ""),
                Page::nextPageToken,
                Page::records,
                100);

        assertEquals(List.of(), result.records);
        assertFalse(result.truncated);
    }

    /**
     * A null page must abort rather than be read as the end of the query.
     *
     * Distinct from a failure that throws: a fetchPage returning null cannot be distinguished from
     * an empty page inside the loop, so ending the query there would report a partial accumulation
     * as complete (truncated=false) -- the silent-wrong-total bug, reached by a different route.
     * The contract requires throwing, and this asserts a null return is rejected rather than
     * quietly tolerated.
     */
    @Test
    public void aNullPageAbortsRatherThanEndingTheQuery() {
        DpApplication.QueryFailedException thrown = assertThrows(
                DpApplication.QueryFailedException.class,
                () -> DpApplication.accumulatePages(
                        pageToken -> {
                            if (pageToken == null) {
                                return new Page(List.of("r0"), "1");
                            }
                            return null;
                        },
                        Page::nextPageToken,
                        Page::records,
                        100));

        assertTrue(thrown.getMessage().contains("null"),
                "the failure must name the null page, not just fail: " + thrown.getMessage());
    }

    /**
     * A null FIRST page aborts too, so an immediately-failing query never looks like an empty
     * result set -- "no annotations found" and "the query failed" must not be the same outcome.
     */
    @Test
    public void aNullFirstPageAbortsRatherThanYieldingAnEmptyResult() {
        assertThrows(
                DpApplication.QueryFailedException.class,
                () -> DpApplication.accumulatePages(
                        pageToken -> null,
                        Page::nextPageToken,
                        Page::records,
                        100));
    }

    /**
     * A failure partway through must abort the whole accumulation rather than return what had been
     * collected: a partial list presented as a complete one is the exact bug this work fixes.
     */
    @Test
    public void aFailedPageAbortsTheAccumulation() {
        DpApplication.QueryFailedException thrown = assertThrows(
                DpApplication.QueryFailedException.class,
                () -> DpApplication.accumulatePages(
                        pageToken -> {
                            if (pageToken == null) {
                                return new Page(List.of("r0"), "1");
                            }
                            throw new DpApplication.QueryFailedException("page 2 failed");
                        },
                        Page::nextPageToken,
                        Page::records,
                        100));

        assertEquals("page 2 failed", thrown.getMessage());
    }

    @Test
    public void describeCountNamesTruncationSoACappedResultIsNotReportedAsATotal() {
        assertEquals("found 3 annotation(s)",
                new DpApplication.PagedResult<>(records(3), false).describeCount("annotation"));

        assertEquals("showing first 3 annotation(s), more available",
                new DpApplication.PagedResult<>(records(3), true).describeCount("annotation"));
    }

    @Test
    public void capConstantIsTheSettledValue() {
        assertEquals(5000, DpApplication.QUERY_RESULT_CAP);
    }
}
