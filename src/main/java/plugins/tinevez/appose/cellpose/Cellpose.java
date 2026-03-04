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

package plugins.tinevez.appose.cellpose;

import static plugins.tinevez.appose.ApposeUtils.apposeLogger;
import static plugins.tinevez.appose.ApposeUtils.getDimensionality;
import static plugins.tinevez.appose.ApposeUtils.getGlasbeyDarkLUT;
import static plugins.tinevez.appose.ApposeUtils.loadScript;
import static plugins.tinevez.appose.ApposeUtils.pixiEnv;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
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
import org.bioimageanalysis.icy.model.roi.ROI;
import org.bioimageanalysis.icy.model.sequence.Sequence;
import org.bioimageanalysis.icy.system.logging.IcyLogger;
import org.bioimageanalysis.icy.system.thread.ThreadUtil;

import net.imglib2.appose.NDArrays;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import plugins.adufour.roi.LabelExtractor;
import plugins.adufour.roi.LabelExtractor.ExtractionType;
import plugins.tinevez.appose.ApposeUtils.IcyApposeLogger;
import plugins.tinevez.imglib2icy.ImgLib2IcyFunctions;

public class Cellpose extends PluginActionable
{

	@Override
	public void run()
	{
		ThreadUtil.bgRun( () -> {

			final Sequence sequence = getActiveSequence();
			if ( sequence == null )
			{
				MessageDialog.showDialog( "This plugin needs an opened sequence." );
				return;
			}
			try
			{
				final Sequence output = process( sequence );

				final List< ROI > rois = LabelExtractor.extractLabels( output, ExtractionType.ALL_LABELS_VS_BACKGROUND, 0 );
				final List< Color > colors = getGlasbeyDarkLUT();
				for ( int i = 0; i < rois.size(); i++ )
				{
					final ROI roi = rois.get( i );
					final Color color = colors.get( i % colors.size() );
					roi.setColor( color );
				}
				sequence.removeAllROI( true );
				sequence.addROIs( rois, true );

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
		final String dimensionality = getDimensionality( sequence );
		@SuppressWarnings( { "unchecked", "rawtypes" } )
		final Img out = process( img, dimensionality );
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

	private < T extends RealType< T > & NativeType< T > > Img< T > process( final Img< T > img, final String dimensionality ) throws Exception
	{
		Thread.currentThread().setContextClassLoader( FastStringService.class.getClassLoader() );

		try (final IcyApposeLogger apposeLogger = apposeLogger( getClass() );)
		{
			// Inputs.
			final NDArray ndArray = NDArrays.asNDArray( img );
			// Get axes order.
			final Map< String, Integer > axesOrder = new HashMap<>();
			for ( int d = 0; d < img.numDimensions(); d++ )
			{
				final int flippedD = img.numDimensions() - d - 1;
				axesOrder.put( "" + dimensionality.charAt( d ), flippedD );
			}

			// Copy the input to a shared memory image.
			final Map< String, Object > inputs = new HashMap<>();
			inputs.put( "image", ndArray );
			inputs.put( "axes", axesOrder );

			// Environment.
			final Environment env = Appose
					.pixi()
					.content( pixiEnv( "/cellpose-pixi.toml" ) )
//					.subscribeProgress( apposeLogger.progressLogger() )
//					.subscribeOutput( apposeLogger.infoLogger() )
//					.subscribeError( apposeLogger.errorLogger() )
					.build();

			// Python service.
			try (final Service python = env.python())
			{
				// Appose task.
				final Task task = python.task( getScript(), inputs );
				task.listen( e -> apposeLogger.logInfo( e.message ) );

				// Start the script, and return to Java immediately.
				apposeLogger.logInfo( "Starting task" );
				final long start = System.currentTimeMillis();
				task.start();
				apposeLogger.logInfo( "Task started" );

				task.waitFor();
				apposeLogger.logInfo( "Task finished with status: " + task.status );

				// Verify that it worked.
				if ( task.status != TaskStatus.COMPLETE )
					throw new RuntimeException( "Python script failed with status: " + task.status );

				// Benchmark.
				final long end = System.currentTimeMillis();
				apposeLogger.logInfo( "Task finished in " + ( end - start ) / 1000. + " s" );

				final NDArray maskArr = ( NDArray ) task.outputs.get( "masks" );
				apposeLogger.logInfo( "Received output from Python: " + maskArr );
				final Img< T > output = NDArrays.asArrayImg( maskArr );
				return output;
			}
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
		}
		return null;
	}

	private static String getScript()
	{
		final String template = loadScript( "/CellposeAppose2DBatch.py" );

		final Map< String, String > settings = Map.of(
				"${--pretrained_model}", "cyto3",
				"${--use_gpu}", "True",
				"${--diameter}", "30",
				"${--chan}", "2",
				"${--chan2}", "1" );
		String script = template;
		for ( final Map.Entry< String, String > entry : settings.entrySet() )
			script = script.replace( entry.getKey(), entry.getValue() );
		return script;
	}
}
