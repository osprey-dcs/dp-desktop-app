package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.grpc.v1.common.Attribute;
import com.ospreydcs.dp.grpc.v1.query.ProviderStats;
import com.ospreydcs.dp.grpc.v1.query.QueryProvidersResponse.ProvidersResult.ProviderInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the ProviderInfo -> ProviderInfoTableRow conversion: tag, attribute,
 * and PV-name formatting for TableView display.
 */
public class ProviderInfoTableRowTest {

    private static ProviderInfo.Builder baseProvider() {
        return ProviderInfo.newBuilder()
                .setId("provider-id-1")
                .setName("provider-1")
                .setDescription("test provider");
    }

    @Test
    public void conversionPassesThroughSimpleFields() {
        ProviderInfoTableRow row = new ProviderInfoTableRow(baseProvider().build());

        assertEquals("provider-id-1", row.getId());
        assertEquals("provider-1", row.getName());
        assertEquals("test provider", row.getDescription());
    }

    @Test
    public void tagsAreJoinedWithCommas() {
        ProviderInfoTableRow row = new ProviderInfoTableRow(
                baseProvider().addTags("alpha").addTags("beta").build());
        assertEquals("alpha, beta", row.getTags());
    }

    @Test
    public void attributesAreFormattedAsKeyEqualsValuePairs() {
        ProviderInfoTableRow row = new ProviderInfoTableRow(baseProvider()
                .addAttributes(Attribute.newBuilder().setName("k1").setValue("v1"))
                .addAttributes(Attribute.newBuilder().setName("k2").setValue("v2"))
                .build());
        assertEquals("k1=v1, k2=v2", row.getAttributes());
    }

    @Test
    public void pvNamesAndBucketCountComeFromProviderStats() {
        ProviderInfoTableRow row = new ProviderInfoTableRow(baseProvider()
                .setProviderStats(ProviderStats.newBuilder()
                        .addPvNames("pv-1")
                        .addPvNames("pv-2")
                        .setNumBuckets(42))
                .build());

        assertEquals("pv-1, pv-2", row.getPvNames());
        assertEquals(List.of("pv-1", "pv-2"), row.getPvNamesList());
        assertEquals("42", row.getNumBuckets());
    }

    @Test
    public void missingProviderStatsYieldsEmptyPvNamesAndZeroBuckets() {
        ProviderInfoTableRow row = new ProviderInfoTableRow(baseProvider().build());

        assertEquals("", row.getPvNames());
        assertEquals(List.of(), row.getPvNamesList());
        assertEquals("0", row.getNumBuckets());
    }

    @Test
    public void emptyTagsAndAttributesFormatAsEmptyStrings() {
        ProviderInfoTableRow row = new ProviderInfoTableRow(baseProvider().build());

        assertEquals("", row.getTags());
        assertEquals("", row.getAttributes());
    }

    @Test
    public void toStringSummarizesIdNameAndPvCount() {
        ProviderInfoTableRow row = new ProviderInfoTableRow(baseProvider()
                .setProviderStats(ProviderStats.newBuilder().addPvNames("pv-1"))
                .build());
        assertEquals("Provider[id=provider-id-1, name=provider-1, pvCount=1]", row.toString());
    }
}
