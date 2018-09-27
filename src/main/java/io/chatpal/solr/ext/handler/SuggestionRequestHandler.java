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
import io.chatpal.solr.ext.logging.ReportingLogger;
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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SuggestionRequestHandler extends SearchHandler {

    private Logger logger = LoggerFactory.getLogger(SuggestionRequestHandler.class);

    private ReportingLogger reporting = ReportingLogger.getInstance();

    private static final int MAX_SIZE = 10;

    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {

        long start = System.currentTimeMillis();

        final ModifiableSolrParams params = new ModifiableSolrParams();

        String text = req.getParams().get(ChatpalParams.PARAM_TEXT);

        if (StringUtils.isEmpty(text)) {
            //noinspection unchecked
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
        final String[] typeParams = req.getParams().getParams(ChatpalParams.PARAM_TYPE);
        if (typeParams != null) {
            params.add(CommonParams.FQ, QueryHelper.buildTermsQuery(ChatpalParams.FIELD_TYPE, typeParams));
        }

        // FIXME: should we filter emtpy tokens?
        final List<String> tokens = Stream.of(text.split(" "))
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        if (text.endsWith(" ")) {
            text = null;
        } else {
            text = tokens.remove(tokens.size() - 1);
        }

        tokens.forEach(t -> params.add(CommonParams.FQ, ChatpalParams.FIELD_SUGGESTION + ":" + t));
        // FIXME: What happens if text is 'null' here?
        params.set(FacetParams.FACET_PREFIX, text);

        appendACLFilter(params, req);

        try (LocalSolrQueryRequest userRequest = new LocalSolrQueryRequest(req.getCore(), params)) {
            final SolrQueryResponse response = new SolrQueryResponse();

            super.handleRequestBody(userRequest, response);
            //build response
            final Iterator<Map.Entry> entries = ((NamedList) ((SimpleOrderedMap) ((SimpleOrderedMap) response.getValues().get("facet_counts")).get("facet_fields")).get(ChatpalParams.FIELD_SUGGESTION)).iterator();

            ArrayList<Map> suggestions = new ArrayList<>();

            String prefix = String.join(" ", tokens);

            if (prefix.length() > 0) prefix += " ";

            while (entries.hasNext()) {

                Map.Entry entry = entries.next();
                // FIXME: this 'contains' will never return true...
                if (!tokens.contains(entry.getKey())) {
                    suggestions.add(ImmutableMap.of(
                            "text", prefix + entry.getKey(),
                            "count", entry.getValue()
                    ));
                }

                if (suggestions.size() >= MAX_SIZE) break;
            }

            //noinspection unchecked
            rsp.getValues().add(ChatpalParams.FIELD_SUGGESTION, suggestions);

            reporting.logSuggestion(JsonLogMessage.suggestionLog()
                    .setClient(req.getCore().getName())
                    .setSearchTerm(text)
                    .setQueryTime(System.currentTimeMillis() - start));
        }
    }

    private void appendACLFilter(ModifiableSolrParams query, SolrQueryRequest req) {
        query.add(CommonParams.FQ, buildACLFilter(req.getParams()));
    }

    private String buildACLFilter(SolrParams params) {
        return QueryHelper.buildTermsQuery(ChatpalParams.FIELD_ACL, params.getParams(ChatpalParams.PARAM_ACL));
    }

}
