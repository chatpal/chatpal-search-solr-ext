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

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.util.ClientUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

public class QueryHelper {

    private QueryHelper() { }

    /**
     * Builds a query that requires one of the parsed terms by using the Solr terms
     * query parser
     * @param field
     * @param values
     * @return
     */
    public static String buildTermsQuery(String field, String[] values){
        if (values == null || values.length < 1) {
            return "-" + field + ":*";
        }
        return String.format("{!terms f=%s}", field) +
                Arrays.stream(values)
                        .filter(StringUtils::isNoneBlank)
                        .collect(Collectors.joining(","));
    }
    /**
     * Builds a query that requires one of the parsed terms by using a normal solr
     * OR query
     * @param field
     * @param values
     * @return
     */
    public static String buildOrFilter(String field, String[] values) {
        if (values == null || values.length < 1) {
            return "-" + field + ":*";
        }

        return "{!q.op=OR}" + field + ":" +
                Arrays.stream(values)
                        .map(ClientUtils::escapeQueryChars)
                        .collect(Collectors.joining(" ", "(", ")"));
    }
}