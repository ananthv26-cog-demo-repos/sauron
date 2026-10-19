package com.freenow.sauron.model;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DataSetTest
{
    @Test
    public void builderSetsAllFixedFields()
    {
        Date eventTime = new Date(1600000000123L);
        DataSet dataSet = DataSet.builder()
            .serviceName("svc")
            .repositoryUrl("git@github.com:org/svc.git")
            .commitId("abc123")
            .buildId("42")
            .owner("team")
            .eventTime(eventTime)
            .build();

        assertEquals("svc", dataSet.getServiceName());
        assertEquals("git@github.com:org/svc.git", dataSet.getRepositoryUrl());
        assertEquals("abc123", dataSet.getCommitId());
        assertEquals("42", dataSet.getBuildId());
        assertEquals("team", dataSet.getOwner());
        assertSame(eventTime, dataSet.getEventTime());
    }


    @Test
    public void builderAndNoArgsConstructorStartWithEmptyMutableAdditionalInformation()
    {
        DataSet built = DataSet.builder().build();
        DataSet constructed = new DataSet();

        assertTrue(built.copyAdditionalInformation().isEmpty());
        assertTrue(constructed.copyAdditionalInformation().isEmpty());

        built.setAdditionalInformation("k", "v");
        constructed.setAdditionalInformation("k", "v");
        assertEquals(Optional.of("v"), built.getStringAdditionalInformation("k"));
        assertEquals(Optional.of("v"), constructed.getStringAdditionalInformation("k"));
    }


    @Test
    public void builderInstancesDoNotShareTheDefaultMap()
    {
        DataSet first = DataSet.builder().build();
        DataSet second = DataSet.builder().build();

        first.setAdditionalInformation("k", "v");

        assertEquals(Optional.empty(), second.getStringAdditionalInformation("k"));
    }


    @Test
    public void setAdditionalInformationReturnsSameInstanceForChaining()
    {
        DataSet dataSet = new DataSet();

        DataSet returned = dataSet.setAdditionalInformation("a", 1).setAdditionalInformation("b", 2);

        assertSame(dataSet, returned);
        assertEquals(Optional.of(1), dataSet.getIntegerAdditionalInformation("a"));
        assertEquals(Optional.of(2), dataSet.getIntegerAdditionalInformation("b"));
    }


    @Test
    public void setAdditionalInformationOverwritesExistingKey()
    {
        DataSet dataSet = new DataSet().setAdditionalInformation("k", "old").setAdditionalInformation("k", "new");

        assertEquals(Optional.of("new"), dataSet.getStringAdditionalInformation("k"));
        assertEquals(1, dataSet.copyAdditionalInformation().size());
    }


    @Test
    public void typedGettersReturnValueWhenTypeMatches()
    {
        Object marker = new Object();
        DataSet dataSet = new DataSet()
            .setAdditionalInformation("string", "text")
            .setAdditionalInformation("long", 5L)
            .setAdditionalInformation("integer", 7)
            .setAdditionalInformation("double", 1.5d)
            .setAdditionalInformation("boolean", Boolean.TRUE)
            .setAdditionalInformation("object", marker);

        assertEquals(Optional.of("text"), dataSet.getStringAdditionalInformation("string"));
        assertEquals(Optional.of(5L), dataSet.getLongAdditionalInformation("long"));
        assertEquals(Optional.of(7), dataSet.getIntegerAdditionalInformation("integer"));
        assertEquals(Optional.of(1.5d), dataSet.getDoubleAdditionalInformation("double"));
        assertEquals(Optional.of(Boolean.TRUE), dataSet.getBooleanAdditionalInformation("boolean"));
        assertSame(marker, dataSet.getObjectAdditionalInformation("object").get());
        assertEquals(Optional.of("text"), dataSet.getObjectAdditionalInformation("string"));
    }


    @Test
    public void typedGettersReturnEmptyWhenKeyIsMissing()
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
    public void typedGettersReturnEmptyWhenTypeDoesNotMatch()
    {
        DataSet dataSet = new DataSet()
            .setAdditionalInformation("string", "5")
            .setAdditionalInformation("integer", 5)
            .setAdditionalInformation("boolean", "true");

        assertEquals(Optional.empty(), dataSet.getLongAdditionalInformation("string"));
        assertEquals(Optional.empty(), dataSet.getIntegerAdditionalInformation("string"));
        assertEquals(Optional.empty(), dataSet.getStringAdditionalInformation("integer"));
        assertEquals(Optional.empty(), dataSet.getDoubleAdditionalInformation("integer"));
        assertEquals(Optional.empty(), dataSet.getBooleanAdditionalInformation("boolean"));
    }


    @Test
    public void typedGettersDoNotWidenOrNarrowNumbers_currentBehavior()
    {
        // CHARACTERIZATION: no numeric conversion; a Long is invisible to getIntegerAdditionalInformation and vice versa.
        DataSet dataSet = new DataSet()
            .setAdditionalInformation("long", 5L)
            .setAdditionalInformation("integer", 5);

        assertEquals(Optional.empty(), dataSet.getIntegerAdditionalInformation("long"));
        assertEquals(Optional.empty(), dataSet.getDoubleAdditionalInformation("long"));
        assertEquals(Optional.empty(), dataSet.getLongAdditionalInformation("integer"));
    }


    @Test
    public void nullValueIsStoredButReadsAsEmpty()
    {
        DataSet dataSet = new DataSet().setAdditionalInformation("k", null);

        assertTrue(dataSet.copyAdditionalInformation().containsKey("k"));
        assertEquals(Optional.empty(), dataSet.getObjectAdditionalInformation("k"));
        assertEquals(Optional.empty(), dataSet.getStringAdditionalInformation("k"));
    }


    @Test
    public void copyAdditionalInformationReturnsDetachedCopy()
    {
        DataSet dataSet = new DataSet().setAdditionalInformation("k", "v");

        Map<String, Object> copy = dataSet.copyAdditionalInformation();
        copy.put("added", 1);
        copy.remove("k");

        assertEquals(Optional.of("v"), dataSet.getStringAdditionalInformation("k"));
        assertEquals(Optional.empty(), dataSet.getObjectAdditionalInformation("added"));
        assertNotSame(copy, dataSet.copyAdditionalInformation());
    }


    @Test
    public void builderKeepsReferenceToCallerMap_currentBehavior()
    {
        // CHARACTERIZATION: the builder does not copy the map, so later caller mutations leak into the DataSet.
        Map<String, Object> external = new HashMap<>();
        DataSet dataSet = DataSet.builder().additionalInformation(external).build();

        external.put("leaked", "yes");

        assertEquals(Optional.of("yes"), dataSet.getStringAdditionalInformation("leaked"));
    }


    @Test
    public void mapSetterReplacesAdditionalInformationByReference_currentBehavior()
    {
        // CHARACTERIZATION: Lombok @Data still generates a public setAdditionalInformation(Map) that stores the caller's map.
        Map<String, Object> external = new HashMap<>();
        external.put("k", "v");
        DataSet dataSet = new DataSet().setAdditionalInformation("old", 1);

        dataSet.setAdditionalInformation(external);
        external.put("later", true);

        assertEquals(Optional.empty(), dataSet.getObjectAdditionalInformation("old"));
        assertEquals(Optional.of("v"), dataSet.getStringAdditionalInformation("k"));
        assertEquals(Optional.of(true), dataSet.getBooleanAdditionalInformation("later"));
    }


    @Test(expected = NullPointerException.class)
    public void nullAdditionalInformationFromBuilderMakesTypedGettersThrow_currentBehavior()
    {
        // CHARACTERIZATION: builder/all-args accept a null map, then every accessor NPEs instead of returning empty.
        DataSet.builder().additionalInformation(null).build().getStringAdditionalInformation("k");
    }


    @Test(expected = NullPointerException.class)
    public void nullAdditionalInformationFromConstructorMakesSetterThrow_currentBehavior()
    {
        // CHARACTERIZATION: see nullAdditionalInformationFromBuilderMakesTypedGettersThrow_currentBehavior.
        new DataSet("svc", null, null, null, null, null, null).setAdditionalInformation("k", "v");
    }


    @Test(expected = NullPointerException.class)
    public void nullAdditionalInformationMakesCopyThrow_currentBehavior()
    {
        // CHARACTERIZATION: see nullAdditionalInformationFromBuilderMakesTypedGettersThrow_currentBehavior.
        new DataSet("svc", null, null, null, null, null, null).copyAdditionalInformation();
    }


    @Test
    public void equalityIncludesFixedFieldsAndAdditionalInformation()
    {
        DataSet a = DataSet.builder().serviceName("svc").commitId("c").build().setAdditionalInformation("k", 1);
        DataSet b = DataSet.builder().serviceName("svc").commitId("c").build().setAdditionalInformation("k", 1);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        b.setAdditionalInformation("k", 1L);
        assertNotEquals(a, b);

        DataSet c = DataSet.builder().serviceName("svc").commitId("other").build().setAdditionalInformation("k", 1);
        assertNotEquals(a, c);
    }
}
