package com.pdftools.jobs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionsProtectorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void encryptsAuthenticatesAndDecryptsSensitiveOptions() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        OptionsProtector protector = new OptionsProtector(key);
        String plaintext =
            "{\"userPassword\":\"open-secret\",\"ownerPassword\":\"owner-secret\"}";

        String first = protector.protect(plaintext);
        String second = protector.protect(plaintext);

        assertTrue(first.startsWith("enc:v1:"));
        assertFalse(first.contains("open-secret"));
        assertNotEquals(first, second);
        assertEquals(plaintext, protector.unprotect(first));
        assertEquals("{}", protector.unprotect("{}"));
    }

    @Test
    void rejectsTamperedCiphertext() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        OptionsProtector protector = new OptionsProtector(key);
        String protectedValue = protector.protect("{\"secret\":\"value\"}");
        String tampered = protectedValue.substring(
            0,
            protectedValue.length() - 2
        ) + "AA";

        assertThrows(
            IllegalStateException.class,
            () -> protector.unprotect(tampered)
        );
    }

    @Test
    void securelyPublishesOneKeyAcrossConcurrentStarts() throws Exception {
        Path keyFile = temporaryDirectory.resolve("options.key");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return new OptionsProtector("", keyFile.toString());
            });
            var second = executor.submit(() -> {
                start.await();
                return new OptionsProtector("", keyFile.toString());
            });
            start.countDown();
            OptionsProtector firstProtector = first.get();
            OptionsProtector secondProtector = second.get();
            String protectedValue = firstProtector.protect(
                "{\"secret\":\"value\"}"
            );
            assertEquals(
                "{\"secret\":\"value\"}",
                secondProtector.unprotect(protectedValue)
            );
        }
        assertEquals(32, Files.size(keyFile));
        assertEquals(
            Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
            ),
            Files.getPosixFilePermissions(keyFile)
        );
    }
}
