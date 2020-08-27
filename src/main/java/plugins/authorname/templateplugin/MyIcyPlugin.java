package plugins.authorname.templateplugin;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;

import icy.file.Loader;
import icy.gui.dialog.MessageDialog;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;
import icy.main.Icy;
import icy.plugin.abstract_.PluginActionable;
import icy.sequence.Sequence;
import icy.sequence.SequenceUtil;
import icy.system.thread.ThreadUtil;

/**
 * A simple plugin demonstration.
 * <p>
 * If a class extends {@link PluginActionable} it will be visible in Icy and
 * executable from the push of a button.
 * <p>
 * This is toy that shows how to make a plugin that can be launched from Icy and
 * that will MODIFY an image.
 * 
 * @author Jean-Yves Tinevez
 *
 */
public class MyIcyPlugin extends PluginActionable
{

	/*
	 * The run() method is called when the user presses the plugin button.
	 */

	@Override
	public void run()
	{
		/*
		 * We are in the Event Dispatch Thread (EDT) right now. It is a good
		 * idea to jump into another thread so that the processing of our plugin
		 * does not prevent the EDT to deal with the user-interface.
		 */

		ThreadUtil.bgRun( new Runnable()
		{
			@Override
			public void run()
			{

				/*
				 * We are are not the EDT anymore. Now we can do heavy-lifting
				 * operations and the Icy UI won't be blocked.
				 */

				final Sequence sequence = getActiveSequence();

				// Check if a sequence is opened.
				if ( sequence == null )
				{
					MessageDialog.showDialog( "This plugin needs an opened sequence." );
					return;
				}

				final int width = sequence.getSizeX();
				final int height = sequence.getSizeY();
				final int nChannel = sequence.getSizeC();

				// Iterate over all the planes of the image, through C and Z.
				for ( final IcyBufferedImage plane : sequence.getAllImage() )
				{
					try
					{
						/*
						 * We put the pixel edit between a beginUpdate() and an
						 * endUpdate (all in a try/finally block), so that we
						 * only display the display (which takes time) once the
						 * edits of a plane are over.
						 * 
						 * We could also have chosen to put the beginUpdate()
						 * before editing the sequence to have only one display
						 * update.
						 */

						plane.beginUpdate();

						/*
						 * We pedestriantly iterate pixel by pixel and recopy
						 * the value of the every 7th pixel on the 2 that
						 * follows.
						 */

						for ( int c = 0; c < nChannel; c++ )
						{
							for ( int y = 0; y < height; y++ )
							{
								double val = 0;
								for ( int x = 0; x < width; x++ )
								{
									if ( x % 7 == 0 )
										val = plane.getData( x, y, c );

									plane.setData( x, y, c, val );
								}
							}
						}
					}
					finally
					{
						plane.endUpdate();
					}
				}
			}
		} );
	}

	/*
	 * We use a main method to make this class runnable from your favorite IDE.
	 * If you run this class, it will simply starts Icy. Your preferences won't
	 * be there however, and it will be a naked" icy.
	 * 
	 * Custom plugins with no extra configuration will show up in the "Plugins"
	 * ribbons, under the "Other Plugins" button. This plugin button gets
	 * automatically placed in the "authorname" menu. This is why by convention
	 * Icy plugins have a package that always starts with "plugins.authorname".
	 */

	public static void main( final String[] args ) throws InvocationTargetException, InterruptedException
	{
		// Launch the application.
		Icy.main( args );

		// Load an image.
		final String imagePath = "samples/Cont1.lsm";
		final Sequence sequence = Loader.loadSequence( imagePath, 0, true );

		// Copy it so that we work on a copy.
		final Sequence copy = SequenceUtil.getCopy( sequence );

		// Display the images.
		SwingUtilities.invokeAndWait( () -> {
			new Viewer( sequence );
			new Viewer( copy );
		} );

		// Run the plugin on the last active image (the copy).
		new MyIcyPlugin().run();
	}
}
