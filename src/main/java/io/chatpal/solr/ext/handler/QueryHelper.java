package io.chatpal.solr.ext.handler;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.params.SolrParams;

public class QueryHelper {
    
    /**
     * Builds a query that requires one of the parsed terms by using the Solr terms
     * query parser
     * @param solrParams
     * @param param
     * @param field
     * @return
     */
    public static String buildTermsQuery(SolrParams solrParams, String param, String field){
        final String[] values = solrParams.getParams(param);
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
     * @param solrParams
     * @param param
     * @param field
     * @return
     */
    public static String buildOrFilter(SolrParams solrParams, String param, String field) {
        final String[] values = solrParams.getParams(param);
        if (values == null || values.length < 1) {
            return "-" + field + ":*";
        }

        return "{!q.op=OR}" + field + ":" +
                Arrays.stream(values)
                        .map(ClientUtils::escapeQueryChars)
                        .collect(Collectors.joining(" ", "(", ")"));
    }
}
