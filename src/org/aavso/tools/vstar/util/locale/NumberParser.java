/**
 * VStar: a statistical analysis tool for variable star data.
 * Copyright (C) 2010  AAVSO (http://www.aavso.org/)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */
package org.aavso.tools.vstar.util.locale;

import org.aavso.tools.vstar.vela.Operand;
import org.aavso.tools.vstar.vela.VeLaEvalError;
import org.aavso.tools.vstar.vela.VeLaInterpreter;
import org.aavso.tools.vstar.vela.VeLaParseError;

/**
 * This class contains a static methods that parses numeric expressions in a
 * locale-independent way, intended as replacements for Number-subclass methods
 * of the same names.
 */
public class NumberParser {

	private static VeLaInterpreter vela = new VeLaInterpreter();

	/**
	 * Parse a string, returning a double primitive value, or if no valid double
	 * value is present, throw an exception. The string is first trimmed of
	 * leading and trailing whitespace.
	 * 
	 * @param str
	 *            The string that (hopefully) contains a number.
	 * @return The double value corresponding to the initial parseable portion
	 *         of the string.
	 * @throws VeLaParseError
	 *             If a parse error occurs.
	 * @throws VeLaEvalError
	 *             If an evaluation error occurs.
	 * @throws NumberFormatException
	 *             If there was no valid double value resulting from the parse
	 *             (e.g. the VeLa expression evaluated to a string).
	 */
	public static double parseDouble(String str) throws VeLaParseError,
			VeLaEvalError, NumberFormatException {
		Operand operand = vela.expressionToOperand(str);

		switch (operand.getType()) {
		case INTEGER:
		case DOUBLE:
			break;
			
		default:
			throw new NumberFormatException();
		}

		return operand.doubleVal();
	}
}
