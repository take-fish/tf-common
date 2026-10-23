package com.fishmatrix.common.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fishmatrix.common.error.ApiException;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesLoadsAndDeletesWithoutUsingOriginalName() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(tempDir.toString(), 1024);
        byte[] content = "hello fishmatrix".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        StoredFile stored = storage.store(new ByteArrayInputStream(content), "../../危险.txt",
                "text/plain", "reader/private/7", 1024);

        assertTrue(stored.storageKey().startsWith("reader/private/7/"));
        assertTrue(stored.storageKey().endsWith(".txt"));
        assertFalse(stored.storageKey().contains("危险"));
        assertEquals(content.length, stored.size());
        assertArrayEquals(content, storage.load(stored.storageKey()).getInputStream().readAllBytes());

        storage.delete(stored.storageKey());
        assertFalse(storage.exists(stored.storageKey()));
    }

    @Test
    void rejectsOversizedFileAndLeavesNoResidue() {
        LocalFileStorage storage = new LocalFileStorage(tempDir.toString(), 1024);

        ApiException exception = assertThrows(ApiException.class, () -> storage.store(
                new ByteArrayInputStream(new byte[2048]), "a.txt", "text/plain", "tmp", 1024));

        assertEquals("PAYLOAD_TOO_LARGE", exception.code());
    }

    @Test
    void rejectsPathTraversal() {
        LocalFileStorage storage = new LocalFileStorage(tempDir.toString(), 1024);

        assertThrows(ApiException.class, () -> storage.load("../../etc/passwd"));
        assertThrows(ApiException.class, () -> storage.store(
                new ByteArrayInputStream(new byte[1]), "a.txt", "text/plain", "../outside", 1024));
    }
}
