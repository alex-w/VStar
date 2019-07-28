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
package org.aavso.tools.vstar.plugin;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.aavso.tools.vstar.data.ValidObservation;
import org.aavso.tools.vstar.ui.resources.LoginInfo;

/**
 * <p>
 * This is the base class for all observation sink plug-in classes.
 * </p>
 * <p>
 * An observation sink plugin will appear in a chooser in VStar's file save
 * dialog menu when its jar file is placed into the vstar_plugins directory.
 * </p>
 */
public abstract class ObservationSinkPluginBase implements IPlugin {

	/**
	 * Save the specified observations to the specified output stream.
	 * 
	 * @param stream
	 *            A buffered output stream.
	 * @param obs
	 *            A list of observations.
	 * @param delimiter
	 *            The field delimiter to use; may be null.
	 */
	abstract public void save(BufferedOutputStream stream,
			List<ValidObservation> obs, String delimiter) throws IOException;

	/**
	 * Return a mapping from field delimiter names to delimiter string values to
	 * be displayed in the file save dialog.
	 * 
	 * @return The map of field delimiter name-value pairs; may be null.
	 */
	public Map<String, String> getDelimiterNameValuePairs() {
		return null;
	}

	/**
	 * @see org.aavso.tools.vstar.plugin.IPlugin#getGroup()
	 */
	@Override
	public String getGroup() {
		return null;
	}

	/**
	 * @see org.aavso.tools.vstar.plugin.IPlugin#requiresAuthentication()
	 */
	@Override
	public boolean requiresAuthentication() {
		return false;
	}

	/**
	 * @see org.aavso.tools.vstar.plugin.IPlugin#additionalAuthenticationSatisfied(org.aavso.tools.vstar.ui.resources.LoginInfo)
	 */
	@Override
	public boolean additionalAuthenticationSatisfied(LoginInfo loginInfo) {
		return true;
	}
}