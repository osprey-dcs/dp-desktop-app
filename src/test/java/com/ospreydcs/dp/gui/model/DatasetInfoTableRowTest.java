package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.grpc.v1.annotation.DataBlock;
import com.ospreydcs.dp.grpc.v1.annotation.DataSet;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the DataSet -> DatasetInfoTableRow conversion, in particular the
 * "[pv-1, pv-2: yyyy-MM-dd HH:mm:ss -> yyyy-MM-dd HH:mm:ss]" data block formatting.
 * The test JVM runs in UTC (see surefire argLine), so expected strings are literal.
 */
public class DatasetInfoTableRowTest {

    private static Timestamp timestamp(String isoInstant) {
        Instant instant = Instant.parse(isoInstant);
        return Timestamp.newBuilder()
                .setEpochSeconds(instant.getEpochSecond())
                .setNanoseconds(instant.getNano())
                .build();
    }

    private static DataBlock.Builder sampleBlock() {
        return DataBlock.newBuilder()
                .addPvNames("pv-1")
                .addPvNames("pv-2")
                .setBeginTime(timestamp("2025-09-03T00:00:00Z"))
                .setEndTime(timestamp("2025-09-03T00:10:00Z"));
    }

    private static DataSet.Builder baseDataSet() {
        return DataSet.newBuilder()
                .setId("ds-1")
                .setName("dataset-1")
                .setOwnerId("owner-1")
                .setDescription("test dataset");
    }

    @Test
    public void conversionPassesThroughSimpleFields() {
        DatasetInfoTableRow row = new DatasetInfoTableRow(baseDataSet().build());

        assertEquals("ds-1", row.getId());
        assertEquals("dataset-1", row.getName());
        assertEquals("owner-1", row.getOwner());
        assertEquals("test dataset", row.getDescription());
    }

    @Test
    public void dataBlocksAreFormattedWithPvNamesAndTimeRange() {
        DatasetInfoTableRow row = new DatasetInfoTableRow(
                baseDataSet().addDataBlocks(sampleBlock()).build());
        assertEquals("[pv-1, pv-2: 2025-09-03 00:00:00 -> 2025-09-03 00:10:00]",
                row.getDataBlocks());
    }

    @Test
    public void multipleDataBlocksAreJoinedWithCommas() {
        DataBlock second = DataBlock.newBuilder()
                .addPvNames("pv-9")
                .setBeginTime(timestamp("2025-09-04T00:00:00Z"))
                .setEndTime(timestamp("2025-09-04T00:05:00Z"))
                .build();
        DatasetInfoTableRow row = new DatasetInfoTableRow(
                baseDataSet().addDataBlocks(sampleBlock()).addDataBlocks(second).build());
        assertEquals("[pv-1, pv-2: 2025-09-03 00:00:00 -> 2025-09-03 00:10:00], "
                        + "[pv-9: 2025-09-04 00:00:00 -> 2025-09-04 00:05:00]",
                row.getDataBlocks());
    }

    @Test
    public void emptyDataBlockListFormatsAsEmptyString() {
        DatasetInfoTableRow row = new DatasetInfoTableRow(baseDataSet().build());
        assertEquals("", row.getDataBlocks());
    }

    @Test
    public void dataBlockWithoutPvNamesOrTimesUsesPlaceholders() {
        DatasetInfoTableRow row = new DatasetInfoTableRow(
                baseDataSet().addDataBlocks(DataBlock.newBuilder()).build());
        assertEquals("[No PVs: No time range]", row.getDataBlocks());
    }

    @Test
    public void toStringSummarizesIdNameAndBlockCount() {
        DatasetInfoTableRow row = new DatasetInfoTableRow(
                baseDataSet().addDataBlocks(sampleBlock()).build());
        assertEquals("Dataset[id=ds-1, name=dataset-1, blocks=1]", row.toString());
    }
}
