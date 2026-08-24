package com.pdftools.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecureMultipartTempDirectoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void securesDirectoryAndPurgesStaleFiles() throws Exception {
        Path multipartDirectory = temporaryDirectory.resolve("multipart");
        Files.createDirectory(multipartDirectory);
        Files.writeString(multipartDirectory.resolve("upload.tmp"), "secret");

        SecureMultipartTempDirectory.initialize(multipartDirectory);

        assertFalse(Files.exists(multipartDirectory.resolve("upload.tmp")));
        assertEquals(
            Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
            ),
            Files.getPosixFilePermissions(multipartDirectory)
        );
    }

    @Test
    void rejectsSymlinkDirectory() throws Exception {
        Path target = temporaryDirectory.resolve("target");
        Files.createDirectory(target);
        Path link = temporaryDirectory.resolve("multipart");
        Files.createSymbolicLink(link, target);

        assertThrows(
            IllegalStateException.class,
            () -> SecureMultipartTempDirectory.initialize(link)
        );
    }

    @Test
    void rejectsUnexpectedSubdirectoriesInsteadOfDeletingRecursively()
            throws Exception {
        Path multipartDirectory = temporaryDirectory.resolve("multipart");
        Files.createDirectory(multipartDirectory);
        Files.createDirectory(multipartDirectory.resolve("unexpected"));

        assertThrows(
            IllegalStateException.class,
            () -> SecureMultipartTempDirectory.initialize(multipartDirectory)
        );
    }
}
