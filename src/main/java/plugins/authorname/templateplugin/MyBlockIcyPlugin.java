/*
 * Copyright (c) 2010-2024. Institut Pasteur.
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

package plugins.authorname.templateplugin;

import org.bioimageanalysis.icy.extension.plugin.abstract_.Plugin;
import org.bioimageanalysis.icy.extension.plugin.annotation_.IcyPluginIcon;
import org.bioimageanalysis.icy.extension.plugin.annotation_.IcyPluginName;
import org.jetbrains.annotations.NotNull;
import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.ezplug.EzVarInteger;
import plugins.adufour.vars.lang.VarInteger;

import java.util.Calendar;

@IcyPluginName("Template (Block)")
@IcyPluginIcon(path = "/icon.svg")
public class MyBlockIcyPlugin extends Plugin implements Block {
    private final VarInteger age = new VarInteger("Age", 30);
    private final VarInteger birth = new VarInteger("Birth Year", 1900);

    /**
     * Fills the specified map with all the necessary input variables
     *
     * @param inputMap the list of input variables to fill
     */
    @Override
    public void declareInput(final @NotNull VarList inputMap) {
        inputMap.add("age", age);
    }

    /**
     * Fills the specified map with all the necessary output variables
     *
     * @param outputMap the list of output variables to fill
     */
    @Override
    public void declareOutput(final @NotNull VarList outputMap) {
        outputMap.add("birth", birth);
    }

    /**
     * Main method
     */
    @Override
    public void run() {
        final Calendar cal = Calendar.getInstance();
        final int year = cal.get(Calendar.YEAR);

        final int by = year - age.getValue();
        birth.setValue(by);
    }
}
