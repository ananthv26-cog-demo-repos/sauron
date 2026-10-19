package com.freenow.sauron.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class DataSetJsonTest
{
    private static final String EMPTY_JSON =
        "{\"serviceName\":null,\"repositoryUrl\":null,\"commitId\":null,\"buildId\":null,\"owner\":null,\"eventTime\":null}";

    private static final long EVENT_MILLIS = 1600000000123L;

    private static final String EVENT_ISO = "2020-09-13T12:26:40.123+00:00";

    private final ObjectMapper mapper = new ObjectMapper();

    private TimeZone originalTimeZone;


    @Before
    public void pinTimeZone()
    {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }


    @After
    public void restoreTimeZone()
    {
        TimeZone.setDefault(originalTimeZone);
    }


    private static DataSet fullDataSet()
    {
        return DataSet.builder()
            .serviceName("svc")
            .repositoryUrl("git@github.com:org/svc.git")
            .commitId("abc123")
            .buildId("42")
            .owner("team")
            .eventTime(new Date(EVENT_MILLIS))
            .build();
    }


    @Test
    public void toJsonOfEmptyDataSetWritesAllFixedFieldsAsNullInDeclarationOrder() throws Exception
    {
        assertEquals(EMPTY_JSON, new DataSet().toJson());
        assertEquals(EMPTY_JSON, DataSet.builder().build().toJson());
    }


    @Test
    public void toJsonWritesFixedFieldsThenFlattensAdditionalInformation() throws Exception
    {
        DataSet dataSet = fullDataSet().setAdditionalInformation("dependencies", 3);

        assertEquals(
            "{\"serviceName\":\"svc\",\"repositoryUrl\":\"git@github.com:org/svc.git\",\"commitId\":\"abc123\",\"buildId\":\"42\","
                + "\"owner\":\"team\",\"eventTime\":\"" + EVENT_ISO + "\",\"dependencies\":3}",
            dataSet.toJson());
    }


    @Test
    public void toJsonWritesAdditionalInformationValueTypesAsJsonTypes() throws Exception
    {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("a", 1);
        DataSet dataSet = new DataSet()
            .setAdditionalInformation("string", "v")
            .setAdditionalInformation("long", 5L)
            .setAdditionalInformation("integer", 7)
            .setAdditionalInformation("double", 1.5d)
            .setAdditionalInformation("boolean", true)
            .setAdditionalInformation("null", null)
            .setAdditionalInformation("list", Arrays.asList(1, "x"))
            .setAdditionalInformation("nested", nested);

        String json = dataSet.toJson();
        assertTrue(json, json.startsWith(EMPTY_JSON.substring(0, EMPTY_JSON.length() - 1) + ","));
        assertEquals(
            mapper.readTree("{\"serviceName\":null,\"repositoryUrl\":null,\"commitId\":null,\"buildId\":null,\"owner\":null,\"eventTime\":null,"
                + "\"string\":\"v\",\"long\":5,\"integer\":7,\"double\":1.5,\"boolean\":true,\"null\":null,\"list\":[1,\"x\"],\"nested\":{\"a\":1}}"),
            mapper.readTree(json));
    }


    @Test
    public void toJsonDoesNotExposeAdditionalInformationAsNamedProperty() throws Exception
    {
        JsonNode node = mapper.readTree(new DataSet().setAdditionalInformation("k", "v").toJson());

        assertTrue(node.path("additionalInformation").isMissingNode());
        assertEquals("v", node.get("k").asText());
    }


    @Test
    public void toJsonWritesEventTimeAsIsoUtcRegardlessOfDefaultTimeZone() throws Exception
    {
        DataSet dataSet = DataSet.builder().eventTime(new Date(0)).build();
        String expected = "{\"serviceName\":null,\"repositoryUrl\":null,\"commitId\":null,\"buildId\":null,\"owner\":null,"
            + "\"eventTime\":\"1970-01-01T00:00:00.000+00:00\"}";

        assertEquals(expected, dataSet.toJson());
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
        assertEquals(expected, dataSet.toJson());
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        assertEquals(expected, dataSet.toJson());
    }


    @Test
    public void plainObjectMapperWritesEventTimeAsEpochMillis_currentBehavior() throws Exception
    {
        // CHARACTERIZATION: toJson() and a default ObjectMapper disagree on the date format (ISO string vs epoch millis).
        DataSet dataSet = fullDataSet();

        JsonNode viaToJson = mapper.readTree(dataSet.toJson());
        JsonNode viaMapper = mapper.readTree(mapper.writeValueAsString(dataSet));

        assertEquals(EVENT_ISO, viaToJson.get("eventTime").asText());
        assertEquals(EVENT_MILLIS, viaMapper.get("eventTime").asLong());
        assertTrue(viaMapper.get("eventTime").isNumber());
    }


    @Test
    public void nullAdditionalInformationIsSilentlyOmittedFromJson_currentBehavior() throws Exception
    {
        // CHARACTERIZATION: unlike the typed getters (which NPE), toJson tolerates a null map and writes only fixed fields.
        DataSet dataSet = new DataSet("svc", null, null, null, null, null, null);

        String expected = "{\"serviceName\":\"svc\",\"repositoryUrl\":null,\"commitId\":null,\"buildId\":null,\"owner\":null,\"eventTime\":null}";
        assertEquals(expected, dataSet.toJson());
        assertEquals(expected, dataSet.toString());
    }


    @Test
    public void toStringIsTheJsonRepresentation() throws Exception
    {
        DataSet dataSet = fullDataSet().setAdditionalInformation("k", "v");

        assertEquals(dataSet.toJson(), dataSet.toString());
    }


    @Test
    public void toStringFallsBackToShortFormWhenJsonSerializationFails_currentBehavior()
    {
        // CHARACTERIZATION: the fallback drops repositoryUrl, buildId, owner and eventTime, so failed-plugin logs lose them.
        DataSet dataSet = fullDataSet().setAdditionalInformation("opaque", new Unserializable());

        assertEquals("DataSet{serviceName='svc', commitId='abc123', additionalInformation={opaque=unserializable}}", dataSet.toString());
    }


    @Test
    public void deserializationPutsUnknownPropertiesIntoAdditionalInformation() throws Exception
    {
        DataSet dataSet = mapper.readValue(
            "{\"serviceName\":\"svc\",\"commitId\":\"abc123\",\"eventTime\":\"" + EVENT_ISO + "\",\"custom\":\"value\",\"flag\":true}",
            DataSet.class);

        assertEquals("svc", dataSet.getServiceName());
        assertEquals("abc123", dataSet.getCommitId());
        assertEquals(EVENT_MILLIS, dataSet.getEventTime().getTime());
        assertEquals(Optional.of("value"), dataSet.getStringAdditionalInformation("custom"));
        assertEquals(Optional.of(true), dataSet.getBooleanAdditionalInformation("flag"));
        assertEquals(2, dataSet.copyAdditionalInformation().size());
    }


    @Test
    public void deserializationAcceptsEpochMillisForEventTime() throws Exception
    {
        DataSet dataSet = mapper.readValue("{\"eventTime\":" + EVENT_MILLIS + "}", DataSet.class);

        assertEquals(EVENT_MILLIS, dataSet.getEventTime().getTime());
        assertTrue(dataSet.copyAdditionalInformation().isEmpty());
    }


    @Test
    public void jsonRoundTripTurnsSmallLongsIntoIntegers_currentBehavior() throws Exception
    {
        // CHARACTERIZATION: a Long that fits in an int comes back as Integer, so getLongAdditionalInformation returns empty.
        DataSet original = fullDataSet().setAdditionalInformation("count", 5L).setAdditionalInformation("big", 5_000_000_000L);

        DataSet roundTripped = mapper.readValue(original.toJson(), DataSet.class);

        assertEquals(Integer.class, roundTripped.copyAdditionalInformation().get("count").getClass());
        assertEquals(Optional.empty(), roundTripped.getLongAdditionalInformation("count"));
        assertEquals(Optional.of(5), roundTripped.getIntegerAdditionalInformation("count"));
        assertEquals(Optional.of(5_000_000_000L), roundTripped.getLongAdditionalInformation("big"));
        assertNotEquals(original, roundTripped);
    }


    @Test
    public void jsonRoundTripPreservesFixedFieldsAndStructuredValues() throws Exception
    {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("a", 1);
        DataSet original = fullDataSet()
            .setAdditionalInformation("list", Arrays.asList(1, "x"))
            .setAdditionalInformation("nested", nested)
            .setAdditionalInformation("double", 1.5d)
            .setAdditionalInformation("null", null);

        DataSet roundTripped = mapper.readValue(original.toJson(), DataSet.class);

        assertEquals("svc", roundTripped.getServiceName());
        assertEquals("git@github.com:org/svc.git", roundTripped.getRepositoryUrl());
        assertEquals("abc123", roundTripped.getCommitId());
        assertEquals("42", roundTripped.getBuildId());
        assertEquals("team", roundTripped.getOwner());
        assertEquals(new Date(EVENT_MILLIS), roundTripped.getEventTime());
        assertEquals(Optional.of(Arrays.asList(1, "x")), roundTripped.getObjectAdditionalInformation("list"));
        assertEquals(Optional.of(nested), roundTripped.getObjectAdditionalInformation("nested"));
        assertEquals(Optional.of(1.5d), roundTripped.getDoubleAdditionalInformation("double"));
        assertTrue(roundTripped.copyAdditionalInformation().containsKey("null"));
        assertEquals(original, roundTripped);
    }


    @Test
    public void listsComeBackAsJavaUtilList() throws Exception
    {
        DataSet roundTripped = mapper.readValue("{\"list\":[\"a\",\"b\"]}", DataSet.class);

        Object list = roundTripped.getObjectAdditionalInformation("list").get();
        assertTrue(list instanceof List);
        assertEquals(Arrays.asList("a", "b"), list);
    }


    private static final class Unserializable
    {
        @Override
        public String toString()
        {
            return "unserializable";
        }
    }
}
