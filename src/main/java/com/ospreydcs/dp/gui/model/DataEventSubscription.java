package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.client.IngestionStreamClient;

public class DataEventSubscription {

    public final SubscribeDataEventDetail subscriptionDetail;
    public final IngestionStreamClient.SubscribeDataEventCall subscribeDataEventCall;

    public DataEventSubscription(
            SubscribeDataEventDetail subscriptionDetail,
            IngestionStreamClient.SubscribeDataEventCall subscribeDataEventCall
    ) {
        this.subscriptionDetail = subscriptionDetail;
        this.subscribeDataEventCall = subscribeDataEventCall;
    }

}
