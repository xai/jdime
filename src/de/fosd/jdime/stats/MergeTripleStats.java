/* 
 * Copyright (C) 2013 Olaf Lessenich.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package de.fosd.jdime.stats;

import de.fosd.jdime.common.FileArtifact;
import de.fosd.jdime.common.MergeTriple;

/**
 * @author Olaf Lessenich
 *
 */
public class MergeTripleStats {

    /**
     *
     */
    private MergeTriple<FileArtifact> triple;
    /**
     *
     */
    private int conflicts;
    /**
     *
     */
    private int conflictingLines;
    /**
     *
     */
    private int lines;
    /**
     *
     */
    private long runtime;
    /**
     *
     */
    private boolean error = false;
    /**
     *
     */
    private String errormsg;

    /**
     * Class Constructor.
     *
     * @param triple merge triple
     * @param conflicts number of conflicts
     * @param conflictingLines number of conflicting lines
     * @param lines number of lines
     * @param runtime runtime for the scenario
     */
    public MergeTripleStats(final MergeTriple<FileArtifact> triple,
            final int conflicts, final int conflictingLines, final int lines,
            final long runtime) {
        this.triple = triple;
        this.conflicts = conflicts;
        this.conflictingLines = conflictingLines;
        this.lines = lines;
        this.runtime = runtime;
    }

    /**
     * Class constructor.
     *
     * @param triple merge triple
     * @param errormsg error message
     */
    public MergeTripleStats(final MergeTriple<FileArtifact> triple,
            final String errormsg) {
        this.triple = triple;
        this.error = true;
        this.errormsg = errormsg;
    }

    /**
     * Returns true if there were errors during this merge.
     *
     * @return true if errors occurred during the merge
     */
    public final boolean hasErrors() {
        return error;
    }

    /**
     * Returns the error message.
     *
     * @return error message
     */
    public final String getErrorMsg() {
        return errormsg;
    }

    /**
     * @return the triple
     */
    public final MergeTriple<FileArtifact> getTriple() {
        return triple;
    }

    /**
     * @return the conflicts
     */
    public final int getConflicts() {
        return conflicts;
    }

    /**
     * @return the conflictingLines
     */
    public final int getConflictingLines() {
        return conflictingLines;
    }

    /**
     * @return the lines
     */
    public final int getLines() {
        return lines;
    }

    /**
     * @return the runtime
     */
    public final long getRuntime() {
        return runtime;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public final String toString() {
        return triple.toString() + ": " + conflicts + " conflicts, "
                + conflictingLines + " cloc, " + lines + " loc, " + runtime
                + " ms.";
    }
}