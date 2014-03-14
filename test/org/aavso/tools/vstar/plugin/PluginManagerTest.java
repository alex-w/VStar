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

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.aavso.tools.vstar.ui.dialog.plugin.manager.PluginManager;
import org.aavso.tools.vstar.ui.dialog.plugin.manager.PluginManagerException;

/**
 * PluginManager test cases.
 */
public class PluginManagerTest extends TestCase {

	private PluginManager pluginManager;

	public PluginManagerTest(String name) {
		super(name);
	}

	protected void setUp() throws Exception {
		super.setUp();
		pluginManager = new PluginManager();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	public void testRetrievePluginInfo() {
		pluginManager.retrieveRemotePluginInfo();
		Map<String, URL> plugins = pluginManager.getRemotePluginsByJarName();
		assertEquals(10, plugins.size());
		Map<String, List<URL>> libs = pluginManager.getLibs();
		assertEquals(2, libs.size());
	}

	public void testRetrieveLocalPluginJarInfo() throws Exception {
		try {
			pluginManager.retrieveLocalPluginInfo();
			assertTrue(pluginManager.getLocalDescriptionsToJarName().size() > 0);
		} catch (PluginManagerException e) {
			fail();
		}
	}

	public void testInstallPlugins() throws IOException {
		pluginManager
				.retrieveRemotePluginInfo(PluginManager.DEFAULT_PLUGIN_BASE_URL_STR);
		Collection<String> jarNames = pluginManager.getRemotePluginsByJarName()
				.keySet();
		try {
			for (String jarName : jarNames) {
				pluginManager.installPlugin(jarName, PluginManager.Operation.INSTALL);
			}
		} catch (PluginManagerException e) {
			fail();
		}
	}
}