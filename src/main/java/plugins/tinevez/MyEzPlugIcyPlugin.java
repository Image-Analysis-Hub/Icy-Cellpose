/*
 * Copyright (c) 2010-2023. Institut Pasteur.
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

package plugins.tinevez;

import plugins.adufour.ezplug.EzButton;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzVarBoolean;
import plugins.adufour.ezplug.EzVarInteger;

import javax.swing.*;

import org.bioimageanalysis.icy.Icy;
import org.bioimageanalysis.icy.extension.ExtensionLoader;
import org.bioimageanalysis.icy.extension.plugin.PluginLauncher;
import org.bioimageanalysis.icy.extension.plugin.PluginLoader;
import org.bioimageanalysis.icy.gui.dialog.MessageDialog;
import org.bioimageanalysis.icy.gui.viewer.Viewer;
import org.bioimageanalysis.icy.io.Loader;
import org.bioimageanalysis.icy.model.sequence.Sequence;
import org.bioimageanalysis.icy.system.IcyExceptionHandler;
import org.bioimageanalysis.icy.system.logging.IcyLogger;
import org.bioimageanalysis.icy.system.thread.ThreadUtil;

import java.lang.reflect.InvocationTargetException;

public class MyEzPlugIcyPlugin extends EzPlug
{

	private final EzVarInteger age = new EzVarInteger( "Your age", 30, 10, 100, 1 );

	private final EzVarBoolean yummy = new EzVarBoolean( "Like chocolate?", true );

	private final EzButton button = new EzButton( "Load an show an image now", l -> loadAndShow() );

	@Override
	protected void initialize()
	{
		// Add elements in order of appearance.
		addEzComponent( age );
		addEzComponent( yummy );
		addEzComponent( button );
	}

	@Override
	public void clean()
	{
		// Nothing to do
	}

	@Override
	protected void execute()
	{
		final String str = "This plugin start button does not do anything useful.\n " + "You say you are "
				+ age.getValue() + " years old and you " + ( yummy.getValue() ? "like" : "don't like" ) + " chocolate.";
		MessageDialog.showDialog( str );
	}

	private void loadAndShow()
	{
		final String imagePath = "samples/Cont1.lsm";

		// Load in a separate thread.
		ThreadUtil.bgRun( () -> {

			final Sequence sequence = Loader.loadSequence( imagePath, 0, true );

			// Display the images.
			try
			{
				SwingUtilities.invokeAndWait( () -> new Viewer( sequence ) );
			}
			catch ( final InvocationTargetException | InterruptedException e )
			{
				IcyLogger.warn( getClass(), e );
			}
		} );
	}

	/**
	 * Only for test purpose.
	 */
	public static void main( final String[] args )
	{
		// Launch the application.
		Icy.main( args );

		/*
		 * Programmatically launch a plugin, as if the user had clicked its
		 * button.
		 */
		PluginLauncher.start( ExtensionLoader.getPlugin( MyEzPlugIcyPlugin.class.getName() ) );
	}
}
