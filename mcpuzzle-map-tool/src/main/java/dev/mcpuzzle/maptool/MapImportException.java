package dev.mcpuzzle.maptool;

/** Indicates that analysis data could not be converted into a valid map-pack draft. */
public final class MapImportException extends Exception {
    public MapImportException(String message) {
        super(message);
    }

    public MapImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
