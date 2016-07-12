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
package org.aavso.tools.vstar.vela;

import java.util.Stack;

import org.aavso.tools.vstar.vela.VeLaParser.RealContext;

/**
 * VeLa: VStar expression Language interpreter
 * 
 * Real number listener.
 * @deprecated
 */
public class RealListener extends VeLaBaseListener {

	private Stack<Double> stack;
	
	public RealListener(Stack<Double> stack) {
		this.stack = stack;
	}

	@Override
	public void exitReal(RealContext ctx) {
		String str = ctx.getText();
		stack.push(VeLaInterpreter.parseDouble(str));
	}
}
