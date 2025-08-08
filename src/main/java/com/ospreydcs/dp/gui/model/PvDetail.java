package com.ospreydcs.dp.gui.model;

import javafx.beans.property.*;

public class PvDetail {

    private final StringProperty pvName = new SimpleStringProperty();
    private final StringProperty dataType = new SimpleStringProperty();
    private final IntegerProperty valuesPerSecond = new SimpleIntegerProperty();
    private final StringProperty initialValue = new SimpleStringProperty();
    private final StringProperty maxStepMagnitude = new SimpleStringProperty();

    public PvDetail() {
        // Default constructor
    }

    public PvDetail(String pvName, String dataType, int valuesPerSecond, String initialValue, String maxStepMagnitude) {
        this.pvName.set(pvName);
        this.dataType.set(dataType);
        this.valuesPerSecond.set(valuesPerSecond);
        this.initialValue.set(initialValue);
        this.maxStepMagnitude.set(maxStepMagnitude);
    }

    // Property getters for binding
    public StringProperty pvNameProperty() {
        return pvName;
    }

    public StringProperty dataTypeProperty() {
        return dataType;
    }

    public IntegerProperty valuesPerSecondProperty() {
        return valuesPerSecond;
    }

    public StringProperty initialValueProperty() {
        return initialValue;
    }

    public StringProperty maxStepMagnitudeProperty() {
        return maxStepMagnitude;
    }

    // Value getters
    public String getPvName() {
        return pvName.get();
    }

    public String getDataType() {
        return dataType.get();
    }

    public int getValuesPerSecond() {
        return valuesPerSecond.get();
    }

    public String getInitialValue() {
        return initialValue.get();
    }

    public String getMaxStepMagnitude() {
        return maxStepMagnitude.get();
    }

    // Value setters
    public void setPvName(String pvName) {
        this.pvName.set(pvName);
    }

    public void setDataType(String dataType) {
        this.dataType.set(dataType);
    }

    public void setValuesPerSecond(int valuesPerSecond) {
        this.valuesPerSecond.set(valuesPerSecond);
    }

    public void setInitialValue(String initialValue) {
        this.initialValue.set(initialValue);
    }

    public void setMaxStepMagnitude(String maxStepMagnitude) {
        this.maxStepMagnitude.set(maxStepMagnitude);
    }

    @Override
    public String toString() {
        return String.format("%s (%s) - Values/sec: %d, Initial: %s, Max Step: %s",
                getPvName(), getDataType(), getValuesPerSecond(), getInitialValue(), getMaxStepMagnitude());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PvDetail pvDetail = (PvDetail) obj;
        return getPvName() != null && getPvName().equals(pvDetail.getPvName());
    }

    @Override
    public int hashCode() {
        return getPvName() != null ? getPvName().hashCode() : 0;
    }
}