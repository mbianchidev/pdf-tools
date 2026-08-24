package com.pdftools.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

@Component
public class SecureMultipartTempDirectory {

    private static final Set<PosixFilePermission> REQUIRED_PERMISSIONS = Set.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE
    );

    public SecureMultipartTempDirectory(
            @Value("${spring.servlet.multipart.location}")
            String location) {
        initialize(Path.of(location));
    }

    static void initialize(Path configuredDirectory) {
        Path directory = configuredDirectory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
            verifyPrivateDirectory(directory);
            purgeStaleEntries(directory);
        } catch (IOException | UnsupportedOperationException exception) {
            throw new IllegalStateException(
                "Multipart temporary directory could not be secured",
                exception
            );
        }
    }

    private static void verifyPrivateDirectory(Path directory)
            throws IOException {
        if (Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(
                "Multipart temporary path must be a non-symlink directory"
            );
        }
        Files.setPosixFilePermissions(directory, REQUIRED_PERMISSIONS);
        Set<PosixFilePermission> actual = Files.getPosixFilePermissions(
            directory,
            LinkOption.NOFOLLOW_LINKS
        );
        if (!actual.equals(REQUIRED_PERMISSIONS)) {
            throw new IllegalStateException(
                "Multipart temporary directory must have mode 0700"
            );
        }
        String owner = Files.getOwner(
            directory,
            LinkOption.NOFOLLOW_LINKS
        ).getName();
        String currentUser = System.getProperty("user.name");
        if (!owner.equals(currentUser)
                && !owner.endsWith("\\" + currentUser)) {
            throw new IllegalStateException(
                "Multipart temporary directory must be owned by the process user"
            );
        }
    }

    private static void purgeStaleEntries(Path directory) throws IOException {
        boolean changed = false;
        try (DirectoryStream<Path> entries =
                Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalStateException(
                        "Multipart temporary directory contains an unexpected "
                            + "subdirectory"
                    );
                }
                Files.delete(entry);
                changed = true;
            }
        }
        if (changed) {
            try (FileChannel channel = FileChannel.open(
                    directory,
                    StandardOpenOption.READ)) {
                channel.force(true);
            }
        }
    }
}
