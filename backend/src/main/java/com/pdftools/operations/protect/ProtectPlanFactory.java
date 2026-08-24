package com.pdftools.operations.protect;

import com.pdftools.operations.OperationException;
import org.springframework.stereotype.Component;
import org.apache.pdfbox.pdmodel.encryption.PdfBoxPasswordPreparation;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Component
public class ProtectPlanFactory {

    private static final int MAX_PASSWORD_BYTES = 127;
    private static final Set<String> PRINT_VALUES =
        Set.of("none", "low", "high");
    private static final Set<String> PERMISSION_FIELDS = Set.of(
        "print",
        "copy",
        "modify",
        "annotate",
        "fillForms",
        "accessibility",
        "assemble"
    );

    public void validateShape(JsonNode options) {
        String userPassword = password(
            options,
            "userPassword",
            "User password"
        );
        String ownerPassword = password(
            options,
            "ownerPassword",
            "Owner password"
        );
        if (userPassword.equals(ownerPassword)) {
            throw new OperationException(
                "PASSWORDS_MUST_DIFFER",
                "User and owner passwords must differ"
            );
        }
        permissions(options.get("permissions"));
    }

    public ProtectPlan create(JsonNode options) {
        validateShape(options);
        JsonNode permissions = options.get("permissions");
        return new ProtectPlan(
            password(options, "userPassword", "User password"),
            password(options, "ownerPassword", "Owner password"),
            permissionText(permissions, "print", "none"),
            permissionBoolean(permissions, "copy", false),
            permissionBoolean(permissions, "modify", false),
            permissionBoolean(permissions, "annotate", false),
            permissionBoolean(permissions, "fillForms", false),
            permissionBoolean(permissions, "accessibility", true),
            permissionBoolean(permissions, "assemble", false)
        );
    }

    private String password(
            JsonNode options,
            String field,
            String label) {
        JsonNode node = options.get(field);
        if (node == null || !node.isTextual() || node.asText().isEmpty()) {
            throw new OperationException(
                "PASSWORD_REQUIRED",
                label + " is required"
            );
        }
        String value = node.asText();
        if (value.chars().anyMatch(character ->
                character < 32 || character > 126)) {
            throw new OperationException(
                "INVALID_PASSWORD",
                label + " must contain printable ASCII characters only"
            );
        }
        String prepared;
        try {
            prepared = PdfBoxPasswordPreparation.prepareStored(value);
        } catch (IllegalArgumentException exception) {
            throw new OperationException(
                "INVALID_PASSWORD",
                label + " contains characters prohibited by PDF encryption"
            );
        }
        int bytes = prepared.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_PASSWORD_BYTES) {
            throw new OperationException(
                "PASSWORD_TOO_LONG",
                label + " must stay within "
                    + MAX_PASSWORD_BYTES
                    + " UTF-8 bytes"
            );
        }
        return prepared;
    }

    private void permissions(JsonNode permissions) {
        if (permissions == null) {
            return;
        }
        if (!permissions.isObject()) {
            throw invalidPermissions();
        }
        permissions.propertyNames().forEach(field -> {
            if (!PERMISSION_FIELDS.contains(field)) {
                throw invalidPermissions();
            }
        });
        String print = permissionText(
            permissions,
            "print",
            "none"
        );
        if (!PRINT_VALUES.contains(print)) {
            throw invalidPermissions();
        }
        for (String field : PERMISSION_FIELDS) {
            if (!field.equals("print")
                    && permissions.has(field)
                    && !permissions.get(field).isBoolean()) {
                throw invalidPermissions();
            }
        }
    }

    private String permissionText(
            JsonNode permissions,
            String field,
            String fallback) {
        if (permissions == null || !permissions.has(field)) {
            return fallback;
        }
        JsonNode value = permissions.get(field);
        if (!value.isTextual()) {
            throw invalidPermissions();
        }
        return value.asText().toLowerCase(Locale.ROOT);
    }

    private boolean permissionBoolean(
            JsonNode permissions,
            String field,
            boolean fallback) {
        return permissions == null || !permissions.has(field)
            ? fallback
            : permissions.get(field).asBoolean();
    }

    private OperationException invalidPermissions() {
        return new OperationException(
            "INVALID_PERMISSIONS",
            "permissions contains unsupported fields or values"
        );
    }

    public record ProtectPlan(
        String userPassword,
        String ownerPassword,
        String print,
        boolean copy,
        boolean modify,
        boolean annotate,
        boolean fillForms,
        boolean accessibility,
        boolean assemble
    ) {
    }
}
