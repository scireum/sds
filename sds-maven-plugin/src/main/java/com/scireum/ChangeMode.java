/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package com.scireum;

public enum ChangeMode {
    /**
     * File has not changed
     */
    SAME,

    /**
     * File has changed
     */
    CHANGED,

    /**
     * File did not exist
     */
    NEW,

    /**
     * File does not exist anymore
     */
    DELETED,
}
