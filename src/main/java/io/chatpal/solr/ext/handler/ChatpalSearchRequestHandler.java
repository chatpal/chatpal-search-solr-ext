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

import io.chatpal.solr.ext.ChatpalParams;
import io.chatpal.solr.ext.DocType;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.IndexableField;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.*;
import org.apache.solr.common.util.NamedList;
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

import java.util.*;
import java.util.stream.Collectors;

public class ChatpalSearchRequestHandler extends SearchHandler {

    private Logger logger = LoggerFactory.getLogger(ChatpalSearchRequestHandler.class);

    private Map<DocType, SolrParams> defaultParams = new EnumMap<>(DocType.class);

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
        queryFor(DocType.Message, originalReq, rsp, this::appendACLFilter);
        queryFor(DocType.Room, originalReq, rsp, this::appendACLFilter);
        queryFor(DocType.User, originalReq, rsp);
    }

    private void queryFor(DocType docType, SolrQueryRequest req, SolrQueryResponse rsp, QueryAdapter... queryAdapter) throws Exception {
        if (!typeFilterAccepts(req, docType)) return;

        final ModifiableSolrParams query = new ModifiableSolrParams();
        final String language = req.getParams().get(ChatpalParams.PARAM_LANG, ChatpalParams.LANG_NONE);

        query.set(CommonParams.Q, req.getParams().get(ChatpalParams.PARAM_TEXT));

        query.set(CommonParams.SORT, req.getParams().get(CommonParams.SORT));//TODO should be type aware?

        // TODO: Make this configurable
        if (docType == DocType.Message) {
            query.set(DisMaxParams.QF, "text^2 text_${lang}^1 decompose_text_${lang}^.5"
                    .replaceAll("\\$\\{lang}", language));
            query.add(HighlightParams.FIELDS, "text_${lang}"
                    .replaceAll("\\$\\{lang}", language));
            query.set(DisMaxParams.BF, "recip(ms(NOW,updated),3.6e-11,3,1)");
        }


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

        // param hierarchy
        final SolrParams defaultedQuery = SolrParams.wrapDefaults(
                // 1. req.getParams()
                query,
                SolrParams.wrapDefaults(
                        // 2. [type].defaults
                        defaultParams.get(docType),
                        // 3. defaults
                        defaults
                )
        );

        logger.debug("Chatpal query: {}", defaultedQuery);

        try (LocalSolrQueryRequest subRequest = new LocalSolrQueryRequest(req.getCore(), defaultedQuery)) {
            final SolrQueryResponse response = new SolrQueryResponse();
            super.handleRequestBody(subRequest, response);

            rsp.add(docType.getKey(), materializeResult(req.getSchema(), response, language));
        }
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

            if (highlighting != null) {
                final String id = String.valueOf(getFirstValue(doc, schema.getUniqueKeyField()));
                final NamedList<Object> highlights = highlighting.get(id);
                if (highlights != null) {
                    for (Map.Entry<String, Object> highlight : highlights) {
                        final String fieldName = highlight.getKey();
                        final Object fieldValue = highlight.getValue();

                        final String targetField = StringUtils.removeEnd(fieldName, "_" + language);

                        if (!rspContext.getReturnFields().wantsField(targetField)) continue;

                        if (isMultiValueFiled(schema, targetField)) {
                            doc.setField(targetField, fieldValue);
                        } else if (fieldValue != null) {
                            if (fieldValue.getClass().isArray()) {
                                final Object firstVal = ((Object[]) fieldValue)[0];
                                doc.setField(targetField, firstVal);
                            } else if (fieldValue instanceof Collection) {
                                Collection c = (Collection) fieldValue;
                                if (!c.isEmpty()) {
                                    doc.setField(targetField, c.iterator().next());
                                }
                            } else {
                                doc.setField(targetField, fieldValue);
                            }
                        }

                    }
                }
            }

            for (String fName : new HashSet<>(doc.getFieldNames())) {
                if (!rspContext.getReturnFields().wantsField(fName)) {
                    doc.removeFields(fName);
                }
            }

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

    @SuppressWarnings("unused")
    private void appendACLFilter(ModifiableSolrParams query, SolrQueryRequest req, SolrQueryResponse rsp, DocType docType) {
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

    private interface QueryAdapter {
        void adaptQuery(ModifiableSolrParams query, SolrQueryRequest req, SolrQueryResponse rsp, DocType docType);
    }
}
