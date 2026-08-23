package com.pdftools.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface StorageService {

    StoredObject put(String key, InputStream inputStream, long contentLength, String mediaType)
        throws IOException;

    StoredResource get(String key) throws IOException;

    List<StoredObjectInfo> list(String prefix) throws IOException;

    void delete(String key) throws IOException;
}
