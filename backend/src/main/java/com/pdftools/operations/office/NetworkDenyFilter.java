package com.pdftools.operations.office;

import com.pdftools.operations.OperationException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class NetworkDenyFilter {

    private static final short LOAD_WORD_ABSOLUTE = 0x20;
    private static final short JUMP_EQUAL = 0x15;
    private static final short JUMP_BITS_SET = 0x45;
    private static final short RETURN = 0x06;
    private static final int SECCOMP_DATA_ARCH_OFFSET = 4;
    private static final int SECCOMP_DATA_ARGUMENTS_OFFSET = 16;
    private static final int ADDRESS_FAMILY_UNIX = 1;
    private static final int SECCOMP_RETURN_ERRNO_PERMISSION = 0x00050001;
    private static final int SECCOMP_RETURN_ALLOW = 0x7fff0000;
    private static final int SECCOMP_RETURN_KILL_PROCESS = 0x80000000;

    private NetworkDenyFilter() {
    }

    static void write(Path path) {
        Syscalls syscalls = syscalls();
        ByteBuffer filter = ByteBuffer
            .allocate(14 * 8)
            .order(ByteOrder.nativeOrder());
        instruction(
            filter,
            LOAD_WORD_ABSOLUTE,
            0,
            0,
            SECCOMP_DATA_ARCH_OFFSET
        );
        instruction(filter, JUMP_EQUAL, 1, 0, syscalls.auditArchitecture());
        instruction(
            filter,
            RETURN,
            0,
            0,
            SECCOMP_RETURN_KILL_PROCESS
        );
        instruction(filter, LOAD_WORD_ABSOLUTE, 0, 0, 0);
        instruction(
            filter,
            JUMP_BITS_SET,
            0,
            1,
            syscalls.alternateAbiBit()
        );
        instruction(
            filter,
            RETURN,
            0,
            0,
            SECCOMP_RETURN_KILL_PROCESS
        );
        instruction(filter, JUMP_EQUAL, 5, 0, syscalls.setSessionId());
        instruction(filter, JUMP_EQUAL, 4, 0, syscalls.setProcessGroup());
        instruction(filter, JUMP_EQUAL, 1, 0, syscalls.socket());
        instruction(filter, JUMP_EQUAL, 0, 3, syscalls.socketPair());
        instruction(
            filter,
            LOAD_WORD_ABSOLUTE,
            0,
            0,
            SECCOMP_DATA_ARGUMENTS_OFFSET
        );
        instruction(
            filter,
            JUMP_EQUAL,
            1,
            0,
            ADDRESS_FAMILY_UNIX
        );
        instruction(
            filter,
            RETURN,
            0,
            0,
            SECCOMP_RETURN_ERRNO_PERMISSION
        );
        instruction(filter, RETURN, 0, 0, SECCOMP_RETURN_ALLOW);
        try {
            Files.write(path, filter.array());
        } catch (IOException exception) {
            throw new OperationException(
                "OFFICE_SANDBOX_SETUP_FAILED",
                "The Office converter network filter could not be created",
                exception
            );
        }
    }

    private static void instruction(
            ByteBuffer buffer,
            short code,
            int jumpTrue,
            int jumpFalse,
            int value) {
        buffer.putShort(code);
        buffer.put((byte) jumpTrue);
        buffer.put((byte) jumpFalse);
        buffer.putInt(value);
    }

    private static Syscalls syscalls() {
        String architecture = System.getProperty("os.arch")
            .toLowerCase(Locale.ROOT);
        return switch (architecture) {
            case "amd64", "x86_64" -> new Syscalls(
                0xC000003E,
                0x40000000,
                41,
                53,
                109,
                112
            );
            case "aarch64", "arm64" -> new Syscalls(
                0xC00000B7,
                0,
                198,
                199,
                154,
                157
            );
            default -> throw new OperationException(
                "OFFICE_SANDBOX_UNAVAILABLE",
                "Office conversion is unavailable on this CPU architecture"
            );
        };
    }

    private record Syscalls(
        int auditArchitecture,
        int alternateAbiBit,
        int socket,
        int socketPair,
        int setProcessGroup,
        int setSessionId
    ) {
    }
}
