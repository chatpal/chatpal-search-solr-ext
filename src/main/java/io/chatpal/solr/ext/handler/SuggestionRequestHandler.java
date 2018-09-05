/*
 * Copyright 2018 Redlink GmbH
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

package io.chatpal.solr.ext.handler;

import com.google.common.collect.ImmutableMap;
import io.chatpal.solr.ext.ChatpalParams;
import io.chatpal.solr.ext.logging.JsonLogMessage;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.StringUtils;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.LocalSolrQueryRequest;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SuggestionRequestHandler extends SearchHandler {

    private Logger logger = LoggerFactory.getLogger(SuggestionRequestHandler.class);

    private Logger elasticLogger = LoggerFactory.getLogger("elasticLogger");

    private static final int MAX_SIZE = 10;

    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {

        long start = System.currentTimeMillis();

        ModifiableSolrParams params = new ModifiableSolrParams();

        String text = req.getParams().get(ChatpalParams.PARAM_TEXT);

        if (StringUtils.isEmpty(text)) {
            rsp.getValues().add(ChatpalParams.FIELD_SUGGESTION, Collections.emptyList());
            return;
        }

        params.set(CommonParams.Q, "*:*");
        params.set(CommonParams.ROWS, 0);
        params.set(FacetParams.FACET, true);
        params.set(FacetParams.FACET_FIELD, ChatpalParams.FIELD_SUGGESTION);
        params.set(FacetParams.FACET_MINCOUNT, 1);
        params.set(FacetParams.FACET_LIMIT, 15);

        //set filter for type
        String[] typeParams = req.getParams().getParams(ChatpalParams.PARAM_TYPE);
        if (typeParams != null) {
            String types = String.join(" OR ", typeParams);
            params.add(CommonParams.FQ, ChatpalParams.FIELD_TYPE + ":(" + types + ")");
        }

        List<String> tokens = Stream.of(text.split(" ")).map(String::toLowerCase).collect(Collectors.toList());

        if (text.endsWith(" ")) {
            text = null;
        } else {
            text = tokens.remove(tokens.size() - 1);
        }

        tokens.forEach(t -> params.add(CommonParams.FQ, ChatpalParams.FIELD_SUGGESTION + ":" + t));
        params.set(FacetParams.FACET_PREFIX, text);

        appendACLFilter(params, req);

        //logger.info("suggestion query: {}", params);

        try (LocalSolrQueryRequest userRequest = new LocalSolrQueryRequest(req.getCore(), params)) {
            final SolrQueryResponse response = new SolrQueryResponse();

            super.handleRequestBody(userRequest, response);
            //build response
            Iterator<Map.Entry> entries = ((NamedList) ((SimpleOrderedMap) ((SimpleOrderedMap) response.getValues().get("facet_counts")).get("facet_fields")).get(ChatpalParams.FIELD_SUGGESTION)).iterator();

            ArrayList<Map> suggestions = new ArrayList<>();

            String prefix = tokens.stream().collect(Collectors.joining(" "));

            if (prefix.length() > 0) prefix += " ";

            while (entries.hasNext()) {

                Map.Entry entry = entries.next();
                if (!tokens.contains(entry.getKey())) {
                    suggestions.add(ImmutableMap.of(
                            "text", prefix + entry.getKey(),
                            "count", entry.getValue()
                    ));
                }

                if (suggestions.size() == MAX_SIZE) break;
            }

            rsp.getValues().add(ChatpalParams.FIELD_SUGGESTION, suggestions);

            elasticLogger.info(JsonLogMessage.suggestionLog()
                    .setClient(req.getCore().getName())
                    .setSearchTerm(text)
                    .setQueryTime(System.currentTimeMillis() - start)
                    .toJsonString());
        }
    }

    private void appendACLFilter(ModifiableSolrParams query, SolrQueryRequest req) {
        query.add(CommonParams.FQ, buildACLFilter(req.getParams()));
    }

    private String buildACLFilter(SolrParams params) {
        return buildOrFilter(params, ChatpalParams.PARAM_ACL, ChatpalParams.FIELD_ACL);
    }

    private String buildOrFilter(SolrParams solrParams, String param, String field) {
        final String[] values = solrParams.getParams(param);
        if (values == null || values.length < 1) {
            return "-" + field + ":*";
        }

        return "{!q.op=OR}" + field + ":" +
                Arrays.stream(values)
                        .map(ClientUtils::escapeQueryChars)
                        .collect(Collectors.joining(" ", "(", ")"));
    }
}
