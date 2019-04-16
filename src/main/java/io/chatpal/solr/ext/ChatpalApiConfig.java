/*
 * Copyright 2020 Redlink GmbH
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
package io.chatpal.solr.ext;

import io.chatpal.solr.ext.util.SolrConfigUtils;
import java.util.Map;
import org.apache.solr.common.MapSerializable;
import org.apache.solr.core.SolrConfig;

public class ChatpalApiConfig implements MapSerializable {

    private static final String XML_ROOT = "/config/chatpal/";

    private Search generalSearch = new Search();

    private FileSearch fileSearch = new FileSearch();

    public Search getGeneralSearch() {
        return generalSearch;
    }

    public FileSearch getFileSearch() {
        return fileSearch;
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("generalSearch", generalSearch);
        map.put("fileSearch", fileSearch);
        return map;
    }

    @Override
    public String toString() {
        return "ChatpalApiConfig{" +
                generalSearch +
                ", " + fileSearch +
                '}';
    }

    public static ChatpalApiConfig fromSolrConfig(SolrConfig solrConfig) {
        final ChatpalApiConfig chatpal = new ChatpalApiConfig();

        chatpal.generalSearch.enabled = solrConfig.getBool(XML_ROOT + "generalSearch/enabled", true);

        chatpal.fileSearch.enabled = solrConfig.getBool(XML_ROOT + "fileSearch/enabled", false);
        chatpal.fileSearch.maxFileSize = SolrConfigUtils.getLong(solrConfig, XML_ROOT + "fileSearch/maxFileSize",
                Math.round(20 * Math.pow(1024, 2)));

        return chatpal;
    }

    public static class Search implements MapSerializable {

        protected boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public Map<String, Object> toMap(Map<String, Object> map) {
            map.put("enabled", enabled);
            return map;
        }

        @Override
        public String toString() {
            return String.format("%s{enabled=%s}", getClass().getSimpleName(), enabled);
        }
    }

    public static class FileSearch extends Search {
        protected long maxFileSize = Math.round(20 * Math.pow(1024, 2));

        public long getMaxFileSize() {
            return maxFileSize;
        }

        @Override
        public Map<String, Object> toMap(Map<String, Object> map) {
            super.toMap(map);
            map.put("maxFileSize", maxFileSize);
            return map;
        }
    }

}
