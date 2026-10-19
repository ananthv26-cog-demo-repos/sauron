package com.freenow.sauron.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Characterization tests: they pin down how DataSet behaves today (Jackson 2.11, Lombok 1.18.12),
 * because its JSON is the contract between every Sauron plugin and Elasticsearch.
 */
public class DataSetTest
{
    private static final Date EPOCH = new Date(0L);


    private static DataSet sample()
    {
        return DataSet.builder()
            .serviceName("payment-service")
            .repositoryUrl("git@github.com:freenowtech/payment-service.git")
            .commitId("abc123")
            .buildId("42")
            .owner("payments-team")
            .eventTime(EPOCH)
            .build();
    }


    @Test
    public void toJsonWritesFixedFieldsInDeclarationOrderThenAdditionalInformationFlattened() throws Exception
    {
        DataSet dataSet = sample()
            .setAdditionalInformation("count", 5)
            .setAdditionalInformation("nested", Collections.singletonMap("k", "v"));

        assertEquals(
            "{\"serviceName\":\"payment-service\","
                + "\"repositoryUrl\":\"git@github.com:freenowtech/payment-service.git\","
                + "\"commitId\":\"abc123\","
                + "\"buildId\":\"42\","
                + "\"owner\":\"payments-team\","
                + "\"eventTime\":\"1970-01-01T00:00:00.000+00:00\","
                + "\"count\":5,"
                + "\"nested\":{\"k\":\"v\"}}",
            dataSet.toJson()
        );
    }


    @Test
    public void toJsonKeepsNullFieldsAndHasNoAdditionalInformationKey() throws Exception
    {
        assertEquals(
            "{\"serviceName\":null,\"repositoryUrl\":null,\"commitId\":null,\"buildId\":null,\"owner\":null,\"eventTime\":null}",
            new DataSet().toJson()
        );
    }


    @Test
    public void toJsonWritesEventTimeInUtcRegardlessOfJvmTimeZone() throws Exception
    {
        TimeZone original = TimeZone.getDefault();
        try
        {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
            assertTrue(sample().toJson().contains("\"eventTime\":\"1970-01-01T00:00:00.000+00:00\""));
        }
        finally
        {
            TimeZone.setDefault(original);
        }
    }


    @Test
    public void toStringIsTheJson() throws Exception
    {
        DataSet dataSet = sample().setAdditionalInformation("count", 5);
        assertEquals(dataSet.toJson(), dataSet.toString());
    }


    @Test
    public void toStringFallsBackToShortFormWhenJsonCannotBeWritten()
    {
        DataSet dataSet = sample().setAdditionalInformation("opaque", new Opaque());
        assertEquals(
            "DataSet{serviceName='payment-service', commitId='abc123', additionalInformation={opaque=opaque}}",
            dataSet.toString()
        );
    }


    @Test
    public void typedGettersReturnValueOnlyForExactType()
    {
        DataSet dataSet = new DataSet()
            .setAdditionalInformation("string", "s")
            .setAdditionalInformation("long", 7L)
            .setAdditionalInformation("integer", 7)
            .setAdditionalInformation("double", 1.5d)
            .setAdditionalInformation("boolean", true);

        assertEquals(Optional.of("s"), dataSet.getStringAdditionalInformation("string"));
        assertEquals(Optional.of(7L), dataSet.getLongAdditionalInformation("long"));
        assertEquals(Optional.of(7), dataSet.getIntegerAdditionalInformation("integer"));
        assertEquals(Optional.of(1.5d), dataSet.getDoubleAdditionalInformation("double"));
        assertEquals(Optional.of(true), dataSet.getBooleanAdditionalInformation("boolean"));
    }


    @Test
    public void typedGettersDoNotWidenOrConvertNumbers()
    {
        DataSet dataSet = new DataSet()
            .setAdditionalInformation("integer", 7)
            .setAdditionalInformation("long", 7L)
            .setAdditionalInformation("numericString", "7");

        assertEquals(Optional.empty(), dataSet.getLongAdditionalInformation("integer"));
        assertEquals(Optional.empty(), dataSet.getDoubleAdditionalInformation("integer"));
        assertEquals(Optional.empty(), dataSet.getIntegerAdditionalInformation("long"));
        assertEquals(Optional.empty(), dataSet.getIntegerAdditionalInformation("numericString"));
        assertEquals(Optional.empty(), dataSet.getStringAdditionalInformation("integer"));
        assertEquals(Optional.empty(), dataSet.getBooleanAdditionalInformation("numericString"));
    }


    @Test
    public void gettersReturnEmptyForMissingKeyOrNullValue()
    {
        DataSet dataSet = new DataSet().setAdditionalInformation("nullValue", null);

        assertEquals(Optional.empty(), dataSet.getStringAdditionalInformation("missing"));
        assertEquals(Optional.empty(), dataSet.getObjectAdditionalInformation("missing"));
        assertEquals(Optional.empty(), dataSet.getObjectAdditionalInformation("nullValue"));
        assertTrue(dataSet.copyAdditionalInformation().containsKey("nullValue"));
    }


    @Test
    public void objectGetterReturnsAnyNonNullValue()
    {
        Map<String, String> nested = Collections.singletonMap("k", "v");
        DataSet dataSet = new DataSet().setAdditionalInformation("nested", nested);

        assertSame(nested, dataSet.getObjectAdditionalInformation("nested").get());
    }


    @Test
    public void setAdditionalInformationChainsOnSameInstanceAndOverwrites()
    {
        DataSet dataSet = new DataSet();

        assertSame(dataSet, dataSet.setAdditionalInformation("key", "first"));
        dataSet.setAdditionalInformation("key", "second");

        assertEquals(Optional.of("second"), dataSet.getStringAdditionalInformation("key"));
        assertEquals(1, dataSet.copyAdditionalInformation().size());
    }


    @Test
    public void copyAdditionalInformationIsAShallowCopy()
    {
        Map<String, Object> nested = new HashMap<>();
        DataSet dataSet = new DataSet().setAdditionalInformation("nested", nested);

        Map<String, Object> copy = dataSet.copyAdditionalInformation();
        copy.put("added", "x");
        nested.put("inner", "y");

        assertFalse(dataSet.getObjectAdditionalInformation("added").isPresent());
        assertSame(nested, copy.get("nested"));
        assertEquals("y", ((Map<?, ?>) dataSet.getObjectAdditionalInformation("nested").get()).get("inner"));
    }


    @Test
    public void builderAndNoArgsConstructorStartWithIndependentMutableMaps()
    {
        DataSet built = DataSet.builder().build().setAdditionalInformation("a", 1);
        DataSet other = DataSet.builder().build();
        DataSet constructed = new DataSet().setAdditionalInformation("b", 2);

        assertEquals(Optional.of(1), built.getIntegerAdditionalInformation("a"));
        assertTrue(other.copyAdditionalInformation().isEmpty());
        assertEquals(Optional.of(2), constructed.getIntegerAdditionalInformation("b"));
    }


    @Test
    public void setAdditionalInformationMapReplacesTheBackingMap()
    {
        Map<String, Object> replacement = new HashMap<>();
        DataSet dataSet = new DataSet().setAdditionalInformation("old", 1);

        dataSet.setAdditionalInformation(replacement);
        dataSet.setAdditionalInformation("new", 2);

        assertFalse(dataSet.getIntegerAdditionalInformation("old").isPresent());
        assertEquals(2, replacement.get("new"));
    }


    @Test
    public void equalsAndHashCodeIncludeAdditionalInformation()
    {
        assertEquals(sample(), sample());
        assertEquals(sample().hashCode(), sample().hashCode());
        assertNotEquals(sample(), sample().setAdditionalInformation("k", "v"));
    }


    @Test
    public void jsonRoundTripPutsUnknownTopLevelKeysIntoAdditionalInformation() throws Exception
    {
        DataSet read = new ObjectMapper().readValue(
            "{\"serviceName\":\"svc\",\"eventTime\":\"1970-01-01T00:00:00.000+00:00\",\"language\":\"java\",\"flag\":true}",
            DataSet.class
        );

        assertEquals("svc", read.getServiceName());
        assertEquals(EPOCH, read.getEventTime());
        assertEquals(Optional.of("java"), read.getStringAdditionalInformation("language"));
        assertEquals(Optional.of(true), read.getBooleanAdditionalInformation("flag"));
    }


    @Test
    public void jsonRoundTripTurnsSmallLongsIntoIntegers() throws Exception
    {
        DataSet original = sample().setAdditionalInformation("size", 5L);

        DataSet read = new ObjectMapper().readValue(original.toJson(), DataSet.class);

        assertEquals(Optional.empty(), read.getLongAdditionalInformation("size"));
        assertEquals(Optional.of(5), read.getIntegerAdditionalInformation("size"));
        assertNotEquals(original, read);
        assertNotSame(original.copyAdditionalInformation(), read.copyAdditionalInformation());
    }


    private static class Opaque
    {
        @Override
        public String toString()
        {
            return "opaque";
        }
    }
}
