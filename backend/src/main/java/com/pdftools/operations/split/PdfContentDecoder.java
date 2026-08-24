package com.pdftools.operations.split;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.CheckpointInputStream;
import com.pdftools.operations.OperationException;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.contentstream.PDContentStream;
import org.apache.pdfbox.filter.Filter;
import org.apache.pdfbox.filter.FilterFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.COSObjectable;
import org.apache.pdfbox.pdmodel.common.PDStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

final class PdfContentDecoder {

    private static final int MAX_FILTERS_PER_STREAM = 16;

    Result materialize(
            PDDocument destination,
            PDContentStream content,
            long limit,
            SplitDecodedBudget budget,
            Runnable cancellationCheck) throws IOException {
        List<COSStream> sourceStreams = sourceStreams(content);
        if (sourceStreams.size() == 1) {
            Stage stage = decodeStream(
                destination,
                sourceStreams.getFirst(),
                limit,
                budget,
                cancellationCheck
            );
            return new Result(stage.stream(), stage.sizeBytes());
        }

        PDStream combined = new PDStream(destination);
        try (OutputStream output = combined.createOutputStream();
             OutputStream budgeted = new SplitBudgetOutputStream(output, budget);
             BoundedOutputStream bounded = new BoundedOutputStream(
                 budgeted,
                 limit,
                 cancellationCheck
             )) {
            for (int index = 0; index < sourceStreams.size(); index++) {
                Stage decoded = decodeStream(
                    destination,
                    sourceStreams.get(index),
                    limit,
                    budget,
                    cancellationCheck
                );
                try (InputStream input = new CheckpointInputStream(
                        decoded.stream().getCOSObject().createRawInputStream(),
                        cancellationCheck)) {
                    input.transferTo(bounded);
                } finally {
                    decoded.stream().getCOSObject().close();
                }
                if (index + 1 < sourceStreams.size()) {
                    bounded.write('\n');
                }
            }
            cancellationCheck.run();
            return new Result(combined, bounded.getCount());
        }
    }

    private Stage decodeStream(
            PDDocument destination,
            COSStream source,
            long limit,
            SplitDecodedBudget budget,
            Runnable cancellationCheck) throws IOException {
        List<org.apache.pdfbox.cos.COSName> filters =
            new PDStream(source).getFilters();
        if (filters.size() > MAX_FILTERS_PER_STREAM) {
            throw new OperationException(
                "PDF_FILTER_LIMIT_EXCEEDED",
                "PDF content uses too many nested stream filters"
            );
        }

        InputStream current = source.createRawInputStream();
        Stage previous = null;
        try {
            if (filters.isEmpty()) {
                return copyStage(
                    destination,
                    current,
                    limit,
                    budget,
                    cancellationCheck
                );
            }
            for (int index = 0; index < filters.size(); index++) {
                PDStream stream = new PDStream(destination);
                Filter filter = FilterFactory.INSTANCE.getFilter(filters.get(index));
                long stageSize;
                try {
                    try (OutputStream output = stream.createOutputStream();
                         OutputStream budgeted = new SplitBudgetOutputStream(
                             output,
                             budget
                         );
                         BoundedOutputStream bounded = new BoundedOutputStream(
                             budgeted,
                             limit,
                             cancellationCheck
                         );
                         InputStream checked = new CheckpointInputStream(
                             current,
                             cancellationCheck
                         )) {
                        filter.decode(checked, bounded, source, index);
                        stageSize = bounded.getCount();
                    }
                } catch (IOException | RuntimeException exception) {
                    stream.getCOSObject().close();
                    throw exception;
                }
                if (previous != null) {
                    previous.stream().getCOSObject().close();
                }
                previous = new Stage(stream, stageSize);
                current = stream.getCOSObject().createRawInputStream();
                cancellationCheck.run();
            }
            Stage result = previous;
            previous = null;
            return result;
        } finally {
            current.close();
            if (previous != null) {
                previous.stream().getCOSObject().close();
            }
        }
    }

    private Stage copyStage(
            PDDocument destination,
            InputStream source,
            long limit,
            SplitDecodedBudget budget,
            Runnable cancellationCheck) throws IOException {
        PDStream stage = new PDStream(destination);
        try (InputStream checked = new CheckpointInputStream(
                source,
                cancellationCheck);
             OutputStream output = stage.createOutputStream();
             OutputStream budgeted = new SplitBudgetOutputStream(output, budget);
             BoundedOutputStream bounded = new BoundedOutputStream(
                 budgeted,
                 limit,
                 cancellationCheck
             )) {
            checked.transferTo(bounded);
            return new Stage(stage, bounded.getCount());
        }
    }

    private List<COSStream> sourceStreams(PDContentStream content) {
        COSBase raw;
        if (content instanceof PDPage page) {
            raw = PdfCosUtils.dereference(
                page.getCOSObject().getItem(
                    org.apache.pdfbox.cos.COSName.CONTENTS
                )
            );
        } else if (content instanceof COSObjectable objectable) {
            raw = PdfCosUtils.dereference(objectable.getCOSObject());
        } else {
            throw new OperationException(
                "INVALID_PDF_CONTENT",
                "PDF content does not expose a readable stream"
            );
        }

        if (raw == null) {
            return List.of();
        }
        if (raw instanceof COSStream stream) {
            return List.of(stream);
        }
        if (!(raw instanceof COSArray streams)) {
            throw new OperationException(
                "INVALID_PDF_CONTENT",
                "PDF content contains an invalid stream collection"
            );
        }
        List<COSStream> result = new ArrayList<>(streams.size());
        for (int index = 0; index < streams.size(); index++) {
            COSBase item = PdfCosUtils.dereference(streams.get(index));
            if (!(item instanceof COSStream stream)) {
                throw new OperationException(
                    "INVALID_PDF_CONTENT",
                    "PDF content collection contains a non-stream entry"
                );
            }
            result.add(stream);
        }
        return result;
    }

    record Result(PDStream stream, long sizeBytes) {
    }

    private record Stage(PDStream stream, long sizeBytes) {
    }

}
