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

import java.util.HashMap;
import java.util.Map;

import javax.swing.SwingUtilities;

import org.apache.groovy.json.FastStringService;
import org.apposed.appose.Appose;
import org.apposed.appose.Environment;
import org.apposed.appose.NDArray;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;
import org.apposed.appose.Service.TaskStatus;
import org.bioimageanalysis.icy.extension.plugin.abstract_.PluginActionable;
import org.bioimageanalysis.icy.gui.dialog.MessageDialog;
import org.bioimageanalysis.icy.gui.viewer.Viewer;
import org.bioimageanalysis.icy.model.sequence.Sequence;
import org.bioimageanalysis.icy.system.logging.IcyLogger;
import org.bioimageanalysis.icy.system.thread.ThreadUtil;

import net.imglib2.appose.NDArrays;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import plugins.tinevez.ApposeUtils.IcyApposeLogger;
import plugins.tinevez.imglib2icy.ImgLib2IcyFunctions;

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

		ThreadUtil.bgRun( () -> {

			/*
			 * We are not the EDT anymore. Now we can do heavy-lifting
			 * operations and the Icy UI won't be blocked.
			 */

			final Sequence sequence = getActiveSequence();
			if ( sequence == null )
			{
				MessageDialog.showDialog( "This plugin needs an opened sequence." );
				return;
			}
			try
			{
				final Sequence output = process( sequence );
				SwingUtilities.invokeAndWait( () -> new Viewer( output ) );
			}
			catch ( final Exception e )
			{
				IcyLogger.warn( getClass(), e );
			}
		} );
	}

	public Sequence process( final Sequence sequence ) throws Exception
	{
		@SuppressWarnings( "rawtypes" )
		final Img img = ImgLib2IcyFunctions.wrap( sequence );
		@SuppressWarnings( { "unchecked", "rawtypes" } )
		final Img out = process( img );
		@SuppressWarnings( "unchecked" )
		final Sequence output = ImgLib2IcyFunctions.wrap( out );

		output.setName( "Cellpose output - " + sequence.getName() );
		output.setChannelName( 0, "Cellpose labels" );
		output.setPixelSizeX( sequence.getPixelSizeX() );
		output.setPixelSizeY( sequence.getPixelSizeY() );
		output.setPixelSizeZ( sequence.getPixelSizeZ() );
		output.setTimeInterval( sequence.getTimeInterval() );

		return output;
	}

	private < T extends RealType< T > & NativeType< T > > Img< T > process( final Img< T > img ) throws Exception
	{
		/*
		 * Required to avoid a Groovy classloader issue that can happen in some
		 * environments. This is a temporary workaround until we have a better
		 * solution for this in Appose. Basically, Groovy uses a service loader
		 * to load the FastStringService class, and it uses the context class
		 * loader of the current thread to do this. In some environments, this
		 * context class loader does not have access to the FastStringService
		 * class, which causes a ClassNotFoundException. By setting the context
		 * class loader to the class loader that loaded the FastStringService
		 * class, we ensure that Groovy can find it and load it properly.
		 */
		Thread.currentThread().setContextClassLoader( FastStringService.class.getClassLoader() );

		/*
		 * Let's create a nice progress dialog for our plugin, that integrates
		 * well with the Icy UI.
		 */
		try (final IcyApposeLogger apposeLogger = ApposeUtils.apposeLogger( getClass() );)
		{
			/*
			 * Copy the image into a shared memory image and wrap it into an
			 * NDArray, then store it in an input map that we will pass to the
			 * Python script.
			 * 
			 * Note that we could have passed multiple inputs to the Python
			 * script by putting more entries in the input map, and they would
			 * all be available in the Python script as shared memory NDArrays.
			 * 
			 * A ND array is a multi-dimensional array that is stored in shared
			 * memory, that can be unwrapped as a NumPy array in Python, and
			 * wrapped as a ImgLib2 image in Java.
			 * 
			 */
			final Map< String, Object > inputs = new HashMap<>();
			final NDArray ndArray = NDArrays.asNDArray( img );
			inputs.put( "image", ndArray );

			/*
			 * Create or retrieve the environment.
			 * 
			 * The first time this code is run, Appose will create the mamba
			 * environment as specified by the cellposeEnv string, download and
			 * install the dependencies. This can take a few minutes, but it is
			 * only done once. The next time the code is run, Appose will just
			 * reuse the existing environment, so it will start much faster.
			 */
			final Environment env = Appose
					.uv()
					.name( envName() )
					.include( "scikit-image" )
					.include( "appose" )
					.subscribeProgress( apposeLogger.progressLogger() )
					.subscribeOutput( apposeLogger.infoLogger() )
					.subscribeError( apposeLogger.errorLogger() )
					.build();

			/*
			 * Using this environment, we create a service that will run the
			 * Python script.
			 */
			try (final Service python = env.python())
			{
				/*
				 * With this service, we can now create a task that will run the
				 * Python script with the specified inputs. This command takes
				 * the script as first argument, and a map of inputs as second
				 * argument. The keys of the map will be the variable names in
				 * the Python script, and the values are the data that will be
				 * passed to Python.
				 */
				final Task task = python.task( getScript(), inputs );
				task.listen( e -> apposeLogger.logInfo( e.message ) );

				// Start the script, and return to Java immediately.
				apposeLogger.logInfo( "Starting task" );
				final long start = System.currentTimeMillis();
				task.start();
				apposeLogger.logInfo( "Task started" );

				/*
				 * Wait for the script to finish. This will block the Java
				 * thread until the Python script is done, but it allows the
				 * Python code to run in parallel without blocking the Java
				 * thread while it is running.
				 */
				task.waitFor();
				apposeLogger.logInfo( "Task finished with status: " + task.status );

				// Verify that it worked.
				if ( task.status != TaskStatus.COMPLETE )
					throw new RuntimeException( "Python script failed with status: " + task.status );

				// Benchmark.
				final long end = System.currentTimeMillis();
				apposeLogger.logInfo( "Task finished in " + ( end - start ) / 1000. + " s" );

				/*
				 * Unwrap output.
				 * 
				 * In the Python script (see below), we create a new NDArray
				 * called 'rotated' that contains the result of the processing.
				 * Here we retrieve this NDArray from the task outputs, and wrap
				 * it into a ShmImg, which is an ImgLib2 image that is backed by
				 * shared memory. We can then display this image with
				 * ImageJFunctions.show(). Note that this does not involve any
				 * copying of the data, as the NDArray and the ShmImg are both
				 * just views on the same shared memory array.
				 */
				final NDArray maskArr = ( NDArray ) task.outputs.get( "rotated" );
				apposeLogger.logInfo( "Received output from Python: " + maskArr );
				final Img< T > output = NDArrays.asArrayImg( maskArr );
				return output;
			}
		}
	}

	private static String envName()
	{
		final String javaPackage = "plugins.tinevez.appose.cellpose";
		final String envNickname = "cellpose3";
		final String envVersion = "0.1";
		final String envSuffix = "icy";

		final String envName = String.format( "%s_%s_%s_%s",
				javaPackage.replace( '.', '_' ),
				envNickname.replace( '.', '_' ),
				envVersion.replace( '.', '_' ),
				envSuffix.replace( '.', '_' ) );
		return envName;
	}

	/*
	 * The Python script.
	 * 
	 * This is the Python code that will be run by the service. It is specified
	 * as a string here for simplicity, but it could be loaded from an existing
	 * .py file. In this example, the script receives an input image as a shared
	 * memory NDArray (the 'image' variable), rotates it by 90 degrees using
	 * scikit-image, and then sends the result back to Fiji by creating a new
	 * NDArray (the 'rotated' variable) and putting it in the task outputs.
	 * 
	 * The string is monolithic and has not parameters for simplicity, but you
	 * can imagine more complex scripts that take multiple inputs, have
	 * parameters, call functions defined in other .py files, etc. The only
	 * requirement is that the script can be run as a standalone script, and
	 * that it uses the appose library to receive inputs and send outputs.
	 * 
	 * To pass on-the-fly parameters, you can:
	 * 
	 * 1/ modify the string below before creating the task, by using replace,
	 * string concatenation, string format, or any other method to inject the
	 * parameters into the script string before it is run. This approach
	 * requires you to write the script as a template with placeholders for the
	 * parameters, and then fill in the placeholders with the actual parameters
	 * when you create the task.
	 * 
	 * 2/or you can use the input map to pass parameters as well, by putting
	 * them in the map with a specific key.
	 */
	private static String getScript()
	{
		return ""
				+ "import numpy as np\n"
				+ "import appose\n"
				+ "\n"
				+ "task.update('Hello from Python!')\n"
				+ "# The variable 'image' is automatically created by Appose from the \n"
				+ "# input map that we passed when creating the task. It is a shared \n"
				+ "# memory NDArray that can be unwrapped as a NumPy array.\n"
				+ "# Careful: the variable name 'image' MUST be the key that we used in \n"
				+ "# the input map in Java.\n"
				+ "task.update('Received image:' + str(image) )\n"
				+ "img = image.ndarray()\n"
				+ "task.update('Image shape:' + str(img.shape) )\n"
				+ "task.update('Image dtype:' + str(img.dtype) )\n"
				+ "\n"
				+ "# Now we have 'img' as a NumPy array.\n"
				+ "\n"
				+ "# Rotate the image by 90 degrees (counter-clockwise)\n"
				+ "rotated_image = np.rot90(img, k=1, axes=(-2, -1))\n"
				+ "task.update('Rotated image shape:' + str(rotated_image.shape) )\n"
				+ "task.update('Rotated image dtype:' + str(rotated_image.dtype) )\n"
				+ "\n"
				+ "# Output back to Fiji\n"
				+ "# First we create a NDArray placeholder, of the same type and shape as \n"
				+ "# the image we want to return.\n"
				+ "shared = appose.NDArray(str(rotated_image.dtype), rotated_image.shape)\n"
				+ "\n"
				+ "# Then we fill this placeholder with the data that we want to return.\n"
				+ "shared.ndarray()[:] = rotated_image\n"
				+ "\n"
				+ "# Finally, we put this NDArray in the task outputs with a specific key (here 'rotated'), \n"
				+ "# so that it can be retrieved from Java after the script is done. The key 'rotated' is \n"
				+ "# arbitrary, but it must be the same as the one we use in Java to retrieve the output.\n"
				+ "task.outputs['rotated'] = shared\n";
	}
}
