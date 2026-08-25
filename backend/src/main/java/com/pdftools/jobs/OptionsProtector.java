package com.pdftools.jobs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

@Component
public class OptionsProtector {

    private static final Logger logger =
        LoggerFactory.getLogger(OptionsProtector.class);
    private static final String PREFIX = "enc:v1:";
    private static final byte[] AAD =
        "pdf-tools-job-options-v1".getBytes(StandardCharsets.US_ASCII);
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public OptionsProtector(
            @Value("${pdf.jobs.options-encryption-key:}")
            String configuredKey,
            @Value("${pdf.jobs.options-key-file}")
            String keyFile) {
        this(loadKey(configuredKey, Path.of(keyFile)));
    }

    OptionsProtector(byte[] keyBytes) {
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException(
                "Options encryption key must contain 32 bytes"
            );
        }
        this.key = new SecretKeySpec(keyBytes.clone(), "AES");
    }

    public String protect(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                new GCMParameterSpec(TAG_BITS, nonce)
            );
            cipher.updateAAD(AAD);
            byte[] ciphertext = cipher.doFinal(
                plaintext.getBytes(StandardCharsets.UTF_8)
            );
            byte[] payload = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(
                ciphertext,
                0,
                payload,
                nonce.length,
                ciphertext.length
            );
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                "Sensitive job options could not be encrypted",
                exception
            );
        }
    }

    public String unprotect(String stored) {
        if (!stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(
                stored.substring(PREFIX.length())
            );
            if (payload.length <= NONCE_BYTES) {
                throw new IllegalArgumentException(
                    "Encrypted options payload is truncated"
                );
            }
            byte[] nonce = java.util.Arrays.copyOfRange(
                payload,
                0,
                NONCE_BYTES
            );
            byte[] ciphertext = java.util.Arrays.copyOfRange(
                payload,
                NONCE_BYTES,
                payload.length
            );
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                new GCMParameterSpec(TAG_BITS, nonce)
            );
            cipher.updateAAD(AAD);
            return new String(
                cipher.doFinal(ciphertext),
                StandardCharsets.UTF_8
            );
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                "Sensitive job options could not be decrypted",
                exception
            );
        }
    }

    private static byte[] loadKey(String configured, Path keyFile) {
        if (configured != null && !configured.isBlank()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(
                    configured.trim()
                );
                if (decoded.length != KEY_BYTES) {
                    throw new IllegalArgumentException(
                        "PDF_OPTIONS_ENCRYPTION_KEY must decode to 32 bytes"
                    );
                }
                return decoded;
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                    "PDF options encryption key is invalid",
                    exception
                );
            }
        }
        Path absoluteKeyFile = keyFile.toAbsolutePath();
        Path temporary = null;
        try {
            Files.createDirectories(absoluteKeyFile.getParent());
            if (Files.exists(absoluteKeyFile, LinkOption.NOFOLLOW_LINKS)) {
                forceDirectory(absoluteKeyFile.getParent());
                return readPrivateKey(absoluteKeyFile);
            }
            byte[] generated = new byte[KEY_BYTES];
            new SecureRandom().nextBytes(generated);
            temporary = Files.createTempFile(
                absoluteKeyFile.getParent(),
                ".options-key-",
                ".tmp",
                PosixFilePermissions.asFileAttribute(requiredPermissions())
            );
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(generated);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.createLink(absoluteKeyFile, temporary);
            } catch (FileAlreadyExistsException exception) {
                forceDirectory(absoluteKeyFile.getParent());
                return readPrivateKey(absoluteKeyFile);
            }
            forceDirectory(absoluteKeyFile.getParent());
            return readPrivateKey(absoluteKeyFile);
        } catch (IOException exception) {
            throw new IllegalStateException(
                "PDF options encryption key could not be securely published; "
                    + "configure PDF_OPTIONS_ENCRYPTION_KEY",
                exception
            );
        } catch (UnsupportedOperationException exception) {
            throw new IllegalStateException(
                "The filesystem cannot securely store the options key; "
                    + "configure PDF_OPTIONS_ENCRYPTION_KEY",
                exception
            );
        } finally {
            if (temporary != null) {
                try {
                    if (Files.deleteIfExists(temporary)) {
                        forceDirectory(absoluteKeyFile.getParent());
                    }
                } catch (IOException exception) {
                    logger.warn(
                        "Could not remove temporary options key {}",
                        temporary,
                        exception
                    );
                }
            }
        }
    }

    private static byte[] readPrivateKey(Path keyFile) throws IOException {
        if (Files.isSymbolicLink(keyFile)
                || !Files.isRegularFile(
                    keyFile,
                    LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(
                "Stored PDF options key must be a regular non-symlink file"
            );
        }
        Set<PosixFilePermission> required = requiredPermissions();
        try {
            Files.setPosixFilePermissions(keyFile, required);
            Set<PosixFilePermission> actual =
                Files.getPosixFilePermissions(
                    keyFile,
                    LinkOption.NOFOLLOW_LINKS
                );
            if (!actual.equals(required)) {
                throw new IllegalStateException(
                    "Stored PDF options key must have mode 0600"
                );
            }
            String owner = Files.getOwner(
                keyFile,
                LinkOption.NOFOLLOW_LINKS
            ).getName();
            String currentUser = System.getProperty("user.name");
            if (!owner.equals(currentUser)
                    && !owner.endsWith("\\" + currentUser)) {
                throw new IllegalStateException(
                    "Stored PDF options key must be owned by the process user"
                );
            }
        } catch (UnsupportedOperationException exception) {
            throw new IllegalStateException(
                "Stored PDF options key permissions cannot be verified; "
                    + "configure PDF_OPTIONS_ENCRYPTION_KEY",
                exception
            );
        }
        byte[] key = Files.readAllBytes(keyFile);
        if (key.length != KEY_BYTES) {
            throw new IllegalStateException(
                "Stored PDF options encryption key must contain 32 bytes"
            );
        }
        return key;
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(
                directory,
                StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static Set<PosixFilePermission> requiredPermissions() {
        return Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
        );
    }
}
