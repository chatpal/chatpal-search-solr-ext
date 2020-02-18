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

import io.chatpal.solr.ext.ChatpalApiConfig;
import io.chatpal.solr.ext.ChatpalParams;
import io.chatpal.solr.ext.DocType;
import io.chatpal.solr.ext.logging.JsonLogMessage;
import io.chatpal.solr.ext.logging.ReportingLogger;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.IndexableField;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.common.params.HighlightParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.LocalSolrQueryRequest;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.DocsStreamer;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.DocList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;

public class ChatpalSearchRequestHandler extends SearchHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatpalSearchRequestHandler.class);

    private ReportingLogger reporting = ReportingLogger.getInstance();

    private Map<DocType, SolrParams> defaultParams = new EnumMap<>(DocType.class);

    private ChatpalApiConfig apiConfig = new ChatpalApiConfig();

    @Override
    public void inform(SolrCore core) {
        super.inform(core);

        apiConfig = ChatpalApiConfig.fromSolrConfig(core.getSolrConfig());
    }

    @Override
    public void init(NamedList args) {
        super.init(args);

        if( args != null ) {
            for (DocType docType : DocType.values()) {
                defaultParams.put(docType, getSolrParamsFromNamedList(args, docType.getKey()));
            }
        }
    }

    @Override
    public void handleRequestBody(SolrQueryRequest originalReq, SolrQueryResponse rsp) throws Exception {
        long start = System.currentTimeMillis();
        final JsonLogMessage.QueryLog log = JsonLogMessage.queryLog()
                .setClient(originalReq.getCore().getName())
                .setSearchTerm(originalReq.getParams().get(ChatpalParams.PARAM_TEXT));

        final Loggable msgLog = queryFor(DocType.Message, originalReq, rsp,
                this::setLanguageConfig,
                this::setTimeRegressionBoost,
                this::appendACLFilter,
                this::appendExclusionFilter);
        if (msgLog != null) {
            log.setResultSize(DocType.Message.getKey(), msgLog.numFound);
        }

        if (apiConfig.getFileSearch().isEnabled()) {
            final Loggable fileLog = queryFor(DocType.File, originalReq, rsp,
                    //file search does not use a language
                    (query, req, rsponse, docType) -> query.set(ChatpalParams.PARAM_LANG, ChatpalParams.LANG_NONE),
                    this::setTimeRegressionBoost,
                    this::appendACLFilter,
                    this::appendExclusionFilter);
            if (fileLog != null) {
                log.setResultSize(DocType.File.getKey(), fileLog.numFound);
            }
        }

        final Loggable roomLog = queryFor(DocType.Room, originalReq, rsp,
                this::appendACLFilter,
                this::appendExclusionFilter);
        if (roomLog != null) {
            log.setResultSize(DocType.Room.getKey(), roomLog.numFound);
        }

        final Loggable userLog = queryFor(DocType.User, originalReq, rsp);
        if (userLog != null) {
            log.setResultSize(DocType.User.getKey(), userLog.numFound);
        }

        log.setQueryTime(System.currentTimeMillis() - start);

        reporting.logQuery(log);
    }

    private Loggable queryFor(DocType docType, SolrQueryRequest req, SolrQueryResponse rsp, QueryAdapter... queryAdapter) throws Exception {
        if (!typeFilterAccepts(req, docType)) return null;

        final ModifiableSolrParams query = new ModifiableSolrParams();
        final String reqLanguage = req.getParams().get(ChatpalParams.PARAM_LANG, ChatpalParams.LANG_NONE);

        //NOTES:
        // * the 'query' parameter overrides the 'text' parameter
        // * the 'text' parameter only allows for wildcards ('*' and '?')
        final String q = req.getParams().get(ChatpalParams.PARAM_QUERY);
        if(q != null){ //explicit query parsed in request:
            query.set("defType", "lucene"); //deactivate edismax if a query is parsed
            query.set(CommonParams.Q, q);
        } else { //normal text query
            query.set(CommonParams.Q, QueryHelper.cleanTextQuery(req.getParams().get(ChatpalParams.PARAM_TEXT)));
        }

        // should sort be type aware?
        query.set(CommonParams.SORT, req.getParams().get(CommonParams.SORT));

        query.set(CommonParams.FQ, buildTypeFilter(docType));

        query.set(CommonParams.START, req.getParams()
                .get(buildTypeParam(docType, ChatpalParams.PARAM_START),
                        req.getParams().get(ChatpalParams.PARAM_START)));
        query.set(CommonParams.ROWS, req.getParams()
                .get(buildTypeParam(docType, ChatpalParams.PARAM_ROWS),
                        req.getParams().get(ChatpalParams.PARAM_ROWS)));

        // Type specific adaptions
        for (QueryAdapter adapter : queryAdapter) {
            adapter.adaptQuery(query, req, rsp, docType);
        }

        //we need the unique field to process inlineHighlighting
        ModifiableSolrParams appendedParams = new ModifiableSolrParams();
        if(req.getSchema().getUniqueKeyField() != null) {
            appendedParams.add(CommonParams.FL, req.getSchema().getUniqueKeyField().getName());
        }

        // param hierarchy
        final SolrParams defaultedQuery =
                SolrParams.wrapAppended(
                        SolrParams.wrapDefaults(
                        // 1. req.getParams()
                        query,
                        SolrParams.wrapDefaults(
                                // 2. [type].defaults
                                defaultParams.get(docType),
                                // 3. defaults
                                defaults
                        )
                ),
                appendedParams
        );

        LOGGER.debug("Chatpal query: {}", defaultedQuery);

        try (LocalSolrQueryRequest subRequest = new LocalSolrQueryRequest(req.getCore(), defaultedQuery)) {
            final SolrQueryResponse response = new SolrQueryResponse();
            super.handleRequestBody(subRequest, response);
            final String lang = subRequest.getParams().get(ChatpalParams.PARAM_LANG, reqLanguage);
            rsp.add(docType.getKey(), materializeResult(req.getSchema(), response, lang));

            return new Loggable(((ResultContext) response.getResponse()).getDocList().matches());
        }
    }

    @SuppressWarnings({"unused", "squid:S1172"})
    private void setLanguageConfig(ModifiableSolrParams query, SolrQueryRequest req, SolrQueryResponse rsp, DocType docType) {
        final String language = req.getParams().get(ChatpalParams.PARAM_LANG, ChatpalParams.LANG_NONE);
        if (isParamSet(req, ChatpalParams.PARAM_QUERY)) {
            query.set(CommonParams.DF, "text_"+language); //use the text_{lang} field as default field
        } else {
            query.set(DisMaxParams.QF, "context^2 text_${lang}^1 decompose_text_${lang}^.5"
                    .replaceAll("\\$\\{lang}", language));
            query.add(HighlightParams.FIELDS, "text_${lang}"
                    .replaceAll("\\$\\{lang}", language));
        }
    }

    @SuppressWarnings({"unused", "squid:S1172"})
    private void setTimeRegressionBoost(ModifiableSolrParams query, SolrQueryRequest req, SolrQueryResponse rsp, DocType docType) {
        if (!isParamSet(req, ChatpalParams.PARAM_QUERY)) {
            query.set(DisMaxParams.BF, "recip(ms(NOW,updated),3.6e-11,3,1)");
        }
    }

    private boolean isParamSet(SolrQueryRequest req, String param) {
        return req.getParams().get(param) != null;
    }

    private boolean typeFilterAccepts(SolrQueryRequest req, DocType type) {
        final String[] types = req.getParams().getParams(ChatpalParams.PARAM_TYPE);
        return ArrayUtils.isEmpty(types) || ArrayUtils.contains(types, type.getKey());
    }


    private NamedList materializeResult(IndexSchema schema, SolrQueryResponse rsp, String language) {
        final NamedList<Object> result = new NamedList<>();

        final ResultContext rspContext  = (ResultContext) rsp.getResponse();
        final DocList docList = rspContext.getDocList();

        final ArrayList<SolrDocument> docs = new ArrayList<>(docList.size());
        @SuppressWarnings("unchecked")
        final NamedList<NamedList<Object>> highlighting = (NamedList) rsp.getValues().get("highlighting");

        final DocsStreamer documentIterator =  new DocsStreamer(rspContext);
        while (documentIterator.hasNext()) {
            final SolrDocument doc = documentIterator.next();

            inlineHighlighting(doc, highlighting, rspContext, schema, language);

            for (String fName : new HashSet<>(doc.getFieldNames())) {
                if (!rspContext.getReturnFields().wantsField(fName)) {
                    doc.removeFields(fName);
                }
            }

            //do not return the internal uid field
            doc.removeFields(schema.getUniqueKeyField().getName());

            docs.add(doc);
        }
        result.add("docs", docs);
        result.add("numFound", docList.matches());
        result.add("start", docList.offset());
        if (docList.hasScores()) {
            result.add("maxScore", docList.maxScore());
        }

        final NamedList<?> facets = (NamedList) rsp.getValues().get("facet_counts");
        if (facets != null) {
            result.add("facets", facets);
        }

        return result;
    }

    private void inlineHighlighting(SolrDocument doc, NamedList<NamedList<Object>> highlighting, ResultContext rspContext,
                                    IndexSchema schema, String language) {
        if (highlighting == null) return;

        final String id = String.valueOf(getFirstValue(doc, schema.getUniqueKeyField()));
        final NamedList<Object> highlights = highlighting.get(id);
        if (highlights == null) return;

        for (Map.Entry<String, Object> highlight : highlights) {
            final String fieldName = highlight.getKey();
            final Object fieldValue = highlight.getValue();

            final String targetField = StringUtils.removeEnd(fieldName, "_" + language);

            if (!rspContext.getReturnFields().wantsField(targetField)) continue;

            if (isMultiValueFiled(schema, targetField)) {
                doc.setField(targetField, fieldValue);
            } else {
                final Object firstVal = getFirstValue(fieldValue);
                if (firstVal != null) {
                    doc.setField(targetField, fieldValue);
                }
            }
        }
    }

    private Object getFirstValue(Object fieldValue) {
        if (fieldValue == null) {
            return null;
        } else if (fieldValue.getClass().isArray()) {
            final Object[] arr = (Object[]) fieldValue;
            if (arr.length > 0) {
                return arr[0];
            }
        } else if (fieldValue instanceof Collection) {
            final Collection c = (Collection) fieldValue;
            if (!c.isEmpty()) {
                return c.iterator().next();
            }
        } else {
            return fieldValue;
        }

        return null;
    }

    private boolean isMultiValueFiled(IndexSchema schema, String fieldName) {
        final SchemaField fieldOrNull = schema.getFieldOrNull(fieldName);
        return fieldOrNull == null || fieldOrNull.multiValued();
    }

    private Object getFirstValue(SolrDocument doc, SchemaField field) {
        final Object value = doc.getFirstValue(field.getName());
        if (value instanceof IndexableField) {
            return DocsStreamer.getValue(field, (IndexableField) value);
        }
        return value;
    }

    private String buildTypeParam(DocType message, String param) {
        return message.getKey() + "." + param;
    }

    private String buildTypeFilter(DocType type) {
        return ChatpalParams.FIELD_TYPE + ":" + type.getIndexVal();
    }

    @SuppressWarnings({"unused", "squid:S1172"})
    private void appendACLFilter(ModifiableSolrParams query, SolrQueryRequest req, SolrQueryResponse rsp, DocType docType) {
        query.add(CommonParams.FQ, buildACLFilter(req.getParams()));
    }

    private String buildACLFilter(SolrParams params) {
        return QueryHelper.buildTermsQuery(ChatpalParams.FIELD_ACL, params.getParams(ChatpalParams.PARAM_ACL));
    }

    @SuppressWarnings({"unused", "squid:S1172"})
    private void appendExclusionFilter(ModifiableSolrParams query, SolrQueryRequest req, SolrQueryResponse rsp, DocType docType) {
        final SolrParams params = req.getParams();
        if(docType == DocType.Message || docType == DocType.Room){
            String exclRoomFilter = buildExclusionFilter(ChatpalParams.FIELD_ROOM_ID, params.getParams(ChatpalParams.PARAM_EXCL_ROOM));
            if(StringUtils.isNotBlank(exclRoomFilter)){
                query.add(CommonParams.FQ, exclRoomFilter);
            }
        }
        if(docType == DocType.Message){
            String exclMsgFilter = buildExclusionFilter(ChatpalParams.FIELD_MSG_ID, params.getParams(ChatpalParams.PARAM_EXCL_MSG));
            if(StringUtils.isNotBlank(exclMsgFilter)){
                query.add(CommonParams.FQ, exclMsgFilter);
            }
        }
    }

    private String buildExclusionFilter(String field, String...excluded) {
        if(field == null || ArrayUtils.isEmpty(excluded)) {
            return null;
        }
        return "-" + QueryHelper.buildTermsQuery(field, excluded);
    }


    private interface QueryAdapter {
        void adaptQuery(ModifiableSolrParams query, SolrQueryRequest req, SolrQueryResponse rsp, DocType docType);
    }

    static class Loggable {
        long numFound;

        Loggable(long numFound) {
            this.numFound = numFound;
        }
    }
}
