package com.freenow.sauron.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DataSetJsonTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String EMPTY_JSON =
        "{\"serviceName\":null,\"repositoryUrl\":null,\"commitId\":null,\"buildId\":null,\"owner\":null,\"eventTime\":null}";


    private static DataSet fullDataSet()
    {
        return DataSet.builder()
            .serviceName("svc")
            .repositoryUrl("url")
            .commitId("c1")
            .buildId("b1")
            .owner("o")
            .eventTime(new Date(1577836800123L))
            .build();
    }


    @Test
    public void toJsonOfEmptyDataSetWritesAllFixedFieldsAsNullAndNoAdditionalInformationKey() throws Exception
    {
        assertEquals(EMPTY_JSON, new DataSet().toJson());
        assertEquals(EMPTY_JSON, DataSet.builder().build().toJson());
    }


    @Test
    public void toJsonWritesIsoDateWithColonOffsetAndFlattensAdditionalInformation() throws Exception
    {
        DataSet dataSet = fullDataSet().setAdditionalInformation("environment", "prod");

        assertEquals(
            "{\"serviceName\":\"svc\",\"repositoryUrl\":\"url\",\"commitId\":\"c1\",\"buildId\":\"b1\",\"owner\":\"o\","
                + "\"eventTime\":\"2020-01-01T00:00:00.123+00:00\",\"environment\":\"prod\"}",
            dataSet.toJson()
        );
    }


    @Test
    public void toJsonWritesAdditionalValuesWithTheirJsonTypes() throws Exception
    {
        DataSet dataSet = new DataSet()
            .setAdditionalInformation("long", 5L)
            .setAdditionalInformation("double", 1.5d)
            .setAdditionalInformation("bool", true)
            .setAdditionalInformation("nothing", null)
            .setAdditionalInformation("list", Arrays.asList(1, "a"))
            .setAdditionalInformation("map", Collections.singletonMap("x", 1));

        assertEquals(
            MAPPER.readTree(EMPTY_JSON.replace("}", ",\"long\":5,\"double\":1.5,\"bool\":true,\"nothing\":null,"
                + "\"list\":[1,\"a\"],\"map\":{\"x\":1}}")),
            MAPPER.readTree(dataSet.toJson())
        );
    }


    @Test
    public void toJsonAlwaysWritesUtcRegardlessOfJvmDefaultTimeZone() throws Exception
    {
        TimeZone original = TimeZone.getDefault();
        try
        {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
            DataSet dataSet = DataSet.builder().eventTime(new Date(0L)).build();
            assertTrue(dataSet.toJson().contains("\"eventTime\":\"1970-01-01T00:00:00.000+00:00\""));
        }
        finally
        {
            TimeZone.setDefault(original);
        }
    }


    @Test
    public void toJsonWithNullAdditionalInformationStillSerializesFixedFields() throws Exception
    {
        DataSet dataSet = new DataSet("svc", null, null, null, null, null, null);

        assertEquals(EMPTY_JSON.replace("\"serviceName\":null", "\"serviceName\":\"svc\""), dataSet.toJson());
    }


    @Test
    public void toStringReturnsTheSameJsonAsToJson() throws Exception
    {
        DataSet dataSet = fullDataSet().setAdditionalInformation("k", "v");

        assertEquals(dataSet.toJson(), dataSet.toString());
        assertEquals(EMPTY_JSON, new DataSet().toString());
    }


    @Test
    public void toStringFallsBackToShortFormWhenJsonFails_currentBehavior()
    {
        // CHARACTERIZATION: if any additional value is not JSON-serializable, toString silently switches format and drops repositoryUrl, buildId, owner and eventTime.
        Object notSerializable = new Object();
        DataSet dataSet = fullDataSet().setAdditionalInformation("obj", notSerializable);

        assertEquals(
            "DataSet{serviceName='svc', commitId='c1', additionalInformation={obj=" + notSerializable + "}}",
            dataSet.toString()
        );
    }


    @Test
    public void toJsonThrowsWhenAdditionalValueIsNotSerializable()
    {
        DataSet dataSet = new DataSet().setAdditionalInformation("obj", new Object());
        try
        {
            dataSet.toJson();
            fail("expected InvalidDefinitionException");
        }
        catch (JsonProcessingException e)
        {
            assertEquals(InvalidDefinitionException.class, e.getClass());
        }
    }


    @Test
    public void additionalKeyNamedLikeAFieldProducesDuplicateJsonKeys_currentBehavior() throws Exception
    {
        // CHARACTERIZATION: an extra key "serviceName" is written as a second "serviceName" property (invalid for strict parsers) and wins on read-back.
        DataSet dataSet = DataSet.builder().serviceName("real").build().setAdditionalInformation("serviceName", "shadow");

        String json = dataSet.toJson();

        assertEquals(EMPTY_JSON.replace("\"serviceName\":null", "\"serviceName\":\"real\"")
            .replace("}", ",\"serviceName\":\"shadow\"}"), json);
        DataSet readBack = MAPPER.readValue(json, DataSet.class);
        assertEquals("shadow", readBack.getServiceName());
        assertTrue(readBack.copyAdditionalInformation().isEmpty());
    }


    @Test
    public void jsonRoundTripTurnsSmallLongsIntoIntegers_currentBehavior() throws Exception
    {
        // CHARACTERIZATION: a Long that fits in int comes back as Integer, so getLongAdditionalInformation returns empty and the DataSet no longer equals the original.
        DataSet original = fullDataSet().setAdditionalInformation("tookSeconds", 5L);

        DataSet readBack = MAPPER.readValue(original.toJson(), DataSet.class);

        assertEquals(Optional.empty(), readBack.getLongAdditionalInformation("tookSeconds"));
        assertEquals(Optional.of(5), readBack.getIntegerAdditionalInformation("tookSeconds"));
        assertNotEquals(original, readBack);
    }


    @Test
    public void jsonReadKeepsFixedFieldsAndPutsUnknownKeysIntoAdditionalInformation() throws Exception
    {
        DataSet readBack = MAPPER.readValue(fullDataSet().toJson(), DataSet.class);
        assertEquals(fullDataSet(), readBack);

        DataSet typed = MAPPER.readValue(
            "{\"big\":12345678901,\"f\":1.0,\"s\":\"x\",\"b\":false,\"arr\":[1,2],\"obj\":{\"a\":1}}", DataSet.class);
        assertEquals(Optional.of(12345678901L), typed.getLongAdditionalInformation("big"));
        assertEquals(Optional.of(1.0d), typed.getDoubleAdditionalInformation("f"));
        assertEquals(Optional.of("x"), typed.getStringAdditionalInformation("s"));
        assertEquals(Optional.of(false), typed.getBooleanAdditionalInformation("b"));
        assertEquals(Arrays.asList(1, 2), typed.getObjectAdditionalInformation("arr").get());
        assertEquals(Collections.singletonMap("a", 1), typed.getObjectAdditionalInformation("obj").get());
    }


    @Test
    public void jsonReadAcceptsOffsetDatesAndEpochMillis() throws Exception
    {
        assertEquals(1591005600000L,
            MAPPER.readValue("{\"eventTime\":\"2020-06-01T12:00:00.000+02:00\"}", DataSet.class).getEventTime().getTime());
        assertEquals(1577836800123L,
            MAPPER.readValue("{\"eventTime\":1577836800123}", DataSet.class).getEventTime().getTime());
    }


    @Test
    public void jsonPropertyNamedAdditionalInformationReplacesTheMap() throws Exception
    {
        DataSet readBack = MAPPER.readValue("{\"additionalInformation\":{\"a\":1},\"z\":2}", DataSet.class);

        assertEquals(Optional.of(1), readBack.getIntegerAdditionalInformation("a"));
        assertEquals(Optional.of(2), readBack.getIntegerAdditionalInformation("z"));
        assertEquals(2, readBack.copyAdditionalInformation().size());
    }


    @Test
    public void jsonNullAdditionalInformationFollowedByExtraKeyFails_currentBehavior() throws Exception
    {
        // CHARACTERIZATION: "additionalInformation":null nulls the map via the Lombok setter, so the next unknown key fails with an NPE wrapped in JsonMappingException.
        try
        {
            MAPPER.readValue("{\"additionalInformation\":null,\"z\":1}", DataSet.class);
            fail("expected JsonMappingException");
        }
        catch (JsonMappingException e)
        {
            assertEquals(NullPointerException.class, e.getCause().getClass());
        }
    }


    @Test
    public void defaultObjectMapperWritesEventTimeAsEpochMillis()
    {
        // Plugins such as console-output serialize DataSet with a plain ObjectMapper instead of toJson().
        DataSet dataSet = DataSet.builder().eventTime(new Date(1577836800123L)).build().setAdditionalInformation("k", "v");

        try
        {
            assertEquals(EMPTY_JSON.replace("\"eventTime\":null", "\"eventTime\":1577836800123").replace("}", ",\"k\":\"v\"}"),
                MAPPER.writeValueAsString(dataSet));
        }
        catch (JsonProcessingException e)
        {
            fail(e.getMessage());
        }
    }
}
