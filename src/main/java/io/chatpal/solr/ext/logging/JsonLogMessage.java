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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.solr.common.util.NamedList;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class JsonLogMessage {

    private static ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    private JsonLogMessage() {}

    public static QueryLog queryLog() {
        return new QueryLog();
    }

    public static SuggestionLog suggestionLog() {
        return new SuggestionLog();
    }

    public static IndexLog indexLog() {
        return new IndexLog();
    }

    public abstract static class Log {

        @JsonProperty("type")
        public abstract String getType();

        @JsonProperty("client")
        public Client client;

        public Log setClient(String collection) {
            this.client = new Client(collection);
            return this;
        }

        public static class Client {
            private String collection;

            protected Client(String collection) {
                this.collection = collection;
            }

            public String getCollection() {
                return collection;
            }

            public void setCollection(String collection) {
                this.collection = collection;
            }
        }

        @JsonIgnore
        public String toJsonString() throws JsonProcessingException {
            return mapper.writeValueAsString(this);
        }
    }

    public static class SuggestionLog extends Log {
        @Override
        public String getType() {
            return "suggestion";
        }

        private Map<String,Object> query;

        public SuggestionLog() {
            this.query = new HashMap<>();
        }

        @JsonProperty("query")
        public Map<String, Object> getQuery() {
            return query;
        }

        public SuggestionLog setQueryTime(long querytime) {
            query.put("querytime", querytime);
            return this;
        }

        public SuggestionLog setSearchTerm(String searchTerm) {
            query.put("searchterm", searchTerm);
            return this;
        }

        @Override
        public SuggestionLog setClient(String collection) {
            super.setClient(collection);
            return this;
        }
    }

    public static class QueryLog extends Log {

        private Map<String,Object> query;

        public QueryLog() {
            this.query = new HashMap<>();
            query.put("resultsize", new HashMap<>());
        }

        @Override
        public String getType() {
            return "query";
        }

        @Override
        public QueryLog setClient(String collection) {
            super.setClient(collection);
            return this;
        }

        @JsonProperty("query")
        public Map<String, Object> getQuery() {
            return query;
        }

        public QueryLog setQueryTime(long querytime) {
            query.put("querytime", querytime);
            return this;
        }

        public QueryLog setResultSize(String type, long resultSize) {
            ((Map)query.get("resultsize")).put(type, resultSize);
            return this;
        }

        public QueryLog setSearchTerm(String searchTerm) {
            query.put("searchterm", searchTerm);
            return this;
        }

    }

    public static class IndexLog extends Log {

        private Map<String, Map> stats = new HashMap<>();

        @Override
        public String getType() {
            return "index";
        }

        @JsonProperty("stats")
        public Object getStats() {
            return stats;
        }

        @Override
        public IndexLog setClient(String collection) {
            super.setClient(collection);
            return this;
        }

        public IndexLog setStats(Map<String, Object> stats) {
            for(Map.Entry<String, Object> entry: stats.entrySet()) {
                this.stats.put(entry.getKey(), Collections.singletonMap("count", ((NamedList) entry.getValue()).get("count")));
            }
            return this;
        }
    }

}
