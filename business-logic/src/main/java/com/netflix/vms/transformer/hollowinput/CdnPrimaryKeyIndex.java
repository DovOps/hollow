package com.netflix.vms.transformer.hollowinput;

import com.netflix.hollow.api.consumer.HollowConsumer;
import com.netflix.hollow.api.consumer.index.AbstractHollowUniqueKeyIndex;
import com.netflix.hollow.core.schema.HollowObjectSchema;

@SuppressWarnings("all")
public class CdnPrimaryKeyIndex extends AbstractHollowUniqueKeyIndex<VMSHollowInputAPI, CdnHollow> {

    public CdnPrimaryKeyIndex(HollowConsumer consumer) {
        this(consumer, true);    }

    public CdnPrimaryKeyIndex(HollowConsumer consumer, boolean isListenToDataRefresh) {
        this(consumer, isListenToDataRefresh, ((HollowObjectSchema)consumer.getStateEngine().getSchema("Cdn")).getPrimaryKey().getFieldPaths());
    }

    public CdnPrimaryKeyIndex(HollowConsumer consumer, String... fieldPaths) {
        this(consumer, true, fieldPaths);
    }

    public CdnPrimaryKeyIndex(HollowConsumer consumer, boolean isListenToDataRefresh, String... fieldPaths) {
        super(consumer, "Cdn", isListenToDataRefresh, fieldPaths);
    }

    public CdnHollow findMatch(Object... keys) {
        int ordinal = idx.getMatchingOrdinal(keys);
        if(ordinal == -1)
            return null;
        return api.getCdnHollow(ordinal);
    }

}