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
package io.chatpal.solr.ext;

import org.apache.solr.common.params.CommonParams;

public interface ChatpalParams {

    String PARAM_TEXT = "text";
    String PARAM_LANG = "language";
    String PARAM_ACL = "acl[]";
    String PARAM_TYPE = "type[]";
    String PARAM_START = CommonParams.START;
    String PARAM_ROWS = CommonParams.ROWS;

    String FIELD_ACL = "rid";
    String FIELD_TYPE = "type";
    String FIELD_SUGGESTION = "suggestion";
    String LANG_NONE = "none";
}
