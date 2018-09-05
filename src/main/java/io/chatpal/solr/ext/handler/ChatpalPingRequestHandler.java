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

import io.chatpal.solr.ext.DocType;
import io.chatpal.solr.ext.logging.JsonLogMessage;
import io.chatpal.solr.ext.logging.ReportingLogger;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.handler.PingRequestHandler;
import org.apache.solr.request.LocalSolrQueryRequest;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.IndexSchema;

import java.util.HashMap;
import java.util.Map;

public class ChatpalPingRequestHandler extends PingRequestHandler {

    private ReportingLogger reporting = ReportingLogger.getInstance();

    private static final String PARAM_STATS = "stats";
    private static final String PARAM_SCHEMA_VERSION = "schemaVersion";

    private static final String VALUE_NEWEST = "newest";
    private static final String VALUE_OLDEST = "oldest";

    private static final String FIELD_AGE = "created";

    @Override
    protected void handlePing(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
        super.handlePing(req, rsp);

        if (req.getParams().getBool(PARAM_SCHEMA_VERSION, true)) {
            final IndexSchema indexSchema = req.getSchema();
            rsp.add(PARAM_SCHEMA_VERSION, indexSchema.getVersion());
        }

        if (req.getParams().getBool(PARAM_STATS, false)) {
            final Map<String, Object> stats = new HashMap<>();

            stats.put(DocType.Message.getKey(), getStats(DocType.Message.getIndexVal(), req));
            stats.put(DocType.Room.getKey(), getStats(DocType.Room.getIndexVal(), req));
            stats.put(DocType.User.getKey(), getStats(DocType.User.getIndexVal(), req));

            rsp.add(PARAM_STATS, stats);

            reporting.logPing(JsonLogMessage.indexLog().setClient(req.getCore().getName()).setStats(stats));
        }

    }

    private Object getStats(String type, SolrQueryRequest req) {

        final ModifiableSolrParams query = new ModifiableSolrParams();
        query.set(CommonParams.Q, "*:*");
        query.set(CommonParams.ROWS, 0);
        query.set(CommonParams.FQ, "type:" + type);

        query.set("json.facet", String.format("{%s:'min(%s)', %s:'max(%s)'}", VALUE_OLDEST, FIELD_AGE, VALUE_NEWEST, FIELD_AGE));

        try (LocalSolrQueryRequest localRequest = new LocalSolrQueryRequest(req.getCore(), query)) {
            final SolrQueryResponse response = new SolrQueryResponse();

            // TODO: maybe we need a concrete handler here? Such as '/select'?
            req.getCore().getRequestHandler(null).handleRequest(localRequest, response);

            return response.getValues().get("facets");
        }
    }
}
