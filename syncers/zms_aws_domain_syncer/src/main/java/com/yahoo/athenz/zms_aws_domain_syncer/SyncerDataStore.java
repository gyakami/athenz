/*
 *
 *  * Copyright The Athenz Authors
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.yahoo.athenz.zms_aws_domain_syncer;

import com.yahoo.athenz.common.server.store.impl.ZMSFileChangeLogStoreCommon;
import com.yahoo.athenz.common.server.store.impl.ZMSFileChangeLogStoreSync;
import com.yahoo.athenz.zms.JWSDomain;
import com.yahoo.athenz.zms.SignedDomain;
import io.athenz.syncer.common.zms.Config;
import io.athenz.syncer.common.zms.DomainValidator;
import io.athenz.syncer.common.zms.ZmsReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncerDataStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncerDataStore.class);
    private final ZmsReader zmsReader;
    private final ZMSFileChangeLogStoreSync syncStore;

    public SyncerDataStore(ZmsReader zmsReader) {
        this.zmsReader = zmsReader;
        String rootDir = Config.getInstance().getConfigParam(Config.SYNC_CFG_PARAM_LOCAL_DOMAIN_ROOT);
        if (rootDir == null) {
            String rootPath = Config.getInstance().getConfigParam(Config.SYNC_CFG_PARAM_ROOT_PATH);
            rootDir = rootPath + "/var/athenz/syncer/data";
        }
        LOGGER.info("SyncerDataStore using root directory: {}", rootDir);
        ZMSFileChangeLogStoreCommon storeCommon = new ZMSFileChangeLogStoreCommon(rootDir);
        this.syncStore = new ZMSFileChangeLogStoreSync(storeCommon);
    }

    public boolean process() {
        LOGGER.info("Starting local file sync process...");
        boolean success = true;

        // 1. Process updates (JWS only)
        if (!processUpdates()) {
            LOGGER.error("Failed to process updates");
            success = false;
        }

        // 2. Process deletes
        if (!syncStore.processDeletes(zmsReader.getZmsClient())) {
            LOGGER.error("Failed to process deletes");
            success = false;
        }

        return success;
    }

    boolean processUpdates() {
        return syncStore.processUpdates(zmsReader.getZmsClient(), true, new ZMSFileChangeLogStoreSync.DomainValidator() {
            @Override
            public boolean validate(JWSDomain domain) {
                DomainValidator validator = zmsReader.getDomainValidator();
                if (!validator.validateJWSDomain(domain)) {
                    LOGGER.error("Validation failed for JWS domain");
                    return false;
                }
                return true;
            }

            @Override
            public boolean validate(SignedDomain domain) {
                // Not supported in this syncer configuration
                LOGGER.error("SignedDomain validation not supported");
                return false;
            }
        });
    }
}
