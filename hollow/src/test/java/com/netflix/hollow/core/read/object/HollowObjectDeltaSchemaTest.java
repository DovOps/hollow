package com.netflix.hollow.core.read.object;

import com.netflix.hollow.api.objects.delegate.HollowObjectGenericDelegate;
import com.netflix.hollow.api.objects.generic.GenericHollowObject;
import com.netflix.hollow.core.AbstractStateEngineTest;
import com.netflix.hollow.core.read.engine.object.HollowObjectTypeReadState;
import com.netflix.hollow.core.schema.HollowObjectSchema;
import com.netflix.hollow.core.write.HollowObjectTypeWriteState;
import com.netflix.hollow.core.write.HollowObjectWriteRecord;
import com.netflix.hollow.core.write.HollowWriteStateEngine;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class HollowObjectDeltaSchemaTest extends AbstractStateEngineTest {

    HollowObjectSchema schema;
    HollowObjectSchema schemaNew;

    @Before
    public void setUp() {
        schema = new HollowObjectSchema("TestObject", 2);
        schema.addField("f1", HollowObjectSchema.FieldType.INT);
        schema.addField("f2", HollowObjectSchema.FieldType.STRING);


        schemaNew = new HollowObjectSchema("TestObject", 3);
        schemaNew.addField("f1", HollowObjectSchema.FieldType.INT);
        schemaNew.addField("f2", HollowObjectSchema.FieldType.STRING);
        schemaNew.addField("f3", HollowObjectSchema.FieldType.LONG);

        super.setUp();
    }

    @Test
    public void test() throws IOException {
        addRecord(1, "one");
        addRecord(2, "two");
        addRecord(3, "three");

        roundTripSnapshot();

        reinitializeTypeStates();
        addRecord(1, "one", 1L);
        addRecord(3, "three", 3L);
        addRecord(10000, "ten thousand", 10000L);
        addRecord(0, "zero", 0L);

        roundTripDelta(true);

        HollowObjectTypeReadState typeState = (HollowObjectTypeReadState) readStateEngine.getTypeState("TestObject");

        Assert.assertEquals(6, typeState.maxOrdinal());

        assertObject(typeState, 0, 1, "one");   /// these were "removed", but data hangs around as a "ghost" until the following cycle.
        assertObject(typeState, 1, 2, "two");   /// these were "removed", but data hangs around as a "ghost" until the following cycle.
        assertObject(typeState, 2, 3, "three"); /// these were "removed", but data hangs around as a "ghost" until the following cycle.
        assertObject(typeState, 3, 1, "one", 1L);
        assertObject(typeState, 4, 3, "three", 3L);
        assertObject(typeState, 5, 0, "zero", 0L);
    }

    private void addRecord(int intVal, String strVal) {
        HollowObjectWriteRecord rec = new HollowObjectWriteRecord(schema);

        rec.setInt("f1", intVal);
        rec.setString("f2", strVal);

        writeStateEngine.add("TestObject", rec);
    }

    private void addRecord(int intVal, String strVal, long longVal) {
        HollowObjectWriteRecord rec = new HollowObjectWriteRecord(schemaNew);

        rec.setInt("f1", intVal);
        rec.setString("f2", strVal);
        rec.setLong("f3", longVal);

        writeStateEngine.add("TestObject", rec);
    }

    private void assertObject(HollowObjectTypeReadState readState, int ordinal, int intVal, String strVal) {
        GenericHollowObject obj = new GenericHollowObject(new HollowObjectGenericDelegate(readState), ordinal);

        Assert.assertEquals(intVal, obj.getInt("f1"));
        Assert.assertEquals(strVal, obj.getString("f2"));
    }

    private void assertObject(HollowObjectTypeReadState readState, int ordinal, int intVal, String strVal, long longVal) {
        GenericHollowObject obj = new GenericHollowObject(new HollowObjectGenericDelegate(readState), ordinal);

        Assert.assertEquals(intVal, obj.getInt("f1"));
        Assert.assertEquals(strVal, obj.getString("f2"));
        Assert.assertEquals(longVal, obj.getString("f3"));
    }

    @Override
    protected void initializeTypeStates() {
        HollowObjectTypeWriteState writeState = new HollowObjectTypeWriteState(schema);
        writeStateEngine.addTypeState(writeState);
    }

    private void reinitializeTypeStates() {
        HollowObjectTypeWriteState writeStateNew = new HollowObjectTypeWriteState(schemaNew);
        writeStateEngine = new HollowWriteStateEngine();
        writeStateEngine.addTypeState(writeStateNew);
        writeStateEngine.restoreFrom(readStateEngine);
    }
}
