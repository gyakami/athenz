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
import com.yahoo.athenz.common.server.util.FilesHelper;
import com.yahoo.athenz.zms.DomainAttributes;
import com.yahoo.rdl.Struct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LocalFileChangeLogStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalFileChangeLogStore.class);

    public static final String LAST_MOD_FNAME     = ".lastModTime";
    public static final String ATTR_LAST_MOD_TIME = "lastModTime";

    private final String storeName;

    File rootDir;
    ObjectMapper jsonMapper;
    FilesHelper filesHelper;

    public LocalFileChangeLogStore(final String rootDirectory, final String storeName) {
        this(rootDirectory, storeName, "cannot create specified root: ", "specified root is not a directory: ");
    }

    public LocalFileChangeLogStore(final String rootDirectory, final String storeName,
            final String createDirectoryErrorPrefix, final String invalidDirectoryErrorPrefix) {

        this.storeName = storeName;
        filesHelper = new FilesHelper();

        jsonMapper = new ObjectMapper();
        jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        rootDir = new File(rootDirectory);
        if (!rootDir.exists()) {
            if (!rootDir.mkdirs()) {
                error(createDirectoryErrorPrefix + rootDirectory);
            }
        } else if (!rootDir.isDirectory()) {
            error(invalidDirectoryErrorPrefix + rootDirectory);
        }

        Set<PosixFilePermission> perms = EnumSet.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
        setupFilePermissions(rootDir, perms);
    }

    public File getRootDir() {
        return rootDir;
    }

    public void setRootDir(File rootDir) {
        this.rootDir = rootDir;
    }

    public ObjectMapper getObjectMapper() {
        return jsonMapper;
    }

    public void setObjectMapper(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public FilesHelper getFilesHelper() {
        return filesHelper;
    }

    public void setFilesHelper(FilesHelper filesHelper) {
        this.filesHelper = filesHelper;
    }

    public void setupFilePermissions(File file, Set<PosixFilePermission> perms) {
        try {
            filesHelper.setPosixFilePermissions(file, perms);
        } catch (IOException ex) {
            error("unable to setup file with permissions: " + ex.getMessage());
        }
    }

    public void setupDomainFile(File file) {
        try {
            filesHelper.createEmptyFile(file);
            Set<PosixFilePermission> perms = EnumSet.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            setupFilePermissions(file, perms);
        } catch (IOException ex) {
            error("unable to setup domain file with permissions: " + ex.getMessage());
        }
    }

    public synchronized <T> T get(String name, Class<T> classType) {

        File file = new File(rootDir, name);
        if (!file.exists()) {
            return null;
        }

        try {
            return jsonMapper.readValue(file, classType);
        } catch (Exception ex) {
            LOGGER.error("Unable to retrieve file: {} error: {}", file.getAbsolutePath(), ex.getMessage());
        }
        return null;
    }

    public synchronized void put(String name, byte[] data) {

        File file = new File(rootDir, name);
        if (!file.exists()) {
            setupDomainFile(file);
        }

        try {
            filesHelper.write(file, data);
        } catch (IOException ex) {
            error("unable to save file: " + file.getPath() + " error: " + ex.getMessage());
        }
    }

    public synchronized void delete(String name) {
        File file = new File(rootDir, name);
        if (!file.exists()) {
            return;
        }

        try {
            filesHelper.delete(file);
        } catch (Exception exc) {
            error("cannot delete file or directory: " + name + " : exc: " + exc);
        }
    }

    public synchronized void clearDomainFiles() {
        String[] files = rootDir.list();
        if (files == null) {
            return;
        }
        for (String name : files) {
            if (name.charAt(0) == '.') {
                continue;
            }
            delete(name);
        }
    }

    public List<String> getLocalDomainList() {

        List<String> names = new ArrayList<>();
        String[] domains = rootDir.list();
        if (domains == null) {
            return names;
        }
        for (String name : domains) {
            if (name.charAt(0) != '.') {
                names.add(name);
            }
        }

        return names;
    }

    public Map<String, DomainAttributes> getLocalDomainAttributeList() {

        Map<String, DomainAttributes> domainAttrs = new HashMap<>();
        String[] domains = rootDir.list();
        if (domains == null) {
            return domainAttrs;
        }
        for (String name : domains) {
            if (name.charAt(0) != '.') {
                File file = new File(rootDir, name);
                domainAttrs.put(name, new DomainAttributes().setFetchTime(file.lastModified() / 1000));
            }
        }

        return domainAttrs;
    }

    public String retrieveLastModificationTime() {
        Struct lastModStruct = get(LAST_MOD_FNAME, Struct.class);
        if (lastModStruct == null) {
            return null;
        }
        return lastModStruct.getString(ATTR_LAST_MOD_TIME);
    }

    public void saveLastModificationTime(String lastModTime) {
        if (lastModTime == null) {
            delete(LAST_MOD_FNAME);
            return;
        }
        Struct lastModStruct = new Struct();
        lastModStruct.put(ATTR_LAST_MOD_TIME, lastModTime);
        byte[] data = jsonValueAsBytes(lastModStruct, Struct.class);
        if (data == null) {
            error("unable to serialize last modification time");
        }
        put(LAST_MOD_FNAME, data);
    }

    public byte[] jsonValueAsBytes(Object obj) {
        try {
            return jsonMapper.writeValueAsBytes(obj);
        } catch (Exception ex) {
            LOGGER.error("{}: unable to serialize json object: {}", storeName, ex.getMessage());
            return null;
        }
    }

    public byte[] jsonValueAsBytes(Object obj, Class<?> cls) {
        try {
            return jsonMapper.writerWithView(cls).writeValueAsBytes(obj);
        } catch (Exception ex) {
            LOGGER.error("Unable to serialize json object: {}", ex.getMessage());
            return null;
        }
    }

    private void error(String msg) {
        LOGGER.error(msg);
        throw new RuntimeException(storeName + ": " + msg);
    }
}
