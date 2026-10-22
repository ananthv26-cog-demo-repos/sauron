package com.freenow.sauron.model;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DataSetTest
{
    @Test
    public void builderSetsEveryField()
    {
        Date eventTime = new Date(1577836800123L);
        DataSet dataSet = DataSet.builder()
            .serviceName("svc")
            .repositoryUrl("https://github.com/org/svc")
            .commitId("c1")
            .buildId("b1")
            .owner("team")
            .eventTime(eventTime)
            .build();

        assertEquals("svc", dataSet.getServiceName());
        assertEquals("https://github.com/org/svc", dataSet.getRepositoryUrl());
        assertEquals("c1", dataSet.getCommitId());
        assertEquals("b1", dataSet.getBuildId());
        assertEquals("team", dataSet.getOwner());
        assertSame(eventTime, dataSet.getEventTime());
    }


    @Test
    public void settersOverwriteFields()
    {
        DataSet dataSet = new DataSet();
        dataSet.setServiceName("svc");
        dataSet.setRepositoryUrl("url");
        dataSet.setCommitId("c1");
        dataSet.setBuildId("b1");
        dataSet.setOwner("team");
        dataSet.setEventTime(new Date(0));

        assertEquals("svc", dataSet.getServiceName());
        assertEquals("url", dataSet.getRepositoryUrl());
        assertEquals("c1", dataSet.getCommitId());
        assertEquals("b1", dataSet.getBuildId());
        assertEquals("team", dataSet.getOwner());
        assertEquals(new Date(0), dataSet.getEventTime());
    }


    @Test
    public void builderAndNoArgsConstructorStartWithEmptyMutableAdditionalInformation()
    {
        DataSet fromBuilder = DataSet.builder().build();
        DataSet fromConstructor = new DataSet();

        assertTrue(fromBuilder.copyAdditionalInformation().isEmpty());
        assertTrue(fromConstructor.copyAdditionalInformation().isEmpty());

        fromBuilder.setAdditionalInformation("k", "v");
        fromConstructor.setAdditionalInformation("k", "v");
        assertEquals(Optional.of("v"), fromBuilder.getStringAdditionalInformation("k"));
        assertEquals(Optional.of("v"), fromConstructor.getStringAdditionalInformation("k"));
    }


    @Test
    public void eachBuiltInstanceGetsItsOwnAdditionalInformationMap()
    {
        DataSet first = DataSet.builder().build();
        DataSet second = DataSet.builder().build();

        first.setAdditionalInformation("k", "v");

        assertTrue(second.copyAdditionalInformation().isEmpty());
    }


    @Test
    public void setAdditionalInformationReturnsSameInstanceForChaining()
    {
        DataSet dataSet = new DataSet();

        DataSet returned = dataSet.setAdditionalInformation("a", 1).setAdditionalInformation("b", 2);

        assertSame(dataSet, returned);
        Map<String, Object> expected = new HashMap<>();
        expected.put("a", 1);
        expected.put("b", 2);
        assertEquals(expected, dataSet.copyAdditionalInformation());
    }


    @Test
    public void setAdditionalInformationOverwritesExistingKeyAndAcceptsNull()
    {
        DataSet dataSet = new DataSet();
        dataSet.setAdditionalInformation("k", "first");
        dataSet.setAdditionalInformation("k", "second");
        dataSet.setAdditionalInformation("nothing", null);

        assertEquals(Optional.of("second"), dataSet.getStringAdditionalInformation("k"));
        assertTrue(dataSet.copyAdditionalInformation().containsKey("nothing"));
        assertEquals(Optional.empty(), dataSet.getObjectAdditionalInformation("nothing"));
    }


    @Test
    public void typedGettersReturnValueWhenTypeMatchesExactly()
    {
        Object list = java.util.Arrays.asList(1, 2);
        DataSet dataSet = new DataSet()
            .setAdditionalInformation("string", "s")
            .setAdditionalInformation("long", 5L)
            .setAdditionalInformation("integer", 7)
            .setAdditionalInformation("double", 1.5d)
            .setAdditionalInformation("boolean", true)
            .setAdditionalInformation("list", list);

        assertEquals(Optional.of("s"), dataSet.getStringAdditionalInformation("string"));
        assertEquals(Optional.of(5L), dataSet.getLongAdditionalInformation("long"));
        assertEquals(Optional.of(7), dataSet.getIntegerAdditionalInformation("integer"));
        assertEquals(Optional.of(1.5d), dataSet.getDoubleAdditionalInformation("double"));
        assertEquals(Optional.of(true), dataSet.getBooleanAdditionalInformation("boolean"));
        assertSame(list, dataSet.getObjectAdditionalInformation("list").get());
        assertEquals(Optional.of("s"), dataSet.getObjectAdditionalInformation("string"));
    }


    @Test
    public void typedGettersReturnEmptyForMissingKey()
    {
        DataSet dataSet = new DataSet();

        assertEquals(Optional.empty(), dataSet.getStringAdditionalInformation("missing"));
        assertEquals(Optional.empty(), dataSet.getLongAdditionalInformation("missing"));
        assertEquals(Optional.empty(), dataSet.getIntegerAdditionalInformation("missing"));
        assertEquals(Optional.empty(), dataSet.getDoubleAdditionalInformation("missing"));
        assertEquals(Optional.empty(), dataSet.getBooleanAdditionalInformation("missing"));
        assertEquals(Optional.empty(), dataSet.getObjectAdditionalInformation("missing"));
    }


    @Test
    public void typedGettersDoNotConvertBetweenTypes_currentBehavior()
    {
        // CHARACTERIZATION: no numeric widening or parsing; an Integer asked for as Long is "absent", "true" is not a Boolean.
        DataSet dataSet = new DataSet()
            .setAdditionalInformation("int", 1)
            .setAdditionalInformation("float", 1.5f)
            .setAdditionalInformation("text", "true")
            .setAdditionalInformation("long", 5L);

        assertEquals(Optional.empty(), dataSet.getLongAdditionalInformation("int"));
        assertEquals(Optional.empty(), dataSet.getStringAdditionalInformation("int"));
        assertEquals(Optional.empty(), dataSet.getDoubleAdditionalInformation("int"));
        assertEquals(Optional.empty(), dataSet.getDoubleAdditionalInformation("float"));
        assertEquals(Optional.empty(), dataSet.getBooleanAdditionalInformation("text"));
        assertEquals(Optional.empty(), dataSet.getIntegerAdditionalInformation("long"));
    }


    @Test
    public void copyAdditionalInformationIsADetachedSnapshot()
    {
        DataSet dataSet = new DataSet().setAdditionalInformation("a", 1);

        Map<String, Object> copy = dataSet.copyAdditionalInformation();
        copy.put("b", 2);
        dataSet.setAdditionalInformation("c", 3);

        assertEquals(Optional.empty(), dataSet.getObjectAdditionalInformation("b"));
        assertFalse(copy.containsKey("c"));
        assertEquals(2, copy.size());
    }


    @Test
    public void builderKeepsCallersMapByReference_currentBehavior()
    {
        // CHARACTERIZATION: the builder does not copy the map, so later writes to the DataSet leak into the caller's map and vice versa.
        Map<String, Object> callerMap = new HashMap<>();
        DataSet dataSet = DataSet.builder().additionalInformation(callerMap).build();

        dataSet.setAdditionalInformation("fromDataSet", 1);
        callerMap.put("fromCaller", 2);

        assertEquals(1, callerMap.get("fromDataSet"));
        assertEquals(Optional.of(2), dataSet.getIntegerAdditionalInformation("fromCaller"));
    }


    @Test
    public void lombokMapSetterReplacesAdditionalInformationByReference()
    {
        Map<String, Object> replacement = new HashMap<>();
        replacement.put("x", "y");
        DataSet dataSet = new DataSet().setAdditionalInformation("old", 1);

        dataSet.setAdditionalInformation(replacement);
        dataSet.setAdditionalInformation("z", 2);

        assertEquals(Optional.empty(), dataSet.getObjectAdditionalInformation("old"));
        assertEquals(Optional.of("y"), dataSet.getStringAdditionalInformation("x"));
        assertEquals(2, replacement.get("z"));
    }


    @Test
    public void nullAdditionalInformationMakesAccessorsThrow_currentBehavior()
    {
        // CHARACTERIZATION: the all-args constructor and setAdditionalInformation(Map) accept null, after which every map accessor throws NPE.
        DataSet dataSet = new DataSet("svc", null, null, null, null, null, null);

        assertThrowsNpe(() -> dataSet.getStringAdditionalInformation("k"));
        assertThrowsNpe(() -> dataSet.getObjectAdditionalInformation("k"));
        assertThrowsNpe(dataSet::copyAdditionalInformation);
        assertThrowsNpe(() -> dataSet.setAdditionalInformation("k", "v"));

        DataSet viaSetter = new DataSet();
        viaSetter.setAdditionalInformation((Map<String, Object>) null);
        assertThrowsNpe(() -> viaSetter.getLongAdditionalInformation("k"));
    }


    @Test
    public void allArgsConstructorUsesDeclaredFieldOrder()
    {
        Map<String, Object> info = new HashMap<>();
        info.put("k", "v");
        Date eventTime = new Date(42L);

        DataSet dataSet = new DataSet("svc", "url", "c1", "b1", "team", eventTime, info);

        assertEquals("svc", dataSet.getServiceName());
        assertEquals("url", dataSet.getRepositoryUrl());
        assertEquals("c1", dataSet.getCommitId());
        assertEquals("b1", dataSet.getBuildId());
        assertEquals("team", dataSet.getOwner());
        assertSame(eventTime, dataSet.getEventTime());
        assertEquals(Optional.of("v"), dataSet.getStringAdditionalInformation("k"));
    }


    @Test
    public void equalityIncludesFieldsAndAdditionalInformation()
    {
        DataSet a = DataSet.builder().serviceName("svc").commitId("c1").build().setAdditionalInformation("k", 1);
        DataSet b = DataSet.builder().serviceName("svc").commitId("c1").build().setAdditionalInformation("k", 1);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(new DataSet(), DataSet.builder().build());

        b.setAdditionalInformation("k", 1L);
        assertNotEquals(a, b);
        assertNotEquals(a, DataSet.builder().serviceName("other").commitId("c1").build().setAdditionalInformation("k", 1));
    }


    private static void assertThrowsNpe(Runnable action)
    {
        try
        {
            action.run();
            fail("expected NullPointerException");
        }
        catch (NullPointerException expected)
        {
            assertNotNull(expected);
        }
    }
}
