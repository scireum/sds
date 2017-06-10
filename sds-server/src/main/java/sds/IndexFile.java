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
     * Contains md5 hash sum of file.
     */
    private String md5;

    public IndexFile(String path, long size, String md5) {
        this.path = path;
        this.size = size;
        this.md5 = md5;
    }

    public String getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public String getMd5() {
        return md5;
    }
}
