package com.pdftools.operations.compress;

import com.pdftools.operations.OperationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Locale;

@Component
public class CompressPdfPlanFactory {

    public CompressPdfPlan create(JsonNode options) {
        JsonNode modeNode = options.get("mode");
        String mode = "recommended";
        if (modeNode != null) {
            if (!modeNode.isTextual()) {
                throw invalidMode();
            }
            mode = modeNode.asText().trim().toLowerCase(Locale.ROOT);
        }
        try {
            return new CompressPdfPlan(CompressionMode.fromOption(mode));
        } catch (IllegalArgumentException exception) {
            throw invalidMode();
        }
    }

    private OperationException invalidMode() {
        return new OperationException(
            "INVALID_COMPRESSION_MODE",
            "mode must be low, recommended, or extreme"
        );
    }

    public enum CompressionMode {
        LOW("low"),
        RECOMMENDED("recommended"),
        EXTREME("extreme");

        private final String option;

        CompressionMode(String option) {
            this.option = option;
        }

        public String option() {
            return option;
        }

        static CompressionMode fromOption(String value) {
            for (CompressionMode mode : values()) {
                if (mode.option.equals(value)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unknown compression mode");
        }
    }

    public record CompressPdfPlan(CompressionMode mode) {
    }
}
