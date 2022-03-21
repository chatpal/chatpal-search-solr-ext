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
package io.chatpal.solr.ext.update.processor;

import io.chatpal.solr.ext.ChatpalParams;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputField;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.apache.solr.update.processor.UpdateRequestProcessorFactory;

import java.io.IOException;
import java.util.Objects;

public class LanguageFieldUpdateProcessorFactory extends UpdateRequestProcessorFactory {

    private static final String SOURCE = "source";
    private static final String TARGET = "target";

    private String sourceField;
    private String targetFieldPattern;
    private boolean isPrefix;

    @Override
    public void init(NamedList args) {
        super.init(args);

        sourceField = Objects.toString(args.get(SOURCE), null);
        if (sourceField == null) {
            throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "Missing configuration: " + SOURCE);
        }

        targetFieldPattern = Objects.toString(args.get(TARGET), null);
        if (targetFieldPattern == null) {
            throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "Missing configuration: " + TARGET);
        }
        isPrefix = !targetFieldPattern.contains("*");
    }

    @Override
    public UpdateRequestProcessor getInstance(final SolrQueryRequest req, final SolrQueryResponse rsp,
                                              final UpdateRequestProcessor next) {
        final String lang = req.getParams().get(ChatpalParams.PARAM_LANG, ChatpalParams.LANG_NONE);

        return new UpdateRequestProcessor(next) {
            @Override
            public void processAdd(AddUpdateCommand cmd) throws IOException {
                final SolrInputField field = cmd.solrDoc.getField(sourceField);

                if (field != null) {
                    final String targetField;
                    if (isPrefix) {
                        targetField = targetFieldPattern + lang;
                    } else {
                        targetField = targetFieldPattern.replace("*", lang);
                    }

                    if (req.getSchema().getFieldOrNull(targetField) != null) {
                        cmd.solrDoc.setField(targetField, field.getValue());
                    }

                    cmd.solrDoc.removeField(sourceField);
                }

                super.processAdd(cmd);
            }
        };
    }

}
