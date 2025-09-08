package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.gui.DpApplication;

public class SubscribeDataEventDetail {

    public final String pvName;
    public final DpApplication.TriggerCondition triggerCondition;
    public final String triggerValue;

    public SubscribeDataEventDetail(
            String pvName,
            DpApplication.TriggerCondition triggerCondition,
            String triggerValue
    ) {
        this.pvName = pvName;
        this.triggerCondition = triggerCondition;
        this.triggerValue = triggerValue;
    }
}
