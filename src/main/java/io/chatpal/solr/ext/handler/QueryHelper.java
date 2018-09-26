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

import io.chatpal.solr.ext.ChatpalParams;

import java.util.Arrays;
import java.util.stream.Collectors;

public class QueryHelper {

    private QueryHelper() { }

    /**
     * Builds a query that requires one of the parsed terms by using the Solr terms
     * query parser.
     * @param field the field-name of the terms query
     * @param values the values to query for ({@code OR})
     * @return field-query-string using Solrs terms query parser
     * @see <a href="https://lucene.apache.org/solr/guide/7_2/other-parsers.html#terms-query-parser">https://lucene.apache.org/solr/guide/7_2/other-parsers.html#terms-query-parser</a>
     */
    public static String buildTermsQuery(String field, String[] values){
        if (values == null || values.length < 1) {
            return "-" + field + ":*";
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

        return "{!q.op=OR}" + field + ":" +
                Arrays.stream(values)
                        .filter(StringUtils::isNotBlank)
                        .map(ClientUtils::escapeQueryChars)
                        .collect(Collectors.joining(" ", "(", ")"));
    }
    
    /**
     * Escapes <a href=
     * "https://www.google.com/?gws_rd=ssl#q=lucene+query+parser+syntax">Lucene
     * query parser syntax</a> not allowed in the
     * {@link ChatpalParams#PARAM_TEXT} parameter
     */
    public static String cleanTextQuery(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // These characters are part of the query syntax and NOT allowed in
            // the 'text' parameter.
            // So they must be escaped
            // NOTE: Chars in the query syntax that are allowed
            // || c == '*' || c == '?'
            if (c == '\\' || c == '+' || c == '-' || c == '!' || c == '(' || c == ')' || c == ':' || c == '^'
                    || c == '[' || c == ']' || c == '\"' || c == '{' || c == '}' || c == '~' || c == '|' || c == '&'
                    || c == ';' || c == '/' || Character.isWhitespace(c)) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

}
