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

package io.chatpal.solr.ext.handler;

import com.google.common.collect.ImmutableMap;
import io.chatpal.solr.ext.ChatpalConfig;
import io.chatpal.solr.ext.ChatpalParams;
import io.chatpal.solr.ext.logging.JsonLogMessage;
import io.chatpal.solr.ext.logging.ReportingLogger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
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

public class SuggestionRequestHandler extends SearchHandler {

    private static final int DEFAULT_SUGGESTION_SIZE = 10;
    private static final Pattern WHITE_SPACE_SPLIT = Pattern.compile("\\s+");

    private static final Logger LOGGER = LoggerFactory.getLogger(SuggestionRequestHandler.class);

    private final ReportingLogger reporting = ReportingLogger.getInstance();

    private int suggestionsSize = DEFAULT_SUGGESTION_SIZE;

    @Override
    public void init(NamedList args) {
        super.init(args);

        if( args != null ) {
            final Object size = args.get(ChatpalConfig.CONF_SUGGESTION_SIZE);
            suggestionsSize = NumberUtils.toInt(String.valueOf(size), DEFAULT_SUGGESTION_SIZE);
            if (suggestionsSize <= 0) {
                LOGGER.warn("Configured {} is less than 1, falling back to default {}",
                        ChatpalConfig.CONF_SUGGESTION_SIZE, DEFAULT_SUGGESTION_SIZE);
                suggestionsSize = DEFAULT_SUGGESTION_SIZE;
            }
        }
    }

    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
        @SuppressWarnings("squid:S1941")
        final long start = System.currentTimeMillis();

        String text = req.getParams().get(ChatpalParams.PARAM_TEXT);

        if (StringUtils.isEmpty(text)) {
            //noinspection unchecked
            rsp.getValues().add(ChatpalParams.FIELD_SUGGESTION, Collections.emptyList());
            return;
        }

        final ModifiableSolrParams params = new ModifiableSolrParams();
        params.set(CommonParams.Q, "*:*");
        params.set(CommonParams.ROWS, 0);
        params.set(FacetParams.FACET, true);
        params.set(FacetParams.FACET_FIELD, ChatpalParams.FIELD_SUGGESTION);
        params.set(FacetParams.FACET_MINCOUNT, 1);
        params.set(FacetParams.FACET_LIMIT, 15);

        //set filter for type
        final String[] typeParams = QueryHelper.getMultiValueParam(ChatpalParams.PARAM_TYPE, req.getParams());
        if (typeParams != null) {
            params.add(CommonParams.FQ, QueryHelper.buildTermsQuery(ChatpalParams.FIELD_TYPE, typeParams));
        }

        final List<String> tokens = WHITE_SPACE_SPLIT.splitAsStream(text)
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        if (text.endsWith(" ")) {
            text = null;
        } else {
            text = tokens.remove(tokens.size() - 1);
        }

        tokens.forEach(t -> params.add(CommonParams.FQ, ChatpalParams.FIELD_SUGGESTION + ":" + t));
        params.set(FacetParams.FACET_PREFIX, text);

        appendACLFilter(params, req);

        try (LocalSolrQueryRequest userRequest = new LocalSolrQueryRequest(req.getCore(), params)) {
            final SolrQueryResponse response = new SolrQueryResponse();

            super.handleRequestBody(userRequest, response);
            //build response
            //noinspection unchecked
            final Iterator<Map.Entry<String, Object>> entries =
                    ((NamedList) (
                            (SimpleOrderedMap) (
                                    (SimpleOrderedMap) response.getValues().get("facet_counts")
                            ).get("facet_fields")
                    ).get(ChatpalParams.FIELD_SUGGESTION))
                            .iterator();

            final List<Map> suggestions = new ArrayList<>();

            String prefix = String.join(" ", tokens);
            if (prefix.length() > 0) prefix += " ";

            while (entries.hasNext()) {
                final Map.Entry<String, Object> entry = entries.next();
                if (!tokens.contains(entry.getKey())) {
                    suggestions.add(ImmutableMap.of(
                            "text", prefix + entry.getKey(),
                            "count", entry.getValue()
                    ));
                }

                if (suggestions.size() >= suggestionsSize) break;
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
        return QueryHelper.buildTermsQuery(ChatpalParams.FIELD_ACL, QueryHelper.getMultiValueParam(ChatpalParams.PARAM_ACL, params));
    }

}
