/*
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
 */

package de.fosd.jdime;

import static de.fosd.jdime.InternalTests.runEnvironmentTest;
import static org.junit.Assert.*;

import org.junit.Test;

/**
 * @author Olaf Lessenich
 *
 */
public class EnvironmentTest {

    @Test
    public final void test() {
        try {
            runEnvironmentTest();
        } catch (Exception e ) {
            fail("Internal tests failed. Check your setup!");
        }
    }

}
