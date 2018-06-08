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
package io.chatpal.solr.ext.update.processor;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputField;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.apache.solr.update.processor.UpdateRequestProcessorFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IdGenerationFieldUpdateProcessorFactory extends UpdateRequestProcessorFactory {

    private static final String TARGET = "targetField", PATTERN = "pattern";

    private static final Pattern REGEX = Pattern.compile("(?<!\\\\)\\{([^:}]+)(?::([^}]*))?}");

    private String targetField, pattern;

    private boolean multiValued = false;

    @Override
    public void init(NamedList args) {
        super.init(args);

        targetField = Objects.toString(args.get(TARGET), null);
        if (targetField == null) {
            throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "Missing configuration: " + TARGET);
        }

        pattern = Objects.toString(args.get(PATTERN), null);
        if (pattern == null) {
            throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "Missing configuration: " + PATTERN);
        }
    }

    @Override
    public UpdateRequestProcessor getInstance(SolrQueryRequest req, SolrQueryResponse rsp, UpdateRequestProcessor next) {
        return new UpdateRequestProcessor(next) {
            @Override
            public void processAdd(AddUpdateCommand cmd) throws IOException {
                final SchemaField target = req.getSchema().getFieldOrNull(targetField);
                if (target != null) {
                    final Matcher matcher = REGEX.matcher(pattern);
                    final StringBuffer result = new StringBuffer();
                    while (matcher.find()) {
                        final String g0 = matcher.group(),
                                fName = matcher.group(1),
                                fallback = StringUtils.defaultString(matcher.group(2), g0);

                        SolrInputField field = cmd.solrDoc.getField(fName);
                        if (field != null) {
                            matcher.appendReplacement(result, String.valueOf(field.getFirstValue()));
                        } else {
                            matcher.appendReplacement(result, fallback);
                        }
                    }
                    matcher.appendTail(result);

                    if (target.multiValued() && multiValued) {
                        cmd.solrDoc.addField(target.getName(), result.toString());
                    } else {
                        cmd.solrDoc.setField(target.getName(), result.toString());
                    }
                }

                super.processAdd(cmd);
            }
        };
    }
}
