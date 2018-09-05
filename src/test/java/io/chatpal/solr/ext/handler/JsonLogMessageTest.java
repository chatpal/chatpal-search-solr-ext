package io.chatpal.solr.ext.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.chatpal.solr.ext.logging.JsonLogMessage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JsonLogMessageTest {

    @Test
    public void testJsonString() throws JsonProcessingException {

        String q = JsonLogMessage.queryLog().setClient("col1").setQueryTime(1).setResultSize("room", 10).setSearchTerm("some").toJsonString();

        assertEquals("{\"client\":{\"collection\":\"col1\"},\"query\":{\"searchterm\":\"some\",\"resultsize\":{\"room\":10},\"querytime\":1},\"type\":\"query\"}", q);

        String s = JsonLogMessage.suggestionLog().setClient("col2").setQueryTime(1).setSearchTerm("s").toJsonString();

        assertEquals("{\"client\":{\"collection\":\"col2\"},\"query\":{\"searchterm\":\"s\",\"querytime\":1},\"type\":\"suggestion\"}", s);

    }
}

