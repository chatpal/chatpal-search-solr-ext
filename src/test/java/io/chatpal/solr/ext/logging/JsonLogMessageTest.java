/*
 * Copyright (c) 2018-2022 Redlink GmbH.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.chatpal.solr.ext.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
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

