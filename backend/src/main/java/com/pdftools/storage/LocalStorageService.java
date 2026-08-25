package com.pdftools.storage;

import com.pdftools.config.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestInputStream;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "pdf.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(StorageProperties properties) {
        this.root = properties.getLocalRoot().toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create local storage root " + root, exception);
        }
    }

    @Override
    public StoredObject put(
            String key,
            InputStream inputStream,
            long contentLength,
            String mediaType) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
        MessageDigest digest = sha256();

        try {
            long bytesWritten;
            try (DigestInputStream digestInput = new DigestInputStream(inputStream, digest);
                 OutputStream output = Files.newOutputStream(temporary)) {
                bytesWritten = digestInput.transferTo(output);
            }

            if (contentLength >= 0 && bytesWritten != contentLength) {
                throw new IOException(
                    "Storage length mismatch: expected " + contentLength + " bytes but wrote " + bytesWritten
                );
            }

            moveAtomically(temporary, target);
            return new StoredObject(
                key,
                bytesWritten,
                HexFormat.of().formatHex(digest.digest()),
                mediaType
            );
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public StoredResource get(String key) throws IOException {
        Path path = resolve(key);
        return new StoredResource(
            Files.newInputStream(path),
            Files.size(path),
            Files.probeContentType(path)
        );
    }

    @Override
    public List<StoredObjectInfo> list(String prefix) throws IOException {
        Path prefixPath = resolve(prefix);
        if (!Files.exists(prefixPath)) {
            return List.of();
        }
        try (var paths = Files.walk(prefixPath)) {
            return paths
                .filter(Files::isRegularFile)
                .map(path -> {
                    try {
                        String key = root.relativize(path)
                            .toString()
                            .replace(path.getFileSystem().getSeparator(), "/");
                        Instant lastModified = Files.getLastModifiedTime(path).toInstant();
                        return new StoredObjectInfo(key, lastModified);
                    } catch (IOException exception) {
                        throw new StorageListingException(exception);
                    }
                })
                .sorted(Comparator.comparing(StoredObjectInfo::key))
                .toList();
        } catch (StorageListingException exception) {
            throw exception.ioException;
        }
    }

    @Override
    public void delete(String key) throws IOException {
        Path path = resolve(key);
        Files.deleteIfExists(path);
        pruneEmptyParents(path.getParent());
    }

    private Path resolve(String key) {
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains("\0")) {
            throw new IllegalArgumentException("Invalid storage key");
        }

        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Storage key escapes the configured root");
        }
        return resolved;
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void pruneEmptyParents(Path directory) throws IOException {
        Path current = directory;
        while (current != null && current.startsWith(root) && !current.equals(root)) {
            try {
                Files.deleteIfExists(current);
            } catch (DirectoryNotEmptyException exception) {
                return;
            }
            current = current.getParent();
        }
    }

    private static final class StorageListingException extends RuntimeException {
        private final IOException ioException;

        private StorageListingException(IOException ioException) {
            super(ioException);
            this.ioException = ioException;
        }
    }
}
