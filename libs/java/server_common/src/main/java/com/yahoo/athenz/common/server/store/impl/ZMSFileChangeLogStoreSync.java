/*
 *  Copyright The Athenz Authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.yahoo.athenz.common.server.store.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.athenz.zms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * Common logic for syncing domain data from ZMS to local file storage.
 * This class abstracts the "fetch, validate, save" loop used by both ZTS and Syncers.
 */
public class ZMSFileChangeLogStoreSync {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZMSFileChangeLogStoreSync.class);

    private final ZMSFileChangeLogStoreCommon storeCommon;
    private final ObjectMapper jsonMapper;
    private final Base64.Decoder base64Decoder;

    public interface DomainValidator {
        boolean validate(JWSDomain domain);
        boolean validate(SignedDomain domain);
    }

    public ZMSFileChangeLogStoreSync(ZMSFileChangeLogStoreCommon storeCommon) {
        this.storeCommon = storeCommon;
        this.jsonMapper = new ObjectMapper();
        this.jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.base64Decoder = Base64.getUrlDecoder();
    }

    public boolean processUpdates(ZMSClient zmsClient, boolean jwsDomainSupport, DomainValidator validator) {
        StringBuilder lastModTimeBuffer = new StringBuilder();

        if (jwsDomainSupport) {
            return processJWSDomainUpdates(zmsClient, validator, lastModTimeBuffer);
        } else {
            return processSignedDomainUpdates(zmsClient, validator, lastModTimeBuffer);
        }
    }

    private boolean processJWSDomainUpdates(ZMSClient zmsClient, DomainValidator validator, StringBuilder lastModTimeBuffer) {
        List<JWSDomain> updatedDomains = storeCommon.getUpdatedJWSDomains(zmsClient, lastModTimeBuffer);

        if (updatedDomains == null) {
            return false;
        }

        boolean result = true;
        if (!updatedDomains.isEmpty()) {
            result = false;
            for (JWSDomain domain : updatedDomains) {
                if (processJWSDomain(domain, validator)) {
                    result = true;
                }
            }
        }

        if (result && lastModTimeBuffer.length() > 0) {
            storeCommon.setLastModificationTimestamp(lastModTimeBuffer.toString());
        }

        return true;
    }

    private boolean processJWSDomain(JWSDomain jwsDomain, DomainValidator validator) {
        if (!validator.validate(jwsDomain)) {
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
            storeCommon.removeLocalDomain(domainName);
            storeCommon.saveLocalDomain(domainName, jwsDomain);
            return true;
        }

        storeCommon.saveLocalDomain(domainName, jwsDomain);
        return true;
    }

    private boolean processSignedDomainUpdates(ZMSClient zmsClient, DomainValidator validator, StringBuilder lastModTimeBuffer) {
        SignedDomains updatedDomains = storeCommon.getUpdatedSignedDomains(zmsClient, lastModTimeBuffer);

        if (updatedDomains == null) {
            return false;
        }

        boolean result = true;
        List<SignedDomain> domains = updatedDomains.getDomains();
        if (domains != null && !domains.isEmpty()) {
            result = false;
            for (SignedDomain domain : domains) {
                if (processSignedDomain(domain, validator)) {
                    result = true;
                }
            }
        }

        if (result && lastModTimeBuffer.length() > 0) {
            storeCommon.setLastModificationTimestamp(lastModTimeBuffer.toString());
        }

        return true;
    }

    private boolean processSignedDomain(SignedDomain signedDomain, DomainValidator validator) {
        if (!validator.validate(signedDomain)) {
            return false;
        }

        DomainData domainData = signedDomain.getDomain();
        String domainName = domainData.getName();

        // Check if disabled
        if (domainData.getEnabled() == Boolean.FALSE) {
            storeCommon.removeLocalDomain(domainName);
            storeCommon.saveLocalDomain(domainName, signedDomain);
            return true;
        }

        storeCommon.saveLocalDomain(domainName, signedDomain);
        return true;
    }

    public boolean processDeletes(ZMSClient zmsClient) {
        Set<String> serverDomains = storeCommon.getServerDomainList(zmsClient);
        if (serverDomains == null) {
            LOGGER.error("Unable to fetch server domain list");
            return false;
        }

        List<String> localDomains = storeCommon.getLocalDomainList();
        for (String localDomain : localDomains) {
            if (!serverDomains.contains(localDomain)) {
                LOGGER.info("Deleting local domain: {}", localDomain);
                storeCommon.removeLocalDomain(localDomain);
            }
        }
        return true;
    }
}
