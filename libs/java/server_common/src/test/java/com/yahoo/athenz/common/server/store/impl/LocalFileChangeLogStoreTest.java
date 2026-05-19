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

import com.yahoo.athenz.CommonTestUtils;
import com.yahoo.athenz.common.server.util.FilesHelper;
import com.yahoo.rdl.JSON;
import com.yahoo.rdl.Struct;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.testng.Assert.*;

public class LocalFileChangeLogStoreTest {

    private File tempDir;

    @AfterMethod
    public void shutdown() {
        CommonTestUtils.deleteDirectory(tempDir);
    }

    @Test
    public void testCreateDirectoryWithOwnerOnlyPermissions() throws IOException {
        tempDir = Files.createTempDirectory("local_file_clog_store").toFile();
        File cacheDir = new File(tempDir, "cache");

        LocalFileChangeLogStore store = new LocalFileChangeLogStore(cacheDir.getAbsolutePath(), "TestStore");

        assertTrue(store.getRootDir().exists());
        assertTrue(store.getRootDir().isDirectory());
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(cacheDir.toPath());
        assertTrue(perms.contains(PosixFilePermission.OWNER_READ));
        assertTrue(perms.contains(PosixFilePermission.OWNER_WRITE));
        assertTrue(perms.contains(PosixFilePermission.OWNER_EXECUTE));
        assertFalse(perms.contains(PosixFilePermission.GROUP_READ));
        assertFalse(perms.contains(PosixFilePermission.OTHERS_READ));
    }

    @Test
    public void testSaveGetAndDeleteDomainFile() throws IOException {
        tempDir = Files.createTempDirectory("local_file_clog_store").toFile();
        LocalFileChangeLogStore store = new LocalFileChangeLogStore(tempDir.getAbsolutePath(), "TestStore");

        Struct data = new Struct();
        data.put("key", "value");
        store.put("athenz", JSON.bytes(data));

        Struct storedData = store.get("athenz", Struct.class);
        assertNotNull(storedData);
        assertEquals(storedData.getString("key"), "value");

        store.delete("athenz");
        assertNull(store.get("athenz", Struct.class));
    }

    @Test
    public void testGetLocalDomainListSkipsHiddenFiles() throws IOException {
        tempDir = Files.createTempDirectory("local_file_clog_store").toFile();
        LocalFileChangeLogStore store = new LocalFileChangeLogStore(tempDir.getAbsolutePath(), "TestStore");

        Files.write(new File(tempDir, "athenz").toPath(), "{}".getBytes());
        Files.write(new File(tempDir, ".lastModTime").toPath(), "{}".getBytes());

        List<String> domains = store.getLocalDomainList();
        assertEquals(domains.size(), 1);
        assertEquals(domains.get(0), "athenz");
    }

    @Test
    public void testSaveRetrieveAndDeleteLastModificationTime() throws IOException {
        tempDir = Files.createTempDirectory("local_file_clog_store").toFile();
        LocalFileChangeLogStore store = new LocalFileChangeLogStore(tempDir.getAbsolutePath(), "TestStore");

        store.saveLastModificationTime("12345");
        assertEquals(store.retrieveLastModificationTime(), "12345");
        assertTrue(new File(tempDir, LocalFileChangeLogStore.LAST_MOD_FNAME).exists());

        store.saveLastModificationTime(null);
        assertNull(store.retrieveLastModificationTime());
        assertFalse(new File(tempDir, LocalFileChangeLogStore.LAST_MOD_FNAME).exists());
    }

    @Test
    public void testClearDomainFilesSkipsHiddenFiles() throws IOException {
        tempDir = Files.createTempDirectory("local_file_clog_store").toFile();
        LocalFileChangeLogStore store = new LocalFileChangeLogStore(tempDir.getAbsolutePath(), "TestStore");

        File domainFile = new File(tempDir, "athenz");
        File hiddenFile = new File(tempDir, ".lastModTime");
        Files.write(domainFile.toPath(), "{}".getBytes());
        Files.write(hiddenFile.toPath(), "{}".getBytes());

        store.clearDomainFiles();

        assertFalse(domainFile.exists());
        assertTrue(hiddenFile.exists());
        assertTrue(store.getLocalDomainList().isEmpty());
    }

    @Test
    public void testMalformedJsonReturnsNull() throws IOException {
        tempDir = Files.createTempDirectory("local_file_clog_store").toFile();
        LocalFileChangeLogStore store = new LocalFileChangeLogStore(tempDir.getAbsolutePath(), "TestStore");

        Files.write(new File(tempDir, "athenz").toPath(), "invalid-json".getBytes());
        Files.write(new File(tempDir, LocalFileChangeLogStore.LAST_MOD_FNAME).toPath(), "invalid-json".getBytes());

        assertNull(store.get("athenz", Struct.class));
        assertNull(store.retrieveLastModificationTime());
    }

    @Test
    public void testJsonValueAsBytes() throws IOException {
        tempDir = Files.createTempDirectory("local_file_clog_store").toFile();
        LocalFileChangeLogStore store = new LocalFileChangeLogStore(tempDir.getAbsolutePath(), "TestStore");

        Struct data = new Struct();
        data.put("key", "value");
        assertNotNull(store.jsonValueAsBytes(data));

        assertNull(store.jsonValueAsBytes(new UnserializableValue()));
    }

    @Test
    public void testWriteFailureThrowsRuntimeException() throws IOException {
        tempDir = Files.createTempDirectory("local_file_clog_store").toFile();
        LocalFileChangeLogStore store = new LocalFileChangeLogStore(tempDir.getAbsolutePath(), "TestStore");

        FilesHelper filesHelper = Mockito.mock(FilesHelper.class);
        Mockito.when(filesHelper.write(any(), any())).thenThrow(new IOException("write error"));
        store.setFilesHelper(filesHelper);

        try {
            store.put("athenz", "{}".getBytes());
            fail();
        } catch (RuntimeException ex) {
            assertTrue(ex.getMessage().contains("unable to save file"));
        }
    }

    // Empty bean used to exercise JSON serialization failure handling.
    private static class UnserializableValue {
    }

}
