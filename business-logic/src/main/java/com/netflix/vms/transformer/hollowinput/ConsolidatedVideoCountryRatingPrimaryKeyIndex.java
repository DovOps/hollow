package com.netflix.vms.transformer.hollowinput;

import com.netflix.hollow.api.consumer.HollowConsumer;
import com.netflix.hollow.api.consumer.index.AbstractHollowUniqueKeyIndex;
import com.netflix.hollow.core.schema.HollowObjectSchema;

@SuppressWarnings("all")
public class ConsolidatedVideoCountryRatingPrimaryKeyIndex extends AbstractHollowUniqueKeyIndex<VMSHollowInputAPI, ConsolidatedVideoCountryRatingHollow> {

    public ConsolidatedVideoCountryRatingPrimaryKeyIndex(HollowConsumer consumer) {
        this(consumer, true);    }

    public ConsolidatedVideoCountryRatingPrimaryKeyIndex(HollowConsumer consumer, boolean isListenToDataRefresh) {
        this(consumer, isListenToDataRefresh, ((HollowObjectSchema)consumer.getStateEngine().getSchema("ConsolidatedVideoCountryRating")).getPrimaryKey().getFieldPaths());
    }

    public ConsolidatedVideoCountryRatingPrimaryKeyIndex(HollowConsumer consumer, String... fieldPaths) {
        this(consumer, true, fieldPaths);
    }

    public ConsolidatedVideoCountryRatingPrimaryKeyIndex(HollowConsumer consumer, boolean isListenToDataRefresh, String... fieldPaths) {
        super(consumer, "ConsolidatedVideoCountryRating", isListenToDataRefresh, fieldPaths);
    }

    public ConsolidatedVideoCountryRatingHollow findMatch(Object... keys) {
        int ordinal = idx.getMatchingOrdinal(keys);
        if(ordinal == -1)
            return null;
        return api.getConsolidatedVideoCountryRatingHollow(ordinal);
    }

}