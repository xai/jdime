/**
 * Copyright (C) 2013-2014 Olaf Lessenich
 * Copyright (C) 2014-2015 University of Passau, Germany
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
 *
 * Contributors:
 *     Olaf Lessenich <lessenic@fim.uni-passau.de>
 *     Georg Seibt <seibt@fim.uni-passau.de>
 */
package de.fosd.jdime.config;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import de.fosd.jdime.strdump.DumpMode;
import de.uni_passau.fim.seibt.kvconfig.sources.ConfigSource;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * A <code>ConfigSource</code> backed by a <code>CommandLine</code> instance. Its {@link #getMapping(String)} method
 * will (for both long and short option names) return the first argument to the option if it is set and has arguments
 * or "true" for options that are set but have no arguments. Otherwise an empty Optional is returned.
 * The left over arguments on the command line ({@link CommandLine#getArgList()}) can be retrieved using the key
 * {@link #ARG_LIST}.
 */
public class CommandLineConfigSource extends ConfigSource {

    /*
     * These constants define the (short) parameter names expected on the command line. Corresponding Options
     * are constructed in buildCliOptions().
     */
    public static final String CLI_LOG_LEVEL = "log";
    public static final String CLI_CONSECUTIVE = "c";
    public static final String CLI_DIFFONLY = "d";
    public static final String CLI_FORCE_OVERWRITE = "f";
    public static final String CLI_HELP = "h";
    public static final String CLI_KEEPGOING = "k";
    public static final String CLI_LOOKAHEAD = "lah";
    public static final String CLI_INSPECT = "i";
    public static final String CLI_MODE = "m";
    public static final String CLI_DUMP = "dmp";
    public static final String CLI_NOEQUALITYMATCHER = "ne";
    public static final String CLI_NOFILTEREQUALITYMATCHER = "nfe";
    public static final String CLI_OUTPUT = "o";
    public static final String CLI_RECURSIVE = "r";
    public static final String CLI_STATS = "s";
    public static final String CLI_PRINT = "p";
    public static final String CLI_QUIET = "q";
    public static final String CLI_VERSION = "v";
    public static final String CLI_PROP_FILE = "pf";
    public static final String CLI_EXIT_ON_ERROR = "eoe";

    public static final String ARG_LIST = "ARG_LIST";
    public static final String ARG_LIST_SEP = ",";

    private Options options;
    private CommandLine cmdLine;

    /**
     * Constructs a new <code>CommandLineConfigSource</code> from the given <code>args</code>.
     *
     * @param args
     *         the command line arguments to parse
     * @throws ParseException
     *         if there is an exception parsing the arguments
     */
    public CommandLineConfigSource(String[] args) throws ParseException {
        this(args, DEFAULT_PRIORITY);
    }

    /**
     * Constructs a new <code>CommandLineConfigSource</code> from the given <code>args</code>.
     *
     * @param args
     *         the command line arguments to parse
     * @param priority
     *         the priority for this <code>ConfigSource</code>
     * @throws ParseException
     *         if there is an exception parsing the arguments
     */
    public CommandLineConfigSource(String[] args, int priority) throws ParseException {
        super(priority, null, null);

        this.options = buildCliOptions();
        this.cmdLine = new DefaultParser().parse(options, args);
    }

    /**
     * Builds the <code>Options</code> instance describing the JDime command line configuration options.
     *
     * @return the <code>Options</code> instance
     */
    private Options buildCliOptions() {
        Options options = new Options();
        Option o;

        o = Option.builder(CLI_LOG_LEVEL)
                .longOpt("log-level")
                .desc("Set the logging level to one of (OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, ALL).")
                .hasArg()
                .argName("level")
                .build();

        options.addOption(o);

        o = Option.builder(CLI_CONSECUTIVE)
                .longOpt("consecutive")
                .desc("Requires diffonly mode. Treats versions as consecutive versions.")
                .hasArg(false)
                .build();

        options.addOption(o);

        o = Option.builder(CLI_DIFFONLY)
                .longOpt("diffonly")
                .desc("Only perform the diff stage.")
                .hasArg(false)
                .build();

        options.addOption(o);

        o = Option.builder(CLI_FORCE_OVERWRITE)
                .longOpt("force-overwrite")
                .desc("Force overwriting of output files.")
                .hasArg(false)
                .build();

        options.addOption(o);

        o = Option.builder(CLI_HELP)
                .longOpt("help")
                .desc("Print this message.")
                .hasArg(false)
                .build();

        options.addOption(o);

        o = Option.builder(CLI_KEEPGOING)
                .longOpt("keep-going")
                .desc("Whether to skip a set of files if there is an exception merging them.")
                .hasArg(false)
                .build();

        options.addOption(o);

        o = Option.builder(CLI_LOOKAHEAD)
                .longOpt("lookahead")
                .desc("Use heuristics for matching. Supply off, full, or a number as argument.")
                .hasArg()
                .argName("level")
                .build();

        options.addOption(o);

        o = Option.builder(CLI_INSPECT)
                .longOpt("inspect")
                .desc("Inspect an AST element. Supply number of element.")
                .hasArg()
                .argName("element")
                .build();

        options.addOption(o);

        o = Option.builder(CLI_MODE)
                .longOpt("mode")
                .desc("Set the mode to one of (unstructured, structured, autotuning, dumptree, dumpgraph, dumpfile, " +
                        "prettyprint, nway)")
                .hasArg()
                .argName("mode")
                .build();

        options.addOption(o);

        {
            String formats = Arrays.stream(DumpMode.values()).map(Enum::name).reduce("", (s, s2) -> s + " " + s2);

            o = Option.builder(CLI_DUMP)
                    .longOpt("dump")
                    .desc("Dumps the inputs using one of the formats: " + formats)
                    .hasArg()
                    .argName("format")
                    .build();

            options.addOption(o);
        }

        o = Option.builder(CLI_NOEQUALITYMATCHER)
                .longOpt("no-equalitymatcher")
                .desc("Disable equality matcher.")
                .hasArg(false)
                .build();

        options.addOption(o);

        o = Option.builder(CLI_NOFILTEREQUALITYMATCHER)
                .longOpt("no-filter-equalitymatcher")
                .desc("Disable filtering results of equality matcher.")
                .hasArg(false)
                .build();

        options.addOption(o);

        o = Option.builder(CLI_OUTPUT)
                .longOpt("output")
                .desc("Set the output directory/file.")
                .hasArg()
                .argName("file")
                .build();

        options.addOption(o);

        o = Option.builder(CLI_RECURSIVE)
                .longOpt("recursive")
                .desc("Merge directories recursively.")
                .hasArg(false)
                .build();

        options.addOption(o);

        o = Option.builder(CLI_STATS)
                .longOpt("stats")
                .desc("Collect statistical data about the merge.")
                .hasArg(false)
                .build();

        options.addOption(o);

        o = Option.builder(CLI_PRINT)
                .longOpt("print")
                .desc("(print/pretend) Prints the merge result to stdout instead of an output file.")
                .hasArg(false)
                .build();

        options.addOption(o);

        o = Option.builder(CLI_QUIET)
                .longOpt("quiet")
                .desc("Do not print the merge result to stdout.")
                .hasArg(false)
                .build();

        options.addOption(o);

        o = Option.builder(CLI_VERSION)
                .longOpt("version")
                .desc("Print the version information and exit.")
                .hasArg(false)
                .build();

        options.addOption(o);

        o = Option.builder(CLI_PROP_FILE)
                .longOpt("properties-file")
                .desc("Set the path to the properties file to use for additional configuration options.")
                .hasArg()
                .argName("path")
                .build();

        options.addOption(o);

        o = Option.builder(CLI_EXIT_ON_ERROR)
                .longOpt("exit-on-error")
                .desc("Whether to end the merge if there is an exception merging a set of files. If neither this " +
                        "option nor keep-going is set the fallback line based strategy will be tried.")
                .hasArg(false)
                .build();

        options.addOption(o);

        return options;
    }

    /**
     * Returns the <code>Options</code> describing the command line options.
     *
     * @return the <code>Options</code>
     */
    public Options getOptions() {
        return options;
    }

    @Override
    protected Optional<String> getMapping(String key) {

        if (ARG_LIST.equals(key)) {
            List<String> argList = cmdLine.getArgList();

            if (argList.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(String.join(ARG_LIST_SEP, argList));
            }
        }

        if (!options.hasOption(key)) {
            return Optional.empty();
        }

        Option opt = options.getOption(key);
        String optName = opt.getOpt();

        if (!cmdLine.hasOption(optName)) {
            return Optional.empty();
        }

        if (opt.hasArg()) {
            return Optional.of(cmdLine.getOptionValue(optName));
        } else {
            return Optional.of("true");
        }
    }
}
