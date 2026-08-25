package com.pdftools.operations.split;

import com.pdftools.operations.shared.pdf.PdfCosUtils;
import com.pdftools.operations.OperationException;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.contentstream.PDContentStream;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDTransparencyGroupAttributes;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.pattern.PDAbstractPattern;
import org.apache.pdfbox.pdmodel.graphics.pattern.PDShadingPattern;
import org.apache.pdfbox.pdmodel.graphics.pattern.PDTilingPattern;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PdfResourcePruner {

    private static final Set<COSName> SAFE_GROUP_COLOR_SPACES = Set.of(
        COSName.DEVICEGRAY,
        COSName.DEVICERGB,
        COSName.DEVICECMYK
    );
    private static final List<COSName> EXT_G_STATE_KEYS = List.of(
        COSName.TYPE,
        COSName.FONT,
        COSName.SMASK,
        COSName.getPDFName("LW"),
        COSName.getPDFName("LC"),
        COSName.getPDFName("LJ"),
        COSName.getPDFName("ML"),
        COSName.getPDFName("D"),
        COSName.getPDFName("RI"),
        COSName.getPDFName("OP"),
        COSName.getPDFName("op"),
        COSName.getPDFName("OPM"),
        COSName.getPDFName("BG"),
        COSName.getPDFName("BG2"),
        COSName.getPDFName("UCR"),
        COSName.getPDFName("UCR2"),
        COSName.getPDFName("TR"),
        COSName.getPDFName("TR2"),
        COSName.getPDFName("HT"),
        COSName.getPDFName("FL"),
        COSName.getPDFName("SM"),
        COSName.getPDFName("SA"),
        COSName.getPDFName("BM"),
        COSName.getPDFName("CA"),
        COSName.getPDFName("ca"),
        COSName.getPDFName("AIS"),
        COSName.getPDFName("TK")
    );
    private static final List<COSName> SHADING_KEYS = names(
        "ShadingType",
        "ColorSpace",
        "Background",
        "BBox",
        "AntiAlias",
        "Matrix",
        "Coords",
        "Domain",
        "Extend",
        "BitsPerCoordinate",
        "BitsPerComponent",
        "BitsPerFlag",
        "Decode",
        "VerticesPerRow",
        "Filter",
        "DecodeParms"
    );
    private static final COSName FUNCTIONS = COSName.getPDFName("Functions");
    private static final List<COSName> FUNCTION_KEYS = names(
        "FunctionType",
        "Domain",
        "Range",
        "Size",
        "BitsPerSample",
        "Order",
        "Encode",
        "Decode",
        "C0",
        "C1",
        "N",
        "Bounds",
        "Filter",
        "DecodeParms"
    );

    private final SplitProperties properties;
    private final PdfColorSpaceDependencies colorSpaceDependencies =
        new PdfColorSpaceDependencies();

    PdfResourcePruner(SplitProperties properties) {
        this.properties = properties;
    }

    private static List<COSName> names(String... values) {
        List<COSName> names = new ArrayList<>();
        for (String value : values) {
            names.add(COSName.getPDFName(value));
        }
        return List.copyOf(names);
    }

    Session openSession(
            PDDocument destination,
            Runnable cancellationCheck,
            Set<COSBase> forbiddenReferences,
            SplitDecodedBudget resourceScratchBudget,
            SplitStructureBudget resourceStructureBudget,
            ContentMaterializer materializer) {
        return new Session(
            destination,
            cancellationCheck,
            forbiddenReferences,
            resourceScratchBudget,
            resourceStructureBudget,
            materializer
        );
    }

    @FunctionalInterface
    interface ContentMaterializer {
        PDStream materialize(PDContentStream content) throws IOException;
    }

    final class Session {
        private final PDDocument destination;
        private final Runnable cancellationCheck;
        private final ContentMaterializer materializer;
        private final PdfResourceCopier resourceCloner;
        private final SplitStructureBudget resourceStructureBudget;
        private final Map<COSBase, IdentityHashMap<COSDictionary, PDFormXObject>>
            forms = new IdentityHashMap<>();
        private final Map<COSBase, IdentityHashMap<COSDictionary, PDTilingPattern>>
            tilingPatterns = new IdentityHashMap<>();

        private Session(
                PDDocument destination,
                Runnable cancellationCheck,
                Set<COSBase> forbiddenReferences,
                SplitDecodedBudget resourceScratchBudget,
                SplitStructureBudget resourceStructureBudget,
                ContentMaterializer materializer) {
            this.destination = destination;
            this.cancellationCheck = cancellationCheck;
            this.materializer = materializer;
            this.resourceStructureBudget = resourceStructureBudget;
            this.resourceCloner = new PdfResourceCopier(
                destination,
                properties,
                cancellationCheck,
                forbiddenReferences,
                resourceScratchBudget,
                resourceStructureBudget
            );
        }

        PDResources prune(
                PDContentStream content,
                PDResources source,
                Object recursionKey) throws IOException {
            return prune(
                content,
                source,
                recursionKey,
                new PdfResourceTraversal(
                    properties.getMaxResourceDepth(),
                    properties.getMaxResourceNodes(),
                    cancellationCheck,
                    resourceStructureBudget
                )
            );
        }

        COSDictionary sanitizeTransparencyGroup(COSDictionary source) {
            if (!COSName.TRANSPARENCY.equals(
                    source.getDictionaryObject(COSName.S))) {
                throw PdfCosUtils.unsupportedTransparencyGroup();
            }

            PDTransparencyGroupAttributes sourceAttributes =
                new PDTransparencyGroupAttributes(source);
            PDTransparencyGroupAttributes safeAttributes =
                new PDTransparencyGroupAttributes();
            COSDictionary safe = safeAttributes.getCOSObject();
            safe.setItem(COSName.TYPE, COSName.GROUP);
            if (sourceAttributes.isIsolated()) {
                safe.setBoolean(COSName.I, true);
            }
            if (sourceAttributes.isKnockout()) {
                safe.setBoolean(COSName.K, true);
            }
            if (source.containsKey(COSName.CS)) {
                COSBase colorSpace = PdfCosUtils.dereference(
                    source.getItem(COSName.CS)
                );
                if (!(colorSpace instanceof COSName name)
                        || !SAFE_GROUP_COLOR_SPACES.contains(name)) {
                    throw new OperationException(
                        "UNSUPPORTED_TRANSPARENCY_GROUP_COLOR_SPACE",
                        "Split does not support this transparency-group color space"
                    );
                }
                safe.setItem(COSName.CS, colorSpace);
            }
            return safe;
        }

        private PDResources prune(
                PDContentStream content,
                PDResources source,
                Object recursionKey,
                PdfResourceTraversal traversal) throws IOException {
            traversal.enter(recursionKey);
            try {
                PdfResourceUsage usage = resourceUsage(content);
                PDResources pruned = new PDResources();
                includeDefaultColorSpaces(source, usage);
                copyFonts(usage, source, pruned);
                copyColorSpaces(
                    usage.colorSpaces(),
                    source,
                    pruned,
                    traversal
                );
                for (COSBase inlineColorSpace : usage.inlineColorSpaces()) {
                    copyReferencedColorSpaces(
                        inlineColorSpace,
                        source,
                        pruned,
                        traversal
                    );
                }
                copyShadings(usage, source, pruned, traversal);
                copyProperties(usage, source, pruned);
                copyExtendedGraphicsStates(usage, source, pruned);
                copyPatterns(usage, source, pruned, traversal);
                copyXObjects(usage, source, pruned, traversal);
                return pruned;
            } finally {
                traversal.exit(recursionKey);
            }
        }

        private void includeDefaultColorSpaces(
                PDResources source,
                PdfResourceUsage usage) throws IOException {
            for (COSName defaultColorSpace : List.of(
                    COSName.DEFAULT_RGB,
                    COSName.DEFAULT_GRAY,
                    COSName.DEFAULT_CMYK)) {
                if (source.hasColorSpace(defaultColorSpace)) {
                    usage.colorSpaces().add(defaultColorSpace);
                }
            }
        }

        private void copyFonts(
                PdfResourceUsage usage,
                PDResources source,
                PDResources destinationResources) throws IOException {
            for (COSName name : PdfCosUtils.sortedNames(usage.fonts())) {
                COSDictionary fonts = source.getCOSObject()
                    .getCOSDictionary(COSName.FONT);
                if (fonts == null) {
                    continue;
                }
                PdfFontValidator.rejectType3Dictionary(fonts.getItem(name));
                PDFont font = source.getFont(name);
                PdfFontValidator.rejectType3(font);
                if (font != null) {
                    putClonedResource(
                        destinationResources,
                        COSName.FONT,
                        name,
                        font.getCOSObject()
                    );
                }
            }
        }

        private void copyShadings(
                PdfResourceUsage usage,
                PDResources source,
                PDResources destinationResources,
                PdfResourceTraversal traversal) throws IOException {
            for (COSName name : PdfCosUtils.sortedNames(usage.shadings())) {
                var shading = source.getShading(name);
                if (shading != null) {
                    copyReferencedColorSpaces(
                        shading.getCOSObject()
                            .getDictionaryObject(COSName.COLORSPACE),
                        source,
                        destinationResources,
                        traversal
                    );
                    putResource(
                        destinationResources,
                        COSName.SHADING,
                        name,
                        sanitizeShading(
                            shading.getCOSObject(),
                            traversal
                        )
                    );
                }
            }
        }

        private void copyProperties(
                PdfResourceUsage usage,
                PDResources source,
                PDResources destinationResources) throws IOException {
            for (COSName name : PdfCosUtils.sortedNames(usage.properties())) {
                if (source.getProperties(name) == null) {
                    continue;
                }
                COSBase type = source.getProperties(name)
                    .getCOSObject()
                    .getDictionaryObject(COSName.TYPE);
                if (COSName.OCG.equals(type) || COSName.OCMD.equals(type)) {
                    throw new OperationException(
                        "OPTIONAL_CONTENT_UNSUPPORTED",
                        "Split does not support optional-content resources"
                    );
                }
                putClonedResource(
                    destinationResources,
                    COSName.PROPERTIES,
                    name,
                    source.getProperties(name).getCOSObject()
                );
            }
        }

        private void copyExtendedGraphicsStates(
                PdfResourceUsage usage,
                PDResources source,
                PDResources destinationResources) throws IOException {
            for (COSName name : PdfCosUtils.sortedNames(
                    usage.extendedGraphicsStates())) {
                PDExtendedGraphicsState state = source.getExtGState(name);
                if (state == null) {
                    continue;
                }
                putClonedResource(
                    destinationResources,
                    COSName.EXT_G_STATE,
                    name,
                    sanitizeExtendedGraphicsState(state)
                );
            }
        }

        private COSDictionary sanitizeExtendedGraphicsState(
                PDExtendedGraphicsState state) throws IOException {
            PdfFontValidator.rejectType3(state);
            COSBase softMask = state.getCOSObject()
                .getDictionaryObject(COSName.SMASK);
            if (softMask != null && !COSName.NONE.equals(softMask)) {
                throw new OperationException(
                    "UNSUPPORTED_SOFT_MASK",
                    "Split does not support used graphics-state soft masks"
                );
            }
            COSDictionary safeState = new COSDictionary();
            for (COSName key : EXT_G_STATE_KEYS) {
                if (!COSName.SMASK.equals(key)
                        && state.getCOSObject().containsKey(key)) {
                    safeState.setItem(
                        key,
                        state.getCOSObject().getItem(key)
                    );
                }
            }
            return safeState;
        }

        private void copyPatterns(
                PdfResourceUsage usage,
                PDResources source,
                PDResources destinationResources,
                PdfResourceTraversal traversal) throws IOException {
            for (COSName name : PdfCosUtils.sortedNames(usage.patterns())) {
                PDAbstractPattern pattern = source.getPattern(name);
                if (pattern instanceof PDTilingPattern tilingPattern) {
                    putResource(
                        destinationResources,
                        COSName.PATTERN,
                        name,
                        cloneTilingPattern(
                            tilingPattern,
                            source,
                            traversal
                        ).getCOSObject()
                    );
                } else if (pattern instanceof PDShadingPattern shadingPattern) {
                    putResource(
                        destinationResources,
                        COSName.PATTERN,
                        name,
                        sanitizeShadingPattern(
                            shadingPattern,
                            source,
                            destinationResources,
                            traversal
                        )
                    );
                } else if (pattern != null) {
                    putClonedResource(
                        destinationResources,
                        COSName.PATTERN,
                        name,
                        pattern.getCOSObject()
                    );
                }
            }
        }

        private void copyXObjects(
                PdfResourceUsage usage,
                PDResources source,
                PDResources destinationResources,
                PdfResourceTraversal traversal) throws IOException {
            for (COSName name : PdfCosUtils.sortedNames(usage.xObjects())) {
                PDXObject xObject = source.getXObject(name);
                if (xObject != null
                        && xObject.getCOSObject().containsKey(COSName.OC)) {
                    throw new OperationException(
                        "OPTIONAL_CONTENT_UNSUPPORTED",
                        "Split does not support optional-content XObjects"
                    );
                }
                if (xObject instanceof PDFormXObject form) {
                    putResource(
                        destinationResources,
                        COSName.XOBJECT,
                        name,
                        cloneForm(form, source, traversal).getCOSObject()
                    );
                } else if (xObject instanceof PDImageXObject image) {
                    copyReferencedColorSpaces(
                        image.getCOSObject()
                            .getDictionaryObject(COSName.COLORSPACE),
                        source,
                        destinationResources,
                        traversal
                    );
                    putResource(
                        destinationResources,
                        COSName.XOBJECT,
                        name,
                        resourceCloner.cloneResource(image.getCOSObject())
                    );
                } else if (xObject != null) {
                    putClonedResource(
                        destinationResources,
                        COSName.XOBJECT,
                        name,
                        xObject.getCOSObject()
                    );
                }
            }
        }

        private COSDictionary sanitizeShadingPattern(
                PDShadingPattern pattern,
                PDResources sourceResources,
                PDResources destinationResources,
                PdfResourceTraversal traversal) throws IOException {
            COSDictionary source = pattern.getCOSObject();
            COSDictionary safe = new COSDictionary();
            safe.setItem(COSName.TYPE, COSName.PATTERN);
            safe.setInt(COSName.PATTERN_TYPE, 2);
            if (source.containsKey(COSName.MATRIX)) {
                safe.setItem(
                    COSName.MATRIX,
                    resourceCloner.cloneResource(
                        source.getItem(COSName.MATRIX)
                    )
                );
            }
            COSBase shading = PdfCosUtils.dereference(
                source.getItem(COSName.SHADING)
            );
            if (!(shading instanceof COSDictionary shadingDictionary)) {
                throw new OperationException(
                    "INVALID_PDF_RESOURCE",
                    "A shading pattern contains an invalid shading"
                );
            }
            copyReferencedColorSpaces(
                shadingDictionary.getDictionaryObject(COSName.COLORSPACE),
                sourceResources,
                destinationResources,
                traversal
            );
            safe.setItem(
                COSName.SHADING,
                sanitizeShading(shadingDictionary, traversal)
            );
            PDExtendedGraphicsState state =
                pattern.getExtendedGraphicsState();
            if (state != null) {
                safe.setItem(
                    COSName.EXT_G_STATE,
                    resourceCloner.cloneResource(
                        sanitizeExtendedGraphicsState(state)
                    )
                );
            }
            return safe;
        }

        private COSDictionary sanitizeShading(
                COSDictionary source,
                PdfResourceTraversal traversal) throws IOException {
            COSDictionary safe = resourceCloner.cloneSelectedDictionary(
                source,
                SHADING_KEYS
            );
            if (source.containsKey(COSName.FUNCTION)) {
                safe.setItem(
                    COSName.FUNCTION,
                    sanitizeFunction(
                        source.getItem(COSName.FUNCTION),
                        traversal
                    )
                );
            }
            return safe;
        }

        private COSBase sanitizeFunction(
                COSBase value,
                PdfResourceTraversal traversal) throws IOException {
            COSBase resolved = PdfCosUtils.dereference(value);
            if (resolved instanceof COSArray functions) {
                traversal.enter(functions);
                try {
                    COSArray safe = new COSArray();
                    for (int index = 0; index < functions.size(); index++) {
                        safe.add(sanitizeFunction(
                            functions.get(index),
                            traversal
                        ));
                    }
                    return safe;
                } finally {
                    traversal.exit(functions);
                }
            }
            if (!(resolved instanceof COSDictionary function)) {
                throw new OperationException(
                    "INVALID_PDF_RESOURCE",
                    "A shading contains an invalid function"
                );
            }
            traversal.enter(function);
            try {
                COSDictionary safe = resourceCloner.cloneSelectedDictionary(
                    function,
                    FUNCTION_KEYS
                );
                if (function.containsKey(FUNCTIONS)) {
                    COSBase functions = PdfCosUtils.dereference(
                        function.getItem(FUNCTIONS)
                    );
                    if (!(functions instanceof COSArray)) {
                        throw new OperationException(
                            "INVALID_PDF_RESOURCE",
                            "A stitching function must contain a function array"
                        );
                    }
                    safe.setItem(
                        FUNCTIONS,
                        sanitizeFunction(functions, traversal)
                    );
                }
                return safe;
            } finally {
                traversal.exit(function);
            }
        }

        private void copyColorSpaces(
                Set<COSName> roots,
                PDResources source,
                PDResources destinationResources,
                PdfResourceTraversal traversal) throws IOException {
            Set<COSName> copied = new HashSet<>();
            for (COSName root : PdfCosUtils.sortedNames(roots)) {
                copyColorSpace(
                    root,
                    source,
                    destinationResources,
                    traversal,
                    copied,
                    new HashSet<>()
                );
            }
        }

        private void copyColorSpace(
                COSName name,
                PDResources source,
                PDResources destinationResources,
                PdfResourceTraversal traversal,
                Set<COSName> copied,
                Set<COSName> visiting) throws IOException {
            if (copied.contains(name)) {
                return;
            }
            COSBase colorSpace = namedColorSpace(source, name);
            if (colorSpace == null) {
                return;
            }
            if (!visiting.add(name)) {
                throw new OperationException(
                    "CYCLIC_PDF_RESOURCE",
                    "The PDF contains cyclic color-space aliases"
                );
            }
            if (visiting.size() > properties.getMaxResourceDepth()) {
                throw new OperationException(
                    "PDF_RESOURCE_COMPLEXITY_LIMIT_EXCEEDED",
                    "PDF color-space aliases exceed the configured depth limit"
                );
            }
            try {
                Set<COSName> dependencies = colorSpaceDependencies.find(
                    colorSpace,
                    source,
                    traversal
                );
                for (COSName dependency : PdfCosUtils.sortedNames(
                        dependencies)) {
                    copyColorSpace(
                        dependency,
                        source,
                        destinationResources,
                        traversal,
                        copied,
                        visiting
                    );
                }
                putClonedResource(
                    destinationResources,
                    COSName.COLORSPACE,
                    name,
                    colorSpace
                );
                copied.add(name);
            } finally {
                visiting.remove(name);
            }
        }

        private void copyReferencedColorSpaces(
                COSBase root,
                PDResources source,
                PDResources destinationResources,
                PdfResourceTraversal traversal) throws IOException {
            Set<COSName> names = colorSpaceDependencies.find(
                root,
                source,
                traversal
            );
            copyColorSpaces(
                names,
                source,
                destinationResources,
                traversal
            );
        }

        private COSBase namedColorSpace(
                PDResources resources,
                COSName name) {
            COSDictionary colorSpaces = resources.getCOSObject()
                .getCOSDictionary(COSName.COLORSPACE);
            return colorSpaces == null ? null : colorSpaces.getItem(name);
        }

        private void putClonedResource(
                PDResources resources,
                COSName category,
                COSName name,
                COSBase value) throws IOException {
            putResource(
                resources,
                category,
                name,
                resourceCloner.cloneResource(value)
            );
        }

        private void putResource(
                PDResources resources,
                COSName category,
                COSName name,
                COSBase value) {
            resourceStructureBudget.consumeNode();
            COSDictionary resourceDictionary = resources.getCOSObject();
            COSDictionary categoryDictionary =
                resourceDictionary.getCOSDictionary(category);
            if (categoryDictionary == null) {
                categoryDictionary = new COSDictionary();
                resourceDictionary.setItem(category, categoryDictionary);
            }
            categoryDictionary.setItem(name, value);
        }

        private PDFormXObject cloneForm(
                PDFormXObject sourceForm,
                PDResources inheritedResources,
                PdfResourceTraversal traversal) throws IOException {
            COSBase key = sourceForm.getCOSObject();
            if (traversal.isActive(key)) {
                throw new OperationException(
                    "CYCLIC_PDF_RESOURCE",
                    "The PDF contains a cyclic form resource"
                );
            }
            PDResources sourceResources = sourceForm.getResources() == null
                ? inheritedResources
                : sourceForm.getResources();
            COSDictionary context = sourceResources.getCOSObject();
            IdentityHashMap<COSDictionary, PDFormXObject> contexts =
                forms.computeIfAbsent(key, ignored -> new IdentityHashMap<>());
            PDFormXObject cached = contexts.get(context);
            if (cached != null) {
                return cached;
            }
            PDFormXObject clonedForm = new PDFormXObject(
                materializer.materialize(sourceForm)
            );
            contexts.put(context, clonedForm);
            try {
                clonedForm.setFormType(sourceForm.getFormType());
                clonedForm.setBBox(
                    PdfCosUtils.copyRectangle(sourceForm.getBBox())
                );
                if (sourceForm.getMatrix() != null) {
                    clonedForm.setMatrix(
                        sourceForm.getMatrix().createAffineTransform()
                    );
                }
                if (sourceForm.getCOSObject().containsKey(COSName.GROUP)) {
                    COSDictionary sourceGroup = sourceForm.getCOSObject()
                        .getCOSDictionary(COSName.GROUP);
                    if (sourceGroup == null) {
                        throw PdfCosUtils.unsupportedTransparencyGroup();
                    }
                    clonedForm.setGroup(new PDTransparencyGroupAttributes(
                        sanitizeTransparencyGroup(sourceGroup)
                    ));
                }
                clonedForm.setResources(prune(
                    clonedForm,
                    sourceResources,
                    key,
                    traversal
                ));
                return clonedForm;
            } catch (IOException | RuntimeException exception) {
                contexts.remove(context);
                throw exception;
            }
        }

        private PDTilingPattern cloneTilingPattern(
                PDTilingPattern sourcePattern,
                PDResources inheritedResources,
                PdfResourceTraversal traversal) throws IOException {
            COSBase key = sourcePattern.getCOSObject();
            if (traversal.isActive(key)) {
                throw new OperationException(
                    "CYCLIC_PDF_RESOURCE",
                    "The PDF contains a cyclic tiling pattern"
                );
            }
            PDResources sourceResources = sourcePattern.getResources() == null
                ? inheritedResources
                : sourcePattern.getResources();
            COSDictionary context = sourceResources.getCOSObject();
            IdentityHashMap<COSDictionary, PDTilingPattern> contexts =
                tilingPatterns.computeIfAbsent(
                    key,
                    ignored -> new IdentityHashMap<>()
                );
            PDTilingPattern cached = contexts.get(context);
            if (cached != null) {
                return cached;
            }
            PDTilingPattern clonedPattern = new PDTilingPattern(
                materializer.materialize(sourcePattern).getCOSObject()
            );
            contexts.put(context, clonedPattern);
            try {
                clonedPattern.setPatternType(
                    PDAbstractPattern.TYPE_TILING_PATTERN
                );
                clonedPattern.setPaintType(sourcePattern.getPaintType());
                clonedPattern.setTilingType(sourcePattern.getTilingType());
                clonedPattern.setXStep(sourcePattern.getXStep());
                clonedPattern.setYStep(sourcePattern.getYStep());
                clonedPattern.setBBox(
                    PdfCosUtils.copyRectangle(sourcePattern.getBBox())
                );
                if (sourcePattern.getMatrix() != null) {
                    clonedPattern.setMatrix(
                        sourcePattern.getMatrix().createAffineTransform()
                    );
                }
                clonedPattern.setResources(prune(
                    clonedPattern,
                    sourceResources,
                    key,
                    traversal
                ));
                return clonedPattern;
            } catch (IOException | RuntimeException exception) {
                contexts.remove(context);
                throw exception;
            }
        }

        private PdfResourceUsage resourceUsage(PDContentStream content)
                throws IOException {
            try (InputStream input = content.getContents()) {
                new PdfContentPreflight(
                    properties.getMaxContentTokens(),
                    properties.getMaxResourceDepth()
                ).validate(input, cancellationCheck);
            }
            PDFStreamParser parser = new PDFStreamParser(content);
            PdfResourceUsage usage = new PdfResourceUsage();
            List<Object> operands = new ArrayList<>();
            int tokenCount = 0;
            int operators = 0;
            try {
                Object token;
                while ((token = parser.parseNextToken()) != null) {
                    if (++tokenCount > properties.getMaxContentTokens()) {
                        throw new OperationException(
                            "PDF_CONTENT_COMPLEXITY_LIMIT_EXCEEDED",
                            "PDF content contains too many tokens"
                        );
                    }
                    if (token instanceof Operator operator) {
                        recordResourceUsage(operator, operands, usage);
                        operands.clear();
                        if (++operators % 1000 == 0) {
                            cancellationCheck.run();
                        }
                    } else {
                        operands.add(token);
                        if (operands.size() > 4096) {
                            throw new OperationException(
                                "PDF_CONTENT_COMPLEXITY_LIMIT_EXCEEDED",
                                "PDF operator contains too many operands"
                            );
                        }
                    }
                }
            } finally {
                parser.close();
            }
            cancellationCheck.run();
            return usage;
        }

        private void recordResourceUsage(
                Operator operator,
                List<Object> operands,
                PdfResourceUsage usage) {
            COSName firstName = firstName(operands);
            switch (operator.getName()) {
                case "Tf" -> add(usage.fonts(), firstName);
                case "Do" -> add(usage.xObjects(), firstName);
                case "gs" -> add(
                    usage.extendedGraphicsStates(),
                    firstName
                );
                case "CS", "cs" -> add(usage.colorSpaces(), firstName);
                case "sh" -> add(usage.shadings(), firstName);
                case "SCN", "scn" -> add(
                    usage.patterns(),
                    lastName(operands)
                );
                case "BDC", "DP" -> {
                    if (COSName.OC.equals(firstName)) {
                        throw new OperationException(
                            "OPTIONAL_CONTENT_UNSUPPORTED",
                            "Split does not support optional-content marked content"
                        );
                    }
                    add(
                        usage.properties(),
                        propertyResourceName(operands)
                    );
                }
                case "BI" -> {
                    COSBase colorSpace = inlineColorSpace(
                        operator.getImageParameters()
                    );
                    if (colorSpace != null) {
                        usage.inlineColorSpaces().add(colorSpace);
                    }
                }
                default -> {
                }
            }
        }

        private COSBase inlineColorSpace(COSDictionary dictionary) {
            if (dictionary == null) {
                return null;
            }
            COSBase colorSpace = dictionary.getDictionaryObject(
                COSName.COLORSPACE
            );
            return colorSpace == null
                ? dictionary.getDictionaryObject(COSName.CS)
                : colorSpace;
        }

        private COSName firstName(List<Object> operands) {
            return operands.isEmpty()
                    || !(operands.getFirst() instanceof COSName name)
                ? null
                : name;
        }

        private COSName lastName(List<Object> operands) {
            for (int index = operands.size() - 1; index >= 0; index--) {
                if (operands.get(index) instanceof COSName name) {
                    return name;
                }
            }
            return null;
        }

        private COSName propertyResourceName(List<Object> operands) {
            return operands.size() > 1
                    && operands.get(1) instanceof COSName name
                ? name
                : null;
        }

        private void add(Set<COSName> names, COSName name) {
            if (name != null) {
                names.add(name);
            }
        }
    }
}
