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
package io.chatpal.solr.ext.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.chatpal.solr.ext.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportingLogger {

    private final Logger logger = LoggerFactory.getLogger(Constants.REPORT_LOGGER);

    public static ReportingLogger getInstance() {
        return new ReportingLogger();
    }

    public void logQuery(JsonLogMessage.QueryLog log) {
        log(log);
    }

    public void logPing(JsonLogMessage.IndexLog log) {
        log(log);
    }

    public void logSuggestion(JsonLogMessage.SuggestionLog log) {
        log(log);
    }

    private void log(JsonLogMessage.Log log) {
        try {
            log(log.toJsonString());
        } catch (JsonProcessingException e) {
            // ignore
        }
    }

    private void log(String jsonString) {
        logger.info(jsonString);
    }
}
