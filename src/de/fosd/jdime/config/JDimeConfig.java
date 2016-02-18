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

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.fosd.jdime.Main;
import de.fosd.jdime.matcher.ordered.mceSubtree.MCESubtreeMatcher;
import de.fosd.jdime.stats.KeyEnums;
import de.uni_passau.fim.seibt.kvconfig.Config;
import de.uni_passau.fim.seibt.kvconfig.sources.PropFileConfigSource;
import de.uni_passau.fim.seibt.kvconfig.sources.SysEnvConfigSource;

/**
 * Contains the singleton <code>Config</code> instance containing the configuration options for JDime. All
 * keys used for retrieving config options should be declared as static final <code>String</code>s in this class.
 */
public final class JDimeConfig extends Config {

    private static final Logger LOG = Logger.getLogger(JDimeConfig.class.getCanonicalName());

    /**
     * The file name of the JDime configuration file.
     */
    private static final String CONFIG_FILE_NAME = "JDime.properties";

    /**
     * The default value for the 'Args' text field in the GUI.
     */
    public static final String DEFAULT_ARGS = "DEFAULT_ARGS";

    /**
     * The default value for the 'Left' text field in the GUI.
     */
    public static final String DEFAULT_LEFT = "DEFAULT_LEFT";

    /**
     * The default value for the 'Base' text field in the GUI.
     */
    public static final String DEFAULT_BASE = "DEFAULT_BASE";

    /**
     * The default value for the 'Right' text field in the GUI.
     */
    public static final String DEFAULT_RIGHT = "DEFAULT_RIGHT";

    /**
     * The default value for the 'JDime' text field in the GUI.
     */
    public static final String DEFAULT_JDIME_EXEC = "DEFAULT_JDIME_EXEC";

    /**
     * Whether to allow invalid values (such as non-existent files) for the text fields in the GUI. Must be either
     * 'true' or 'false'.
     */
    public static final String ALLOW_INVALID = "ALLOW_INVALID";

    /**
     * How many lines of JDime output to buffer before adding them to the displayed lines in the GUI. Must
     * be a number parseable by {@link Integer#parseInt(String)}.
     */
    public static final String BUFFERED_LINES = "BUFFERED_LINES";

    /**
     * Whether to use the {@link MCESubtreeMatcher} when diffing. Must be either 'true' or 'false'.
     */
    public static final String USE_MCESUBTREE_MATCHER = "USE_MCESUBTREE_MATCHER";

    /**
     * Whether to append a number to the file name to ensure that no file of the same name is overwritten when
     * writing the statistics. Must be either 'true' or 'false'. Defaults to true.
     */
    public static final String STATISTICS_OUTPUT_USE_UNIQUE_FILES = "STATISTICS_OUTPUT_USE_UNIQUE_FILES";

    /**
     * Using this value for {@link #STATISTICS_HR_OUTPUT} or {@link #STATISTICS_XML_OUTPUT} disables the output.
     */
    public static final String STATISTICS_OUTPUT_OFF = "off";

    /**
     * Using this value for {@link #STATISTICS_HR_OUTPUT} or {@link #STATISTICS_XML_OUTPUT} sends the output to standard
     * out.
     */
    public static final String STATISTICS_OUTPUT_STDOUT = "stdout";

    /**
     * Where to send the human readable statistics output if '-stats' is given on the command line. If the value denotes
     * a file this file will be written to, if it denotes a directory a file will be created there using the pattern
     * specified in {@link #STATISTICS_HR_NAME}. Paths are relative to the current working directory.
     * Defaults to {@link #STATISTICS_OUTPUT_STDOUT}.
     *
     * @see #STATISTICS_OUTPUT_OFF
     * @see #STATISTICS_OUTPUT_STDOUT
     * @see #STATISTICS_OUTPUT_USE_UNIQUE_FILES
     */
    public static final String STATISTICS_HR_OUTPUT = "STATISTICS_HR_OUTPUT";

    /**
     * A {@link String#format(Locale, String, Object...)} pattern to be used when creating a new file to write
     * the human readable statistics output to. The current {@link Date} will be passed to the format method as its
     * first parameter after the format <code>String</code>. Defaults to {@link #STATISTICS_HR_DEFAULT_NAME}.
     */
    public static final String STATISTICS_HR_NAME = "STATISTICS_HR_NAME";

    /**
     * The default name pattern when {@link #STATISTICS_HR_NAME} is not given.
     */
    public static final String STATISTICS_HR_DEFAULT_NAME = "Statistics_HR.txt";

    /**
     * Where to send the XML statistics output if '-stats' is given on the command line. If the value denotes
     * a file this file will be written to, if it denotes a directory a file will be created there using the pattern
     * specified in {@link #STATISTICS_XML_NAME}. Paths are relative to the current working directory.
     * Defaults to {@link #STATISTICS_OUTPUT_OFF}.
     *
     * @see #STATISTICS_OUTPUT_OFF
     * @see #STATISTICS_OUTPUT_STDOUT
     * @see #STATISTICS_OUTPUT_USE_UNIQUE_FILES
     */
    public static final String STATISTICS_XML_OUTPUT = "STATISTICS_XML_OUTPUT";

    /**
     * A {@link String#format(Locale, String, Object...)} pattern to be used when creating a new file to write
     * the XML statistics output to. The current {@link Date} will be passed to the format method as its
     * first parameter after the format <code>String</code>. Defaults to {@link #STATISTICS_XML_DEFAULT_NAME}.
     */
    public static final String STATISTICS_XML_NAME = "STATISTICS_XML_NAME";

    /**
     * The default name pattern when {@link #STATISTICS_XML_NAME} is not given.
     */
    public static final String STATISTICS_XML_DEFAULT_NAME = "Statistics_XML.xml";

    /**
     * Constructs a new <code>JDimeConfig</code>. A <code>SysEnvConfigSource</code> will be added. If a
     * <code>File</code> named {@value #CONFIG_FILE_NAME} in the current working directory does exist a
     * <code>PropFileConfigSource</code> will be added for it.
     */
    public JDimeConfig() {
        addSource(new SysEnvConfigSource(1));
        loadConfigFile(new File(CONFIG_FILE_NAME));
    }

    /**
     * Constructs a new <code>JDimeConfig</code>. A <code>SysEnvConfigSource</code> will be added. If
     * <code>configFile</code> does exist a <code>PropFileConfigSource</code> will be added for it.
     *
     * @param configFile
     *         the configuration property file
     */
    public JDimeConfig(File configFile) {
        addSource(new SysEnvConfigSource(1));
        loadConfigFile(configFile);
    }

    /**
     * Checks whether the given file exists and if so adds a <code>PropFileConfigSource</code> for it.
     *
     * @param configFile
     *         the file to check
     */
    private void loadConfigFile(File configFile) {

        if (configFile.exists()) {

            try {
                addSource(new PropFileConfigSource(2, configFile));
            } catch (IOException e) {
                LOG.log(Level.WARNING, e, () -> "Could not add a ConfigSource for " + configFile.getAbsolutePath());
            }
        } else {
            LOG.log(Level.WARNING, () -> String.format("%s can not be used as a config file as it does not exist.", configFile));
        }
    }

    /**
     * Set the logging level. The levels in descending order are:<br>
     *
     * <ul>
     *  <li>ALL</li>
     *  <li>SEVERE (highest value)</li>
     *  <li>WARNING</li>
     *  <li>INFO</li>
     *  <li>CONFIG</li>
     *  <li>FINE</li>
     *  <li>FINER</li>
     *  <li>FINEST (lowest value)</li>
     *  <li>OFF</li>
     * </ul>
     *
     * @param logLevel
     *             one of the valid log levels according to {@link Level#parse(String)}
     */
    public static void setLogLevel(String logLevel) {
        Level level;

        try {
            level = Level.parse(logLevel.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOG.warning(() -> "Invalid log level %s. Must be one of OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST or ALL.");
            return;
        }

        Logger root = Logger.getLogger(Main.class.getPackage().getName());
        root.setLevel(level);

        for (Handler handler : root.getHandlers()) {
            handler.setLevel(level);
        }
    }
}
