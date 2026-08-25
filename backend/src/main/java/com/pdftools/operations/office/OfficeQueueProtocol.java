package com.pdftools.operations.office;

import com.pdftools.operations.OperationException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class OfficeQueueProtocol {

    static final String REQUEST = "request.bin";
    static final String INPUT = "input";
    static final String OUTPUT = "output.pdf";
    static final String READY = ".ready";
    static final String RUNNING = ".running";
    static final String CANCEL = ".cancel";
    static final String ACKNOWLEDGED = ".acknowledged";
    static final String COMPLETED = ".completed";
    static final String FAILED = ".failed";
    static final String PROGRESS = ".progress";
    static final String ABANDONED = ".abandoned";
    static final String DAEMON_READY = ".daemon-ready";

    private static final int VERSION_1 = 1;
    private static final int VERSION_2 = 2;

    private OfficeQueueProtocol() {
    }

    static void writeRequest(Path path, Request request) {
        if (!request.valid()) {
            throw protocolFailure(null);
        }
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path)))) {
            boolean legacyLayout = !request.type().equals("excel")
                && request.optionsJson().equals("{}");
            output.writeInt(legacyLayout ? VERSION_1 : VERSION_2);
            output.writeUTF(request.type());
            output.writeUTF(request.extension());
            if (!legacyLayout) {
                output.writeUTF(request.optionsJson());
            }
        } catch (IOException exception) {
            throw protocolFailure(exception);
        }
    }

    static Request readRequest(Path path) {
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            int version = input.readInt();
            if (version != VERSION_1 && version != VERSION_2) {
                throw protocolFailure(null);
            }
            Request request = new Request(
                input.readUTF(),
                input.readUTF(),
                version == VERSION_2 ? input.readUTF() : "{}"
            );
            if (input.read() != -1 || !request.valid()) {
                throw protocolFailure(null);
            }
            return request;
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw protocolFailure(exception);
        }
    }

    static void writeFailure(Path path, String code, String message) {
        Path temporary = temporary(path);
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(
                    Files.newOutputStream(temporary)))) {
            output.writeInt(VERSION_1);
            output.writeUTF(code);
            output.writeUTF(message);
        } catch (IOException exception) {
            throw protocolFailure(exception);
        }
        move(temporary, path);
    }

    static Failure readFailure(Path path) {
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            if (input.readInt() != VERSION_1) {
                throw protocolFailure(null);
            }
            Failure failure = new Failure(
                input.readUTF(),
                input.readUTF()
            );
            if (input.read() != -1
                    || !failure.code().matches("[A-Z0-9_]{1,96}")
                    || failure.message().isBlank()
                    || failure.message().length() > 1000) {
                throw protocolFailure(null);
            }
            return failure;
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw protocolFailure(exception);
        }
    }

    static void writeProgress(Path path, int progress) {
        if (progress < 0 || progress > 99) {
            throw protocolFailure(null);
        }
        Path temporary = temporary(path);
        try {
            Files.writeString(temporary, Integer.toString(progress));
        } catch (IOException exception) {
            throw protocolFailure(exception);
        }
        move(temporary, path);
    }

    static int readProgress(Path path) {
        try {
            int progress = Integer.parseInt(Files.readString(path).trim());
            if (progress < 0 || progress > 99) {
                throw protocolFailure(null);
            }
            return progress;
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException | NumberFormatException exception) {
            throw protocolFailure(exception);
        }
    }

    static void marker(Path path) {
        Path temporary = temporary(path);
        try {
            Files.write(temporary, new byte[]{1});
        } catch (IOException exception) {
            throw protocolFailure(exception);
        }
        move(temporary, path);
    }

    static void move(Path source, Path destination) {
        try {
            try {
                Files.move(
                    source,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                    source,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException exception) {
            throw protocolFailure(exception);
        }
    }

    private static Path temporary(Path path) {
        return path.resolveSibling(path.getFileName() + ".tmp");
    }

    private static OperationException protocolFailure(Throwable cause) {
        return new OperationException(
            "OFFICE_QUEUE_PROTOCOL_ERROR",
            "The Office converter queue returned invalid state",
            cause
        );
    }

    record Request(
        String type,
        String extension,
        String optionsJson
    ) {
        private boolean valid() {
            return optionsJson != null
                && !optionsJson.isBlank()
                && optionsJson.length() <= 16_384
                && switch (type) {
                case "word" -> extension.matches("\\.docx?")
                    && optionsJson.equals("{}");
                case "powerpoint" -> extension.matches("\\.pptx?")
                    && optionsJson.equals("{}");
                case "excel" -> extension.matches("\\.xlsx?")
                    && validExcelOptions();
                default -> false;
            };
        }

        private boolean validExcelOptions() {
            try {
                return new tools.jackson.databind.ObjectMapper()
                    .readTree(optionsJson)
                    .isObject();
            } catch (RuntimeException exception) {
                return false;
            }
        }
    }

    record Failure(String code, String message) {
    }
}
