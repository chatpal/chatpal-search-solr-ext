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
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class QueryHelperTest {

    @Test
    public void buildTermsQuery() {

        Assert.assertThat("simple term query", QueryHelper.buildTermsQuery("foo", new String[]{"x1", "x2", "x3", "x4"}),
                CoreMatchers.is("{!terms f=foo}x1,x2,x3,x4"));
        Assert.assertThat("filter empty values", QueryHelper.buildTermsQuery("foo", new String[]{"x1", "", "x3", null, "x5", "x6"}),
                CoreMatchers.is("{!terms f=foo}x1,x3,x5,x6"));

        Assert.assertThat("empty term query", QueryHelper.buildTermsQuery("empty", new String[0]),
                CoreMatchers.is("{!terms f=empty}"));
        Assert.assertThat("null term query", QueryHelper.buildTermsQuery("null", null),
                CoreMatchers.is("{!terms f=null}"));

        try {
            QueryHelper.buildTermsQuery(null, new String[0]);
            Assert.fail("IllegalArgumentException expected");
        } catch (final Exception e) {
            Assert.assertThat("null-field", e, CoreMatchers.instanceOf(IllegalArgumentException.class));
        }
    }

    @Test
    public void buildOrFilter() {
        Assert.assertThat("simple OR", QueryHelper.buildOrFilter("foo", new String[]{"x1", "x2", "x3", "x4"}),
                CoreMatchers.is("{!q.op=OR}foo:(x1 x2 x3 x4)"));
        Assert.assertThat("null/escape OR", QueryHelper.buildOrFilter("foo", new String[]{"x1", "x\"2", "x3", null, "x5", "", "x7"}),
                CoreMatchers.is("{!q.op=OR}foo:(x1 x\\\"2 x3 x5 x7)"));

        Assert.assertThat("simple OR without field", QueryHelper.buildOrFilter(null, new String[]{"x1", "x2", "x3", "x4"}),
                CoreMatchers.is("{!q.op=OR}(x1 x2 x3 x4)"));
        Assert.assertThat("null/escape OR without filed", QueryHelper.buildOrFilter(null, new String[]{"x1", "x\"2", "x3", null, "x5", "", "x7"}),
                CoreMatchers.is("{!q.op=OR}(x1 x\\\"2 x3 x5 x7)"));

        Assert.assertThat("negative filter", QueryHelper.buildOrFilter("empty", new String[0]),
                CoreMatchers.is("-empty:*"));
        Assert.assertThat("negative filter", QueryHelper.buildOrFilter("null", null),
                CoreMatchers.is("-null:*"));

        Assert.assertThat("negative range", QueryHelper.buildOrFilter(null, new String[0]), CoreMatchers.is("-[* TO *]"));
        Assert.assertThat("negative range", QueryHelper.buildOrFilter(null, null), CoreMatchers.is("-[* TO *]"));

    }
    
    @Test
    public void cleanTextQuery() {
        Assert.assertThat("plain text", QueryHelper.cleanTextQuery("Test text"), CoreMatchers.is("Test text"));
        Assert.assertThat("parenthesis", QueryHelper.cleanTextQuery("Test (Junit)"), CoreMatchers.is("Test \\(Junit\\)"));
        Assert.assertThat("brackets", QueryHelper.cleanTextQuery("Test[1]"), CoreMatchers.is("Test\\[1\\]"));
        Assert.assertThat("asterisk", QueryHelper.cleanTextQuery("Test*"), CoreMatchers.is("Test*"));
        Assert.assertThat("ampersand", QueryHelper.cleanTextQuery("-Test* +Unit &fail"), CoreMatchers.is("-Test* +Unit \\&fail"));
        Assert.assertThat("tilde and colon", QueryHelper.cleanTextQuery("~1:3"), CoreMatchers.is("\\~1\\:3"));
    }

    @Test
    public void testGetMultiValueParam() {
        final String key = "key";
        final String[] values = {"value1", "value2", "value3"};
        final ModifiableSolrParams newParams = new ModifiableSolrParams();
        newParams.set(key, StringUtils.join(values, ","));

        Assert.assertThat("new param style", QueryHelper.getMultiValueParam(key, newParams), Matchers.arrayContaining(values));

        final ModifiableSolrParams legacyParams = new ModifiableSolrParams();
        legacyParams.add(key + ChatpalParams.LEGACY_PARAM_SUFFIX, values);
        Assert.assertThat("legacy param style", QueryHelper.getMultiValueParam(key, legacyParams), Matchers.arrayContaining(values));
    }
}