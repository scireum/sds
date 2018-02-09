/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sds;

/**
 * Represents file information of a file in the repository.
 */
public class IndexFile {

    /**
     * Contains relative path of file in artifact.
     */
    private String path;

    /**
     * Contains size of file in bytes.
     */
    private long size;

    /**
     * Contains crc32 hash sum of file.
     */
    private long crc;

    public IndexFile(String path, long size, long crc) {
        this.path = path;
        this.size = size;
        this.crc = crc;
    }

    public String getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public long getCRC() {
        return crc;
    }
}
