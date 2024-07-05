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

import org.bioimageanalysis.icy.Icy;
import org.bioimageanalysis.icy.extension.plugin.PluginLauncher;
import org.bioimageanalysis.icy.extension.plugin.PluginLoader;
import org.bioimageanalysis.icy.extension.plugin.annotation_.IcyPluginDescription;
import org.bioimageanalysis.icy.extension.plugin.annotation_.IcyPluginIcon;
import org.bioimageanalysis.icy.extension.plugin.annotation_.IcyPluginName;
import org.bioimageanalysis.icy.gui.dialog.MessageDialog;
import org.bioimageanalysis.icy.gui.viewer.Viewer;
import org.bioimageanalysis.icy.io.Loader;
import org.bioimageanalysis.icy.model.sequence.Sequence;
import org.bioimageanalysis.icy.system.logging.IcyLogger;
import org.bioimageanalysis.icy.system.thread.ThreadUtil;
import plugins.adufour.ezplug.EzButton;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzVarBoolean;
import plugins.adufour.ezplug.EzVarInteger;

import javax.swing.*;

@IcyPluginName("Template (EzPlug)") // This is the text that will be displayed on the UI
@IcyPluginDescription(shortDesc = "Description of my plugin.", longDesc = "") // Display the short description inside a tooltip on the UI (WIP)
@IcyPluginIcon(path = "/icon.svg", monochrome = false) // Do not forget to add '/' at the beginning, The icon must be SVG (monochrome or colored), PNG or JPG
public class MyEzPlugIcyPlugin extends EzPlug {
    private final EzVarInteger age = new EzVarInteger("Your age", 30, 10, 100, 1);
    private final EzVarBoolean yummy = new EzVarBoolean("Like chocolate?", true);
    private final EzButton button = new EzButton("Load an show an image now", l -> loadAndShow());

    @Override
    protected void initialize() {
        // Add elements in order of appearance.
        addEzComponent(age);
        addEzComponent(yummy);
        addEzComponent(button);
    }

    @Override
    public void clean() {
        // Nothing to do
    }

    @Override
    protected void execute() {
        final String str = "This plugin start button does not do anything useful.\n "
                + "You say you are " + age.getValue() + " years old and you "
                + (yummy.getValue() ? "like" : "don't like")
                + " chocolate.";
        MessageDialog.showDialog(str);

        // You can make some logs with IcyLogger class with these levels: trace, debug, info, warn, error, fatal (error and fatal are always printed on screen)
        IcyLogger.info(this.getClass(), str);
    }

    private void loadAndShow() {
        final String imagePath = "samples/Cont1.lsm";

        // Load in a separate thread.
        ThreadUtil.bgRun(() -> {
            try (final Sequence sequence = Loader.loadSequence(imagePath, 0, true)) {

                // Display the images.
                SwingUtilities.invokeAndWait(() -> new Viewer(sequence));
            }
            catch (final InterruptedException e) {
                IcyLogger.warn(this.getClass(), e, "Interrupted by user.");
            }
            catch (final Exception e) {
                IcyLogger.error(this.getClass(), e, "Error loading sequence.");
            }
        });
    }

    /**
     * Only for test purpose.
     */
    @SuppressWarnings("resource")
    public static void main(final String[] args) {
        // Launch the application.
        Icy.main(args);

        /*
         * Programmatically launch a plugin, as if the user had clicked its
         * button.
         */
        PluginLauncher.start(PluginLoader.getPlugin(MyEzPlugIcyPlugin.class.getName()));
    }
}
