package plugins.tinevez.appose;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apposed.appose.Builder.ProgressConsumer;
import org.apposed.appose.TaskEvent;

import fr.icy.common.geom.point.Point5D;
import fr.icy.common.geom.rectangle.Rectangle5D;
import fr.icy.common.type.DataIteratorUtil;
import fr.icy.extension.kernel.roi.roi3d.ROI3DBox;
import fr.icy.gui.frame.progress.ProgressFrame;
import fr.icy.model.colormap.IcyColorMap;
import fr.icy.model.roi.ROI;
import fr.icy.model.sequence.Sequence;
import fr.icy.model.sequence.SequenceDataIterator;
import fr.icy.system.logging.IcyLogger;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cellpose.ApposeTaskListener;
import net.imglib2.cellpose.AxisInfo;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import plugins.adufour.ezplug.EzStatus;
import plugins.tinevez.imglib2icy.ImgLib2IcyFunctions;

public class ApposeUtils
{

	private static List< Color > loadColorsFromResource( final String resourcePath )
	{
		final List< Color > colors = new ArrayList<>( 256 );
		try (InputStream is = ApposeUtils.class.getResourceAsStream( resourcePath );
				BufferedReader reader = new BufferedReader( new InputStreamReader( is ) ))
		{

			if ( is == null )
			{
				IcyLogger.error( ApposeUtils.class, "LUT resource not found: " + resourcePath );
				return null;
			}

			String line;
			while ( ( line = reader.readLine() ) != null )
			{
				line = line.trim();
				if ( line.isEmpty() )
					continue; // Skip empty lines

				// Split by whitespace
				final String[] parts = line.split( "\\s+" );
				if ( parts.length >= 3 )
				{
					final byte r = ( byte ) Integer.parseInt( parts[ 0 ] );
					final byte g = ( byte ) Integer.parseInt( parts[ 1 ] );
					final byte b = ( byte ) Integer.parseInt( parts[ 2 ] );
					final Color color = new Color( r & 0xFF, g & 0xFF, b & 0xFF );
					colors.add( color );
				}
			}
		}
		catch ( final IOException e )
		{
			IcyLogger.error( ApposeUtils.class, "Error reading LUT resource: " + resourcePath );
		}
		return colors;
	}

	public static final List< Color > getGlasbeyDarkColors()
	{
		return loadColorsFromResource( "/glasbey_on_dark.lut" );
	}

	private static long[] getDims( final Sequence sequence )
	{
		final int sizeX = sequence.getSizeX();
		final int sizeY = sequence.getSizeY();
		final int sizeC = sequence.getSizeC();
		final int sizeZ = sequence.getSizeZ();
		final int sizeT = sequence.getSizeT();
		return new long[] { sizeX, sizeY, sizeC, sizeZ, sizeT };
	}

	public static AxisInfo getAxisInfo( final Sequence sequence )
	{
		final long[] dims = getDims( sequence );
		// Icy is always XYCZT.
		final int nx = dims[ 0 ] > 1 ? 0 : -1;
		final int ny = dims[ 1 ] > 1 ? 1 : -1;
		final int nc = dims[ 2 ] > 1 ? 2 : -1;
		final int nz = dims[ 3 ] > 1 ? 3 : -1;
		final int nt = dims[ 4 ] > 1 ? 4 : -1;
		return new AxisInfo( nx, ny, nc, nz, nt );
	}

	public static IcyApposeLogger apposeLogger( final Class< ? > callerKlass )
	{
		return new IcyApposeLogger( callerKlass );
	}

	public static IcyApposeEzLogger apposeEzLogger( final Class< ? > callerKlass, final EzStatus status )
	{
		return new IcyApposeEzLogger( status, callerKlass );
	}

	public static class IcyApposeLogger implements ApposeTaskListener
	{
		private final ProgressFrame pf;

		private final ProgressConsumer progressConsumer;

		private final Class< ? > klass;

		private IcyApposeLogger( final Class< ? > klass )
		{
			this.klass = klass;
			this.pf = new ProgressFrame( klass.getSimpleName() );
			this.progressConsumer = ( title, current, maximum ) -> {
				if ( title != null && !title.isBlank() )
				{
					IcyLogger.info( klass, title );
					pf.setMessage( title );
				}
				pf.notifyProgress( current, maximum );
			};
		}

		public void logInfo( final String msg )
		{
			if ( msg != null )
				IcyLogger.info( klass, msg );
		}

		@Override
		public Consumer< TaskEvent > taskListener()
		{
			return e -> {
				if ( e.message != null && !e.message.trim().isEmpty() )
					logInfo( e.responseType + ": " + e.message );
				if ( e.current >= 0 && e.maximum > 0 )
					progressConsumer.accept( e.message, e.current, e.maximum );
			};
		}

		@Override
		public Consumer< String > outputListener()
		{
			return s -> logInfo( s );
		}

		@Override
		public Consumer< String > errorListener()
		{
			return str -> {
				if ( str != null && str.contains( "✔ The" ) && str.contains( "environment has been installed." ) )
				{
					final String envName = str.substring( str.indexOf( "✔ The" ) + 5, str.indexOf( "environment" ) );
					logInfo( "Python environment " + envName + " is ready." );
				}
				else
				{
					IcyLogger.error( klass, str );
				}
			};
		}

		@Override
		public ProgressConsumer progressListener()
		{
			return progressConsumer;
		}

		@Override
		public void message( final String msg )
		{
			logInfo( msg );
		}
	}

	private static class IcyApposeEzLogger implements ApposeTaskListener
	{
		private final EzStatus status;

		private final ProgressConsumer progressConsumer;

		private final Class< ? > klass;

		private IcyApposeEzLogger( final EzStatus status, final Class< ? > klass )
		{
			this.status = status;
			this.klass = klass;
			this.progressConsumer = ( title, current, maximum ) -> {
				if ( title != null && !title.isBlank() )
					status.setMessage( title );
				status.setCompletion( ( double ) current / maximum );
			};
		}

		@Override
		public Consumer< TaskEvent > taskListener()
		{
			return e -> {
				if ( e.message != null && !e.message.trim().isEmpty() )
					status.setMessage( e.responseType + ": " + e.message );
				if ( e.current >= 0 && e.maximum > 0 )
					progressConsumer.accept( e.message, e.current, e.maximum );
			};
		}

		@Override
		public Consumer< String > outputListener()
		{
			return s -> status.setMessage( s );
		}

		@Override
		public Consumer< String > errorListener()
		{
			return str -> {
				/*
				 * We have an issue here: pixi always return an error message
				 * that says "✔ The cp4-cpu environment has been installed."
				 * when the environment is ready, even if it was already
				 * installed. So we need to filter out this message to avoid
				 * showing an error dialog.
				 */
				if ( str != null && str.contains( "✔ The" ) && str.contains( "environment has been installed." ) )
				{
					final String envName = str.substring( str.indexOf( "✔ The" ) + 5, str.indexOf( "environment" ) );
					status.setMessage( "Python environment " + envName + " is ready." );
				}
				else
				{
					IcyLogger.error( klass, str );
				}
			};
		}

		@Override
		public ProgressConsumer progressListener()
		{
			return progressConsumer;
		}

		@Override
		public void message( final String msg )
		{
			status.setMessage( msg );
		}
	}

	public static IcyColorMap getGlasbeyDarkColorMap()
	{
		return new GlasbeyDarkColoMap();
	}

	private static class GlasbeyDarkColoMap extends IcyColorMap
	{

		private int nColors;

		public GlasbeyDarkColoMap()
		{
			super( "Glasbey on dark" );

			final List< Color > colors = getGlasbeyDarkColors();
			beginUpdate();
			try
			{
				this.nColors = colors.size();
				for ( int i = 0; i < nColors; i++ )
					setRGB( i, colors.get( i ) );
			}
			finally
			{
				endUpdate();
			}
		}

		@Override
		public Color getColor( final int index )
		{
			return super.getColor( index % nColors );
		}
	}

	/**
	 * Wraps the specified Icy sequence into an ImgLib2 Img. If the input
	 * sequence has a focused ROI, then the returned Img will be a view on the
	 * ROI, otherwise it will be a view on the whole sequence.
	 *
	 * @param <T>
	 *            the pixel type of the returned Img.
	 * @param input
	 *            the Icy sequence to wrap.
	 * @param roi
	 *            the ROI to focus on, or <code>null</code> to use the whole
	 *            sequence. Warning: the ROI, if not <code>null</code>, must be
	 *            fully contained within the bounds of the sequence. If it
	 *            extends beyond, you will get OutOfBounds exceptions at some
	 *            point.
	 * @return a view on the specified sequence.
	 */
	public static < T extends RealType< T > & NativeType< T > > RandomAccessibleInterval< T > toImg( final Sequence input, final ROI roi )
	{
		final Img< T > img = ImgLib2IcyFunctions.wrap( input );
		if ( roi == null )
			return img;

		final Rectangle5D bounds = roi.getBounds5D();
		final long min[] = img.minAsLongArray();
		final long max[] = img.maxAsLongArray();
		min[ 0 ] = ( long ) bounds.getMinX();
		max[ 0 ] = ( long ) bounds.getMaxX() - 1;
		min[ 1 ] = ( long ) bounds.getMinY();
		max[ 1 ] = ( long ) bounds.getMaxY() - 1;
		if ( max.length > 2 )
		{
			min[ 2 ] = ( long ) bounds.getMinZ();
			max[ 2 ] = ( long ) bounds.getMaxZ() - 1;
		}
		final FinalInterval interval = new FinalInterval( min, max );
		return Views.interval( img, interval );
	}

	public static final < T extends NativeType< T > & IntegerType< T > > void clearOutsideRoi( final Sequence sequence, final ROI roi )
	{
		if ( roi == null )
			return;

		try
		{
			// Move the ROI to the origin.
			final ROI copy = roi.getCopy();
			final Point5D origin = copy.getPosition5D();
			origin.setX( 0. );
			origin.setY( 0. );
			origin.setZ( 0. );
			copy.setPosition5D( origin );
			// Inverse and clear.
			final ROI roiSeq = new ROI3DBox( sequence.getBounds5D().toRectangle3D() );
			final ROI inverse = roiSeq.getSubtraction( copy );
			final double value = 0.;
			DataIteratorUtil.set( new SequenceDataIterator( sequence, inverse ), value );
		}
		catch ( UnsupportedOperationException | InterruptedException e )
		{
			e.printStackTrace();
		}
	}
}
