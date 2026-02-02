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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.athenz.common.server.store.impl.ZMSFileChangeLogStoreCommon;
import com.yahoo.athenz.zms.DomainData;
import com.yahoo.athenz.zms.JWSDomain;
import io.athenz.syncer.common.zms.Config;
import io.athenz.syncer.common.zms.ZmsReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.List;
import java.util.Set;

public class SyncerDataStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncerDataStore.class);
    private final ZmsReader zmsReader;
    private ZMSFileChangeLogStoreCommon changeLogStoreCommon;
    private final Base64.Decoder base64Decoder = Base64.getUrlDecoder();
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public SyncerDataStore(ZmsReader zmsReader) {
        this.zmsReader = zmsReader;
        String rootDir = Config.getInstance().getConfigParam(Config.SYNC_CFG_PARAM_LOCAL_DOMAIN_ROOT);
        if (rootDir == null) {
            String rootPath = Config.getInstance().getConfigParam(Config.SYNC_CFG_PARAM_ROOT_PATH);
            rootDir = rootPath + "/var/athenz/syncer/data";
        }
        LOGGER.info("SyncerDataStore using root directory: {}", rootDir);
        this.changeLogStoreCommon = new ZMSFileChangeLogStoreCommon(rootDir);

        jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public boolean process() {
        LOGGER.info("Starting local file sync process...");
        boolean success = true;

        // 1. Process updates (Always JWS)
        if (!processUpdates()) {
            LOGGER.error("Failed to process updates");
            success = false;
        }

        // 2. Process deletes
        if (!processDeletes()) {
            LOGGER.error("Failed to process deletes");
            success = false;
        }

        return success;
    }

    boolean processUpdates() {
        StringBuilder lastModTimeBuffer = new StringBuilder();
        List<JWSDomain> updatedDomains = changeLogStoreCommon.getUpdatedJWSDomains(zmsReader.getZmsClient(), lastModTimeBuffer);

        if (updatedDomains == null) {
            // If domains is null, it indicates a failure in fetching updates
            return false;
        }

        boolean result = true;
        if (!updatedDomains.isEmpty()) {
            result = false;
            for (JWSDomain domain : updatedDomains) {
                if (processJWSDomain(domain)) {
                    result = true;
                }
            }
        }

        if (result && lastModTimeBuffer.length() > 0) {
            changeLogStoreCommon.setLastModificationTimestamp(lastModTimeBuffer.toString());
        }

        return true;
    }

    boolean processJWSDomain(JWSDomain jwsDomain) {
        if (!validateJWSDomain(jwsDomain)) {
            return false;
        }

        DomainData domainData;
        try {
             byte[] payload = base64Decoder.decode(jwsDomain.getPayload());
             domainData = jsonMapper.readValue(payload, DomainData.class);
        } catch (Exception ex) {
            LOGGER.error("Unable to parse jws domain payload", ex);
            return false;
        }

        String domainName = domainData.getName();
        // Check if disabled
        if (domainData.getEnabled() == Boolean.FALSE) {
             changeLogStoreCommon.removeLocalDomain(domainName);
             changeLogStoreCommon.saveLocalDomain(domainName, jwsDomain);
             return true;
        }

        changeLogStoreCommon.saveLocalDomain(domainName, jwsDomain);
        return true;
    }

    boolean validateJWSDomain(JWSDomain jwsDomain) {
        boolean result = Crypto.validateJWSDocument(
                jwsDomain.getProtectedHeader(),
                jwsDomain.getPayload(),
                jwsDomain.getSignature(),
                keyId -> Config.getInstance().getZmsPublicKey(keyId)
        );

        if (!result) {
            LOGGER.error("Validation failed for JWS domain");
        }
        return result;
    }

    boolean processDeletes() {
        Set<String> serverDomains = changeLogStoreCommon.getServerDomainList(zmsReader.getZmsClient());
        if (serverDomains == null) {
            LOGGER.error("Unable to fetch server domain list");
            return false;
        }

        List<String> localDomains = changeLogStoreCommon.getLocalDomainList();
        for (String localDomain : localDomains) {
            if (!serverDomains.contains(localDomain)) {
                LOGGER.info("Deleting local domain: {}", localDomain);
                changeLogStoreCommon.removeLocalDomain(localDomain);
            }
        }
        return true;
    }
}
