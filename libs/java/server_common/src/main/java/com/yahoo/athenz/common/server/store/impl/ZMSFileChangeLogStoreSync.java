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

import com.yahoo.athenz.common.server.util.DomainValidator;
import com.yahoo.athenz.zms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Common logic for syncing domain data from ZMS to local file storage.
 * This class abstracts the "fetch, validate, save" loop used by both ZTS and Syncers.
 */
public class ZMSFileChangeLogStoreSync {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZMSFileChangeLogStoreSync.class);

    private final ZMSFileChangeLogStoreCommon storeCommon;

    public ZMSFileChangeLogStoreSync(ZMSFileChangeLogStoreCommon storeCommon) {
        this.storeCommon = storeCommon;
    }

    public boolean processUpdates(ZMSClient zmsClient, boolean jwsDomainSupport, DomainValidator validator, Function<String, PublicKey> keyProvider) {
        StringBuilder lastModTimeBuffer = new StringBuilder();

        if (jwsDomainSupport) {
            return processJWSDomainUpdates(zmsClient, validator, keyProvider, lastModTimeBuffer);
        } else {
            return processSignedDomainUpdates(zmsClient, validator, keyProvider, lastModTimeBuffer);
        }
    }

    private boolean processJWSDomainUpdates(ZMSClient zmsClient, DomainValidator validator, Function<String, PublicKey> keyProvider, StringBuilder lastModTimeBuffer) {
        List<JWSDomain> updatedDomains = storeCommon.getUpdatedJWSDomains(zmsClient, lastModTimeBuffer);

        if (updatedDomains == null) {
            return false;
        }

        boolean result = true;
        if (!updatedDomains.isEmpty()) {
            result = false;
            for (JWSDomain domain : updatedDomains) {
                if (processJWSDomain(domain, validator, keyProvider)) {
                    result = true;
                }
            }
        }

        if (result && lastModTimeBuffer.length() > 0) {
            storeCommon.setLastModificationTimestamp(lastModTimeBuffer.toString());
        }

        return true;
    }

    private boolean processJWSDomain(JWSDomain jwsDomain, DomainValidator validator, Function<String, PublicKey> keyProvider) {
        if (!validator.validateJWSDomain(jwsDomain, keyProvider)) {
            return false;
        }

        DomainData domainData = validator.getDomainData(jwsDomain);
        if (domainData == null) {
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

    private boolean processSignedDomainUpdates(ZMSClient zmsClient, DomainValidator validator, Function<String, PublicKey> keyProvider, StringBuilder lastModTimeBuffer) {
        SignedDomains updatedDomains = storeCommon.getUpdatedSignedDomains(zmsClient, lastModTimeBuffer);

        if (updatedDomains == null) {
            return false;
        }

        boolean result = true;
        List<SignedDomain> domains = updatedDomains.getDomains();
        if (domains != null && !domains.isEmpty()) {
            result = false;
            for (SignedDomain domain : domains) {
                if (processSignedDomain(domain, validator, keyProvider)) {
                    result = true;
                }
            }
        }

        if (result && lastModTimeBuffer.length() > 0) {
            storeCommon.setLastModificationTimestamp(lastModTimeBuffer.toString());
        }

        return true;
    }

    private boolean processSignedDomain(SignedDomain signedDomain, DomainValidator validator, Function<String, PublicKey> keyProvider) {
        if (!validator.validateSignedDomain(signedDomain, keyProvider)) {
            return false;
        }

        DomainData domainData = validator.getDomainData(signedDomain);
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
