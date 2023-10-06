/*
 *  Copyright 2016-2019 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.hollow.core.read.engine.object;

import com.netflix.hollow.api.sampling.DisabledSamplingDirector;
import com.netflix.hollow.api.sampling.HollowObjectSampler;
import com.netflix.hollow.api.sampling.HollowSampler;
import com.netflix.hollow.api.sampling.HollowSamplingDirector;
import com.netflix.hollow.core.memory.MemoryMode;
import com.netflix.hollow.core.memory.encoding.GapEncodedVariableLengthIntegerReader;
import com.netflix.hollow.core.memory.encoding.VarInt;
import com.netflix.hollow.core.memory.pool.ArraySegmentRecycler;
import com.netflix.hollow.core.read.HollowBlobInput;
import com.netflix.hollow.core.read.dataaccess.HollowObjectTypeDataAccess;
import com.netflix.hollow.core.read.engine.HollowReadStateEngine;
import com.netflix.hollow.core.read.engine.HollowTypeReadState;
import com.netflix.hollow.core.read.engine.SnapshotPopulatedOrdinalsReader;
import com.netflix.hollow.core.read.filter.HollowFilterConfig;
import com.netflix.hollow.core.schema.HollowObjectSchema;
import com.netflix.hollow.core.schema.HollowSchema;
import com.netflix.hollow.tools.checksum.HollowChecksum;
import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;

/**
 * A {@link HollowTypeReadState} for OBJECT type records. 
 */
public class HollowObjectTypeReadState extends HollowTypeReadState implements HollowObjectTypeDataAccess {

    private final HollowObjectSchema unfilteredSchema;
    private final HollowObjectSampler sampler;

    static class ShardsHolder {
        final HollowObjectTypeReadStateShard shards[];
        final int shardNumberMask;

        public ShardsHolder(int numShards) {
            this.shards = new HollowObjectTypeReadStateShard[numShards];
            this.shardNumberMask = numShards - 1;
        }

        public ShardsHolder(HollowSchema schema, HollowObjectTypeDataElements[] dataElements, int[] shardOrdinalShifts) {
            int numShards = dataElements.length;
            this.shardNumberMask = numShards - 1;
            this.shards = new HollowObjectTypeReadStateShard[numShards];
            for (int i=0; i<numShards; i++) {
                this.shards[i] = new HollowObjectTypeReadStateShard((HollowObjectSchema) schema, shardOrdinalShifts[i]);
                this.shards[i].setCurrentData(this, dataElements[i]);
            }
        }

        public HollowObjectTypeReadStateShard[] getShards() {  // TODO: package private
            return shards;
        }
    }

    private volatile ShardsHolder shardsVolatile;

    private int maxOrdinal;

    public HollowObjectTypeReadState(HollowReadStateEngine fileEngine, HollowObjectSchema schema) {
        this(fileEngine, MemoryMode.ON_HEAP, schema, schema, 1);
    }

    public HollowObjectTypeReadState(HollowReadStateEngine fileEngine, MemoryMode memoryMode, HollowObjectSchema schema, HollowObjectSchema unfilteredSchema, int numShards) {
        super(fileEngine, memoryMode, schema);
        this.sampler = new HollowObjectSampler(schema, DisabledSamplingDirector.INSTANCE);
        this.unfilteredSchema = unfilteredSchema;

        int shardOrdinalShift = 31 - Integer.numberOfLeadingZeros(numShards);   // numShards = 4 => shardOrdinalShift = 2. Ordinal 4 = 100, shardOrdinal = 100 >> 2 == 1 (in shard 0). Ordinal 10 = 1010, shardOrdinal = 1010 >> 2 = 2 (in shard 2)
        if(numShards < 1 || 1 << shardOrdinalShift != numShards)
            throw new IllegalArgumentException("Number of shards must be a power of 2!");

        this.shardsVolatile = new ShardsHolder(numShards);
        for(int i=0;i<numShards;i++) {
            this.shardsVolatile.shards[i] = new HollowObjectTypeReadStateShard(schema, shardOrdinalShift);
        }
    }

    @Override
    public HollowObjectSchema getSchema() {
        return (HollowObjectSchema)schema;
    }

    @Override
    public int maxOrdinal() {
        return maxOrdinal;
    }

    @Override
    public void readSnapshot(HollowBlobInput in, ArraySegmentRecycler memoryRecycler) throws IOException {
        if(shardsVolatile.shards.length > 1)
            maxOrdinal = VarInt.readVInt(in);

        for(int i = 0; i< shardsVolatile.shards.length; i++) {
            HollowObjectTypeDataElements snapshotData = new HollowObjectTypeDataElements(getSchema(), memoryMode, memoryRecycler);
            snapshotData.readSnapshot(in, unfilteredSchema);
            shardsVolatile.shards[i].setCurrentData(shardsVolatile, snapshotData);
        }

        if(shardsVolatile.shards.length == 1)
            maxOrdinal = shardsVolatile.shards[0].currentDataElements().maxOrdinal;

        SnapshotPopulatedOrdinalsReader.readOrdinals(in, stateListeners);
    }

    /**
     * Given old and new numShards, this method returns the shard resizing multiplier.
     */
    static int shardingFactor(int oldNumShards, int newNumShards) {
        if (newNumShards <= 0 || oldNumShards <= 0 || newNumShards == oldNumShards) {
            throw new IllegalStateException("Invalid shard resizing, oldNumShards=" + oldNumShards + ", newNumShards=" + newNumShards);
        }

        boolean isNewGreater = newNumShards > oldNumShards;
        int dividend = isNewGreater ? newNumShards : oldNumShards;
        int divisor = isNewGreater ? oldNumShards : newNumShards;

        if (dividend % divisor != 0) {
            throw new IllegalStateException("Invalid shard resizing, oldNumShards=" + oldNumShards + ", newNumShards=" + newNumShards);
        }
        return dividend / divisor;
    }

    // SNAP: TODO: Test shards holder serving right data at the intermediate stages of resharding
    public void reshard(int newNumShards) {   // TODO: package private
        int prevNumShards = shardsVolatile.shards.length;
        int shardingFactor = shardingFactor(prevNumShards, newNumShards);
        int newShardOrdinalShift = 31 - Integer.numberOfLeadingZeros(newNumShards);
        HollowObjectTypeDataElements[] newDataElements;
        int[] shardOrdinalShifts;

        if (newNumShards > prevNumShards) { // split existing shards
            // Step 1:  Grow the number of shards. Each original shard will result in N child shards where N is the sharding factor.
            //          The child shards will reference into the existing data elements as-is, and reuse existing shardOrdinalShift.
            //          However since the shards array is resized, a read will map into the new shard index, as a result a subset of
            //          ordinals in each shard will be accessed. In the next "splitting" step, the data elements in these new shards
            //          will be filtered to only retain the subset of ordinals that are actually accessed.
            newDataElements = new HollowObjectTypeDataElements[newNumShards];
            shardOrdinalShifts = new int[newNumShards];
            for(int i = 0; i< prevNumShards; i++) {
                for (int j = 0; j < shardingFactor; j ++) {
                    newDataElements[i+(prevNumShards*j)] = shardsVolatile.shards[i].currentDataElements();
                    shardOrdinalShifts[i+(prevNumShards*j)] = 31 - Integer.numberOfLeadingZeros(prevNumShards);
                }
            }
            // atomic update to shardsVolatile: full construction happens-before the store to shardsVolatile, in other
            // words a fully constructed object as visible to this thread will be visible to other threads that load the
            // new shardsVolatile.
            shardsVolatile = new ShardsHolder(schema, newDataElements, shardOrdinalShifts);

            // shardsVolatile = expandWithExistingDataElements(shardsVolatile, prevNumShards, shardingFactor);


            // Step 2: Split each original data element into N child data elements where N is the sharding factor.
            //         Then update each of the N child shards with the respective split of data element, this will be
            //         sufficient to serve all reads into this shard. Once all child shards for a pre-split parent
            //         shard have been assigned the split data elements, the parent data elements can be discarded.
            for(int i = 0; i< prevNumShards; i++) {
                HollowObjectTypeDataElements dataElementsToSplit = shardsVolatile.shards[i].currentDataElements();

                HollowObjectTypeDataElementsSplitter splitter = new HollowObjectTypeDataElementsSplitter();
                HollowObjectTypeDataElements[] splits = splitter.split(dataElementsToSplit, shardingFactor);

                for (int j = 0; j < shardingFactor; j ++) {
                    newDataElements[i + (prevNumShards*j)] = splits[j];
                    shardOrdinalShifts[i + (prevNumShards*j)] = newShardOrdinalShift;
                }

                shardsVolatile = new ShardsHolder(schema, newDataElements, shardOrdinalShifts); // atomic update to shards

                dataElementsToSplit.destroy(); // it is now safe to destroy pre-split data elements
                if (dataElementsToSplit.encodedRemovals != null) {
                    dataElementsToSplit.encodedRemovals.destroy();
                }
            }
            // Re-sharding done.
            // shardsVolatile now contains newNumShards shards where each shard contains
            // a split of original data elements.

        } else { // join existing shards
            // Step 1: Join N data elements to create one, where N is the sharding factor. Then update each of the
            //         N shards to reference the joined result, but with a new shardOrdinalShift.
            //         Reads will continue to reference the same shard index as before, but the new shardOrdinalShift
            //         will help these reads land at the right ordinal in the joined shard. When all N old shards
            //         corresponding to one new shard have been updated, the N pre-join data elements can be destroyed.
            newDataElements = new HollowObjectTypeDataElements[prevNumShards];
            shardOrdinalShifts = new int[prevNumShards];
            for (int i = 0; i < prevNumShards; i++) {
                newDataElements[i] = shardsVolatile.shards[i].currentDataElements();
                shardOrdinalShifts[i] = shardsVolatile.shards[i].shardOrdinalShift;
            }
            for (int i = 0; i < newNumShards; i++) {
                HollowObjectTypeDataElements[] dataElementsToJoin = new HollowObjectTypeDataElements[shardingFactor];
                for (int j = 0; j < shardingFactor; j ++) {
                    dataElementsToJoin[j] = shardsVolatile.shards[i + (newNumShards*j)].currentDataElements();
                };

                HollowObjectTypeDataElementsJoiner joiner = new HollowObjectTypeDataElementsJoiner();
                HollowObjectTypeDataElements joined = joiner.join(dataElementsToJoin);

                for (int j = 0; j < shardingFactor; j ++) {
                    newDataElements[i + (newNumShards*j)] = joined;
                    shardOrdinalShifts[i + (newNumShards*j)] = newShardOrdinalShift;
                };
                shardsVolatile = new ShardsHolder(schema, newDataElements, shardOrdinalShifts); // atomic update to shards

                for (int j = 0; j < shardingFactor; j ++) {
                    dataElementsToJoin[j].destroy();   // now safe to destroy
                    if (dataElementsToJoin[j].encodedRemovals != null) {
                        dataElementsToJoin[j].encodedRemovals.destroy();
                    }
                };
            }

            // Step 2: Resize the shards array to only keep the first newNumShards shards.
            shardsVolatile = new ShardsHolder(schema,
                    Arrays.copyOfRange(newDataElements, 0, newNumShards),
                    Arrays.copyOfRange(shardOrdinalShifts, 0, newNumShards));
            // Re-sharding done.
            // shardsVolatile now contains newNumShards shards where each shard contains
            // a join of original data elements.
        }
    }

    // ShardsHolder expandWithExistingDataElements(ShardsHolder shardsHolder, int prevNumShards, int shardingFactor) {
// 
    // }

    @Override
    public void applyDelta(HollowBlobInput in, HollowSchema deltaSchema, ArraySegmentRecycler memoryRecycler, int deltaNumShards) throws IOException {
        if (shouldReshard(shardsVolatile.shards.length, deltaNumShards)) {
            reshard(deltaNumShards);
        }
        if(shardsVolatile.shards.length > 1)
            maxOrdinal = VarInt.readVInt(in);

        for(int i = 0; i< shardsVolatile.shards.length; i++) {
            HollowObjectTypeDataElements deltaData = new HollowObjectTypeDataElements((HollowObjectSchema)deltaSchema, memoryMode, memoryRecycler);
            deltaData.readDelta(in);
            if(stateEngine.isSkipTypeShardUpdateWithNoAdditions() && deltaData.encodedAdditions.isEmpty()) {

                if(!deltaData.encodedRemovals.isEmpty())
                    notifyListenerAboutDeltaChanges(deltaData.encodedRemovals, deltaData.encodedAdditions, i, shardsVolatile.shards.length);

                HollowObjectTypeDataElements currentData = shardsVolatile.shards[i].currentDataElements();
                GapEncodedVariableLengthIntegerReader oldRemovals = currentData.encodedRemovals == null ? GapEncodedVariableLengthIntegerReader.EMPTY_READER : currentData.encodedRemovals;
                if(oldRemovals.isEmpty()) {
                    currentData.encodedRemovals = deltaData.encodedRemovals;
                    oldRemovals.destroy();
                } else {
                    if(!deltaData.encodedRemovals.isEmpty()) {
                        currentData.encodedRemovals = GapEncodedVariableLengthIntegerReader.combine(oldRemovals, deltaData.encodedRemovals, memoryRecycler);
                        oldRemovals.destroy();
                    }
                    deltaData.encodedRemovals.destroy();
                }

                deltaData.encodedAdditions.destroy();
            } else {
                HollowObjectTypeDataElements nextData = new HollowObjectTypeDataElements(getSchema(), memoryMode, memoryRecycler);
                HollowObjectTypeDataElements oldData = shardsVolatile.shards[i].currentDataElements();
                nextData.applyDelta(oldData, deltaData);
                shardsVolatile.shards[i].setCurrentData(shardsVolatile, nextData);
                notifyListenerAboutDeltaChanges(deltaData.encodedRemovals, deltaData.encodedAdditions, i, shardsVolatile.shards.length);
                deltaData.encodedAdditions.destroy();
                oldData.destroy();
            }
            deltaData.destroy();
            stateEngine.getMemoryRecycler().swap();
        }

        if(shardsVolatile.shards.length == 1)
            maxOrdinal = shardsVolatile.shards[0].currentDataElements().maxOrdinal;
    }

    public static void discardSnapshot(HollowBlobInput in, HollowObjectSchema schema, int numShards) throws IOException {
        discardType(in, schema, numShards, false);
    }

    public static void discardDelta(HollowBlobInput in, HollowObjectSchema schema, int numShards) throws IOException {
        discardType(in, schema, numShards, true);
    }

    public static void discardType(HollowBlobInput in, HollowObjectSchema schema, int numShards, boolean delta) throws IOException {
        HollowObjectTypeDataElements.discardFromInput(in, schema, numShards, delta);
        if(!delta)
            SnapshotPopulatedOrdinalsReader.discardOrdinals(in);
    }

    @Override
    public boolean isNull(int ordinal, int fieldIndex) {
        sampler.recordFieldAccess(fieldIndex);
        HollowObjectTypeReadStateShard shard = shardsVolatile.shards[ordinal & shardsVolatile.shardNumberMask];
        return shard.isNull(ordinal >> shard.shardOrdinalShift, fieldIndex);
    }

    @Override
    public int readOrdinal(int ordinal, int fieldIndex) {
        sampler.recordFieldAccess(fieldIndex);
        HollowObjectTypeReadStateShard shard = shardsVolatile.shards[ordinal & shardsVolatile.shardNumberMask];
        return shard.readOrdinal(ordinal >> shard.shardOrdinalShift, fieldIndex);
    }

    @Override
    public int readInt(int ordinal, int fieldIndex) {
        sampler.recordFieldAccess(fieldIndex);
        HollowObjectTypeReadStateShard shard = shardsVolatile.shards[ordinal & shardsVolatile.shardNumberMask];
        return shard.readInt(ordinal >> shard.shardOrdinalShift, fieldIndex);
    }

    @Override
    public float readFloat(int ordinal, int fieldIndex) {
        sampler.recordFieldAccess(fieldIndex);
        HollowObjectTypeReadStateShard shard = shardsVolatile.shards[ordinal & shardsVolatile.shardNumberMask];
        return shard.readFloat(ordinal >> shard.shardOrdinalShift, fieldIndex);
    }

    @Override
    public double readDouble(int ordinal, int fieldIndex) {
        sampler.recordFieldAccess(fieldIndex);
        HollowObjectTypeReadStateShard shard = shardsVolatile.shards[ordinal & shardsVolatile.shardNumberMask];
        return shard.readDouble(ordinal >> shard.shardOrdinalShift, fieldIndex);
    }

    @Override
    public long readLong(int ordinal, int fieldIndex) {
        sampler.recordFieldAccess(fieldIndex);
        HollowObjectTypeReadStateShard shard = shardsVolatile.shards[ordinal & shardsVolatile.shardNumberMask];
        return shard.readLong(ordinal >> shard.shardOrdinalShift, fieldIndex);
    }

    @Override
    public Boolean readBoolean(int ordinal, int fieldIndex) {
        sampler.recordFieldAccess(fieldIndex);
        HollowObjectTypeReadStateShard shard = shardsVolatile.shards[ordinal & shardsVolatile.shardNumberMask];
        return shard.readBoolean(ordinal >> shard.shardOrdinalShift, fieldIndex);
    }

    @Override
    public byte[] readBytes(int ordinal, int fieldIndex) {
        sampler.recordFieldAccess(fieldIndex);
        HollowObjectTypeReadStateShard shard = shardsVolatile.shards[ordinal & shardsVolatile.shardNumberMask];
        return shard.readBytes(ordinal >> shard.shardOrdinalShift, fieldIndex);
    }

    @Override
    public String readString(int ordinal, int fieldIndex) {
        sampler.recordFieldAccess(fieldIndex);
        HollowObjectTypeReadStateShard shard = shardsVolatile.shards[ordinal & shardsVolatile.shardNumberMask];
        return shard.readString(ordinal >> shard.shardOrdinalShift, fieldIndex);
    }

    @Override
    public boolean isStringFieldEqual(int ordinal, int fieldIndex, String testValue) {
        sampler.recordFieldAccess(fieldIndex);
        HollowObjectTypeReadStateShard shard = shardsVolatile.shards[ordinal & shardsVolatile.shardNumberMask];
        return shard.isStringFieldEqual(ordinal >> shard.shardOrdinalShift, fieldIndex, testValue);
    }

    @Override
    public int findVarLengthFieldHashCode(int ordinal, int fieldIndex) {
        sampler.recordFieldAccess(fieldIndex);
        HollowObjectTypeReadStateShard shard = shardsVolatile.shards[ordinal & shardsVolatile.shardNumberMask];
        return shard.findVarLengthFieldHashCode(ordinal >> shard.shardOrdinalShift, fieldIndex);
    }

    /**
     * Warning:  Not thread-safe.  Should only be called within the update thread.
     * @param fieldName the field name
     * @return the number of bits required for the field
     */
    public int bitsRequiredForField(String fieldName) {
        HollowObjectTypeReadStateShard[] shards = this.shardsVolatile.shards;
        int maxBitsRequiredForField = shards[0].bitsRequiredForField(fieldName);
        
        for(int i=1;i<shards.length;i++) {
            int shardRequiredBits = shards[i].bitsRequiredForField(fieldName);
            if(shardRequiredBits > maxBitsRequiredForField)
                maxBitsRequiredForField = shardRequiredBits;
        }
        
        return maxBitsRequiredForField;
    }
    
    @Override
    public HollowSampler getSampler() {
        return sampler;
    }

    @Override
    protected void invalidate() {
        HollowObjectTypeReadStateShard[] shards = this.shardsVolatile.shards;
        stateListeners = EMPTY_LISTENERS;
        for(int i=0;i<shards.length;i++)
            shards[i].invalidate();
    }

    @Override
    public void setSamplingDirector(HollowSamplingDirector director) {
        sampler.setSamplingDirector(director);
    }

    @Override
    public void setFieldSpecificSamplingDirector(HollowFilterConfig fieldSpec, HollowSamplingDirector director) {
        sampler.setFieldSpecificSamplingDirector(fieldSpec, director);
    }

    @Override
    public void ignoreUpdateThreadForSampling(Thread t) {
        sampler.setUpdateThread(t);
    }
    
    HollowObjectTypeDataElements[] currentDataElements() {
        HollowObjectTypeReadStateShard[] shards = this.shardsVolatile.shards;
        HollowObjectTypeDataElements currentDataElements[] = new HollowObjectTypeDataElements[shards.length];
        
        for(int i=0;i<shards.length;i++)
            currentDataElements[i] = shards[i].currentDataElements();
        
        return currentDataElements;
    }

    @Override
    protected void applyToChecksum(HollowChecksum checksum, HollowSchema withSchema) {
        HollowObjectTypeReadStateShard[] shards = this.shardsVolatile.shards;
        if(!(withSchema instanceof HollowObjectSchema))
            throw new IllegalArgumentException("HollowObjectTypeReadState can only calculate checksum with a HollowObjectSchema: " + getSchema().getName());

        BitSet populatedOrdinals = getPopulatedOrdinals();
        
        for(int i=0;i<shards.length;i++) {
            // if (shards[i].shardOrdinalShift != 0) {       // SNAP: TODO: detect virtual shard
            if (false) {
                throw new UnsupportedOperationException("applyToChecksum called for virtual shard, unexpected");  // SNAP: TODO: remove this altogether, or support applyToChecksum for virtual shards
            }
            shards[i].applyToChecksum(checksum, withSchema, populatedOrdinals, i, shards.length);
        }
    }

	@Override
	public long getApproximateHeapFootprintInBytes() {
        HollowObjectTypeReadStateShard[] shards = this.shardsVolatile.shards;
	    long totalApproximateHeapFootprintInBytes = 0;
	    
	    for(int i=0;i<shards.length;i++) {
            totalApproximateHeapFootprintInBytes += shards[i].getApproximateHeapFootprintInBytes();
        }
	    
	    return totalApproximateHeapFootprintInBytes;
	}
	
	@Override
	public long getApproximateHoleCostInBytes() {
        HollowObjectTypeReadStateShard[] shards = this.shardsVolatile.shards;
	    long totalApproximateHoleCostInBytes = 0;
	    
	    BitSet populatedOrdinals = getPopulatedOrdinals();

	    for(int i=0;i<shards.length;i++)
	        totalApproximateHoleCostInBytes += shards[i].getApproximateHoleCostInBytes(populatedOrdinals, i, shards.length);
        
	    return totalApproximateHoleCostInBytes;
	}
	
	void setCurrentData(HollowObjectTypeDataElements data) {
        HollowObjectTypeReadStateShard[] shards = this.shardsVolatile.shards;
	    if(shards.length > 1)
	        throw new UnsupportedOperationException("Cannot directly set data on sharded type state");
	    shards[0].setCurrentData(this.shardsVolatile, data);
	    maxOrdinal = data.maxOrdinal;
	}

    @Override
    public int numShards() {
        HollowObjectTypeReadStateShard[] shards = this.shardsVolatile.shards;
        return shards.length;
    }
	
}
