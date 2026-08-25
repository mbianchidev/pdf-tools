package com.pdftools.operations.excelpdf;

import com.pdftools.operations.OperationException;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.util.AreaReference;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Set;

@Component
public class ExcelPlanFactory {

    private static final Set<String> PRINT_AREA_MODES = Set.of(
        "existing",
        "used",
        "custom"
    );
    private static final Set<String> ORIENTATIONS = Set.of(
        "preserve",
        "portrait",
        "landscape"
    );

    public ExcelPlan create(
            JsonNode options,
            SpreadsheetVersion spreadsheetVersion) {
        String mode = text(options, "printAreaMode", "existing");
        String orientation = text(
            options,
            "orientation",
            "preserve"
        );
        if (!PRINT_AREA_MODES.contains(mode)) {
            throw new OperationException(
                "INVALID_EXCEL_PRINT_AREA_MODE",
                "printAreaMode must be existing, used, or custom"
            );
        }
        if (!ORIENTATIONS.contains(orientation)) {
            throw new OperationException(
                "INVALID_EXCEL_ORIENTATION",
                "orientation must be preserve, portrait, or landscape"
            );
        }
        String printArea = "";
        if (mode.equals("custom")) {
            printArea = text(options, "printArea", "");
            if (printArea.isBlank()
                    || printArea.length() > 64
                    || printArea.contains("!")
                    || printArea.contains(",")) {
                throw invalidPrintArea();
            }
            try {
                if (!printArea.matches(
                        "^\\$?[A-Za-z]{1,3}\\$?[1-9]\\d*:"
                            + "\\$?[A-Za-z]{1,3}\\$?[1-9]\\d*$")
                        || !AreaReference.isContiguous(printArea)) {
                    throw invalidPrintArea();
                }
                AreaReference area = new AreaReference(
                    printArea,
                    spreadsheetVersion
                );
                if (!withinBounds(area, spreadsheetVersion)) {
                    throw invalidPrintArea();
                }
                printArea = area.formatAsString();
            } catch (IllegalArgumentException exception) {
                throw invalidPrintArea();
            }

        } else if (options.has("printArea")
                && !options.path("printArea").asText("").isBlank()) {
            throw new OperationException(
                "UNEXPECTED_EXCEL_PRINT_AREA",
                "printArea is only valid in custom mode"
            );
        }
        return new ExcelPlan(mode, printArea, orientation);
    }

    public SpreadsheetVersion spreadsheetVersion(String filename) {
        return filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xls")
            ? SpreadsheetVersion.EXCEL97
            : SpreadsheetVersion.EXCEL2007;
    }

    private boolean withinBounds(
            AreaReference area,
            SpreadsheetVersion version) {
        return area.getFirstCell().getRow() < version.getMaxRows()
            && area.getLastCell().getRow() < version.getMaxRows()
            && area.getFirstCell().getCol() < version.getMaxColumns()
            && area.getLastCell().getCol() < version.getMaxColumns();
    }

    private String text(
            JsonNode options,
            String field,
            String fallback) {
        JsonNode node = options.get(field);
        if (node == null) {
            return fallback;
        }
        if (!node.isTextual()) {
            throw new OperationException(
                "INVALID_EXCEL_OPTIONS",
                field + " must be a string"
            );
        }
        return node.asText().trim().toLowerCase(
            java.util.Locale.ROOT
        );
    }

    private OperationException invalidPrintArea() {
        return new OperationException(
            "INVALID_EXCEL_PRINT_AREA",
            "printArea must be one contiguous A1 range without a sheet name"
        );
    }

    public record ExcelPlan(
        String printAreaMode,
        String printArea,
        String orientation
    ) {
    }
}
