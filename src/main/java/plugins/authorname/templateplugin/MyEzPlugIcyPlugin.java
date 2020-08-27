package plugins.authorname.templateplugin;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;

import icy.file.Loader;
import icy.gui.dialog.MessageDialog;
import icy.gui.viewer.Viewer;
import icy.main.Icy;
import icy.plugin.PluginLauncher;
import icy.plugin.PluginLoader;
import icy.sequence.Sequence;
import icy.system.thread.ThreadUtil;
import plugins.adufour.ezplug.EzButton;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzVarBoolean;
import plugins.adufour.ezplug.EzVarInteger;

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
		final String str = "This plugin start button does not do anything useful.\n "
				+ "You say you are " + age.getValue() + " years old and you "
				+ ( yummy.getValue().booleanValue() ? "like" : "don't like" )
				+ " chocolate.";
		MessageDialog.showDialog( str );
	}

	private void loadAndShow()
	{
		final String imagePath = "samples/Cont1.lsm";

		// Load in a separate thread.
		ThreadUtil.bgRun( new Runnable()
		{
			@Override
			public void run()
			{

				final Sequence sequence = Loader.loadSequence( imagePath, 0, true );

				// Display the images.
				try
				{
					SwingUtilities.invokeAndWait( () -> {
						new Viewer( sequence );
					} );
				}
				catch ( InvocationTargetException | InterruptedException e )
				{
					e.printStackTrace();
				}
			}
		} );
	}

	public static void main( final String[] args )
	{
		// Launch the application.
		Icy.main( args );

		/*
		 * Programmatically launch a plugin, as if the user had clicked its
		 * button.
		 */
		PluginLauncher.start( PluginLoader.getPlugin( MyEzPlugIcyPlugin.class.getName() ) );
	}
}
