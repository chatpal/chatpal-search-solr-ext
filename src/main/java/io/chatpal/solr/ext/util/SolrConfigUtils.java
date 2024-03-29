/*
 * Copyright (c) 2020-2022 Redlink GmbH.
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
package io.chatpal.solr.ext.util;

import org.apache.solr.core.Config;

public final class SolrConfigUtils {

    private SolrConfigUtils() { }

    public static long getLong(Config solrConfig, String path, long def) {
        try {
            return Long.parseLong(solrConfig.get(path, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
