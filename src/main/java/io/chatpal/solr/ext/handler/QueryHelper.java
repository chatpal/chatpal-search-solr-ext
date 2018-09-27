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
     * query parser.
     * @param field the field. MUST NOT be <code>null</code> nor blank
     * @param values the values
     * @return the terms filter
     * @throws IllegalArgumentException if <code>null</code> or blank is parsed as field
     * @see <a href="https://lucene.apache.org/solr/guide/7_2/other-parsers.html#terms-query-parser">https://lucene.apache.org/solr/guide/7_2/other-parsers.html#terms-query-parser</a>
     */
    public static String buildTermsQuery(String field, String[] values){
        if(StringUtils.isBlank(field)){
            throw new IllegalArgumentException("The parsed field MUST NOT be NULL nor blank");
        }

        //NOTE: we create an empty terms filter if no values are parsed
        if (values == null) {
            values = new String[0];
        }

        return String.format("{!terms f=%s}", field) +
                Arrays.stream(values)
                        .filter(StringUtils::isNotBlank)
                        .collect(Collectors.joining(","));
    }
    /**
     * Builds a query that requires one of the parsed terms by using a normal solr
     * OR query
     * @param field the field-name of the or query
     * @param values the values to query for ({@code OR})
     * @return field-query-string, connected with the {@code OR} operator
     */
    public static String buildOrFilter(String field, String[] values) {
        if (values == null || values.length < 1) {
            return "-" + field + ":*";
        }

        return "{!q.op=OR}" +
                //NOTE: a NULL or blank field denotes to the configured 'df'
                (StringUtils.isBlank(field) ? "" : (field + ":")) +
                Arrays.stream(values)
                        .filter(StringUtils::isNotBlank)
                        .map(ClientUtils::escapeQueryChars)
                        .collect(Collectors.joining(" ", "(", ")"));
    }
}
