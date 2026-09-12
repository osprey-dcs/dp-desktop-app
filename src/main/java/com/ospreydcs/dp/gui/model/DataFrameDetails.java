package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.grpc.v1.common.DataColumn;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import java.util.List;
import java.util.Objects;

/**
 * Model class representing a data frame with calculation results.
 * Contains a name, list of timestamps, and list of data columns.
 */
public class DataFrameDetails {
    
    private String name;
    private List<Timestamp> timestamps;
    private List<DataColumn> dataColumns;
    
    public DataFrameDetails() {
    }
    
    public DataFrameDetails(String name, List<Timestamp> timestamps, List<DataColumn> dataColumns) {
        this.name = name;
        this.timestamps = timestamps;
        this.dataColumns = dataColumns;
    }
    
    /**
     * Converts a protobuf CalculationsDataFrame to a DataFrameDetails.
     *
     * As of dp-grpc #132 a CalculationsDataFrame is a name plus a nested common.DataFrame rather
     * than a flat name/DataTimestamps/DataColumns triple.  DataFrameDetails stays flat because its
     * other construction path is the Excel import (DataImportResult.DataFrameResult), which the
     * modernization did not change; this factory is the single place that unwraps the nesting, so
     * the read sites -- AnnotationBuilderViewModel.loadFromAnnotation() and
     * AnnotationExploreController.openFrameDialog() -- do not each repeat it.
     */
    public static DataFrameDetails fromCalculationsDataFrame(
            com.ospreydcs.dp.grpc.v1.annotation.Calculations.CalculationsDataFrame frame) {

        if (frame == null) {
            return null;
        }

        return new DataFrameDetails(
                frame.getName(),
                frame.getFrame().getDataTimestamps().getTimestampList().getTimestampsList(),
                frame.getFrame().getDataColumnsList());
    }

    // Getters and Setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public List<Timestamp> getTimestamps() {
        return timestamps;
    }
    
    public void setTimestamps(List<Timestamp> timestamps) {
        this.timestamps = timestamps;
    }
    
    public List<DataColumn> getDataColumns() {
        return dataColumns;
    }
    
    public void setDataColumns(List<DataColumn> dataColumns) {
        this.dataColumns = dataColumns;
    }
    
    /**
     * Returns a formatted display string for this data frame.
     * Format: "Frame name - Column1, Column2, Column3..."
     * Truncates column list if too long.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        // Frame name
        if (name != null && !name.trim().isEmpty()) {
            sb.append(name);
        } else {
            sb.append("Unnamed Frame");
        }
        
        sb.append(" - ");
        
        // Column names (abbreviated)
        if (dataColumns != null && !dataColumns.isEmpty()) {
            int maxColumns = 3; // Show max 3 column names
            for (int i = 0; i < Math.min(dataColumns.size(), maxColumns); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                DataColumn column = dataColumns.get(i);
                String columnName = column.getName();
                if (columnName != null && !columnName.trim().isEmpty()) {
                    sb.append(columnName);
                } else {
                    sb.append("Column").append(i + 1);
                }
            }
            
            if (dataColumns.size() > maxColumns) {
                sb.append("... (").append(dataColumns.size()).append(" columns)");
            }
        } else {
            sb.append("No columns");
        }
        
        return sb.toString();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataFrameDetails that = (DataFrameDetails) o;
        return Objects.equals(name, that.name) &&
               Objects.equals(timestamps, that.timestamps) &&
               Objects.equals(dataColumns, that.dataColumns);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, timestamps, dataColumns);
    }
}