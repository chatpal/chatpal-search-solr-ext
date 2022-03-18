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

public final class ChatpalParams {

    /**
     * THe basic Rocket.Chat search text
     */
    public static final String PARAM_TEXT = "text";
    /**
     * Allows to parse a Query using Solr syntax. If present this takes
     * preference over {@link #PARAM_TEXT}
     */
    public static final String PARAM_QUERY = "query";

    public static final String PARAM_LANG = "language";
    public static final String PARAM_ACL = "acl";
    public static final String PARAM_TYPE = "type";
    public static final String PARAM_START = CommonParams.START;
    public static final String PARAM_ROWS = CommonParams.ROWS;

    public static final String PARAM_EXCL_MSG = "excl.msg";
    public static final String PARAM_EXCL_ROOM = "excl.room";

    public static final String LEGACY_PARAM_SUFFIX = "[]";

    public static final String FIELD_MSG_ID = "id";
    public static final String FIELD_ROOM_ID = "rid";
    public static final String FIELD_ACL = FIELD_ROOM_ID;
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_SUGGESTION = "suggestion";
    public static final String LANG_NONE = "none";


    private ChatpalParams() {
    }

}
