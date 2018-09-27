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

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class QueryHelperTest {

    @Test
    public void buildTermsQuery() {

        Assert.assertThat(QueryHelper.buildTermsQuery("foo", new String[]{"x1", "x2", "x3", "x4"}),
                CoreMatchers.is("{!terms f=foo}x1,x2,x3,x4"));
        Assert.assertThat(QueryHelper.buildTermsQuery("foo", new String[]{"x1", "", "x3", null, "x5", "x6"}),
                CoreMatchers.is("{!terms f=foo}x1,x3,x5,x6"));

        Assert.assertThat(QueryHelper.buildTermsQuery("empty", new String[0]),
                CoreMatchers.is("{!terms f=empty}"));
        Assert.assertThat(QueryHelper.buildTermsQuery("null", null),
                CoreMatchers.is("{!terms f=null}"));

        try {
            QueryHelper.buildTermsQuery(null, new String[0]);
            Assert.fail("IllegalArgumentException expected");
        } catch (final Exception e) {
            Assert.assertThat(e, CoreMatchers.instanceOf(IllegalArgumentException.class));
        }
    }

    @Test
    public void buildOrFilter() {
        Assert.assertThat(QueryHelper.buildOrFilter("foo", new String[]{"x1", "x2", "x3", "x4"}),
                CoreMatchers.is("{!q.op=OR}foo:(x1 x2 x3 x4)"));
        Assert.assertThat(QueryHelper.buildOrFilter("foo", new String[]{"x1", "x\"2", "x3", null, "x5", "", "x7"}),
                CoreMatchers.is("{!q.op=OR}foo:(x1 x\\\"2 x3 x5 x7)"));

        Assert.assertThat(QueryHelper.buildOrFilter(null, new String[]{"x1", "x2", "x3", "x4"}),
                CoreMatchers.is("{!q.op=OR}(x1 x2 x3 x4)"));
        Assert.assertThat(QueryHelper.buildOrFilter(null, new String[]{"x1", "x\"2", "x3", null, "x5", "", "x7"}),
                CoreMatchers.is("{!q.op=OR}(x1 x\\\"2 x3 x5 x7)"));

        Assert.assertThat(QueryHelper.buildOrFilter("empty", new String[0]),
                CoreMatchers.is("-empty:*"));
        Assert.assertThat(QueryHelper.buildOrFilter("null", null),
                CoreMatchers.is("-null:*"));

        Assert.assertThat(QueryHelper.buildOrFilter(null, new String[0]), CoreMatchers.is("-[* TO *]"));
        Assert.assertThat(QueryHelper.buildOrFilter(null, null), CoreMatchers.is("-[* TO *]"));

    }
    
    @Test
    public void cleanTextQuery() {
        Assert.assertThat(QueryHelper.cleanTextQuery("Test text"), CoreMatchers.is("Test text"));
        Assert.assertThat(QueryHelper.cleanTextQuery("Test (Junit)"), CoreMatchers.is("Test \\(Junit\\)"));
        Assert.assertThat(QueryHelper.cleanTextQuery("Test[1]"), CoreMatchers.is("Test\\[1\\]"));
        Assert.assertThat(QueryHelper.cleanTextQuery("Test*"), CoreMatchers.is("Test*"));
        Assert.assertThat(QueryHelper.cleanTextQuery("-Test* +Unit &fail"), CoreMatchers.is("-Test* +Unit \\&fail"));
        Assert.assertThat(QueryHelper.cleanTextQuery("~1:3"), CoreMatchers.is("\\~1\\:3"));
    }
}