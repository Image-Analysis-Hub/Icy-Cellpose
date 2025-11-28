/*
 * Copyright (c) 2010-2025. Institut Pasteur.
 *
 * This file is part of Icy.
 * Icy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Icy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Icy. If not, see <https://www.gnu.org/licenses/>.
 */

package icy.plugins.authorname.plugintemplate;

import org.bioimageanalysis.icy.extension.plugin.abstract_.PluginActionable;
import org.bioimageanalysis.icy.extension.plugin.annotation_.IcyPluginName;
import org.bioimageanalysis.icy.system.logging.IcyLogger;

/**
 * A simple multi-release plugin demonstration.
 * <p>
 * This shows you that you can define the same class with different versions of Java (this one is the default one, in Java 17).
 */
@IcyPluginName("MMJ 17")
public class TestMMJPlugin extends PluginActionable {
    /**
     * Runs this operation.
     */
    @Override
    public void run() {
        IcyLogger.success(this.getClass(), "MMJ: 17");
    }
}
