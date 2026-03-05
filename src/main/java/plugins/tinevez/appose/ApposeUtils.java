package plugins.tinevez.appose;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apposed.appose.Builder.ProgressConsumer;
import org.bioimageanalysis.icy.gui.frame.progress.ProgressFrame;
import org.bioimageanalysis.icy.model.colormap.IcyColorMap;
import org.bioimageanalysis.icy.model.sequence.Sequence;
import org.bioimageanalysis.icy.system.logging.IcyLogger;

import plugins.adufour.ezplug.EzStatus;

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

	public static String getDimensionality( final Sequence sequence )
	{
		final long[] dims = getDims( sequence );
		final String start = "XYCZT"; // Always like this in Icy.
		final StringBuilder sb = new StringBuilder();
		for ( int i = 0; i < dims.length; i++ )
			if ( dims[ i ] > 1 )
				sb.append( start.charAt( i ) );
		return sb.toString();
	}

	/**
	 * Utility method to load a Pixi environment from resources.
	 *
	 * @param resourcePath
	 *            the resource path.
	 * @return the Pixi environment as a string.
	 */
	public static String pixiEnv( final String resourcePath )
	{
		String env = "";
		try
		{
			final URL pixiFile = ApposeUtils.class.getResource( resourcePath );
			env = IOUtils.toString( pixiFile, StandardCharsets.UTF_8 );

		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
		return env;
	}

	/**
	 * Utility method to load an Appose script template from resources.
	 *
	 * @param resourcePath
	 *            the resource path.
	 * @return the script template.
	 */
	public static String loadScript( final String resourcePath )
	{
		try
		{
			return new String( ApposeUtils.class.getResourceAsStream( resourcePath ).readAllBytes() );
		}
		catch ( final Exception e )
		{
			throw new RuntimeException( "Failed to load Appose script template from resources: " + resourcePath, e );
		}
	}

	public static IcyApposeLogger apposeLogger( final Class< ? > callerKlass )
	{
		return new IcyApposeLogger( callerKlass );
	}

	public static IcyApposeEzLogger apposeEzLogger( final EzStatus status )
	{
		return new IcyApposeEzLogger( status );
	}

	public static class IcyApposeLogger implements AutoCloseable
	{
		private final ProgressFrame pf;

		private final ProgressConsumer progressConsumer;

		private final Logger logger;

		public IcyApposeLogger( final Class< ? > klass )
		{
			this.pf = new ProgressFrame( klass.getSimpleName() );
			this.progressConsumer = ( title, current, maximum ) -> {
				pf.setMessage( title );
				pf.notifyProgress( current, maximum );
			};
			this.logger = LogManager.getLogger( klass );
		}

		public void logInfo( final String msg )
		{
//			IcyLogger.info( klass, msg );
			System.out.println( msg ); // DEBUG
		}

		public void logError( final String msg )
		{
			logger.error( msg );
		}

		public void logProgress( final String title, final long current, final long maximum )
		{
			progressConsumer.accept( title, current, maximum );
		}

		public ProgressConsumer progressLogger()
		{
			return progressConsumer;
		}

		public Consumer< String > infoLogger()
		{
			return this::logInfo;
		}

		public Consumer< String > errorLogger()
		{
			return this::logError;
		}

		@Override
		public void close() throws Exception
		{
			pf.close();
		}
	}

	public static class IcyApposeEzLogger
	{
		private final EzStatus status;

		private final ProgressConsumer progressConsumer;

		public IcyApposeEzLogger( final EzStatus status )
		{
			this.status = status;
			this.progressConsumer = ( title, current, maximum ) -> {
				logInfo( title );
				status.setCompletion( ( double ) current / maximum );
			};
		}

		public void logInfo( final String msg )
		{
			if ( msg != null )
				status.setMessage( msg );
		}

		public void logError( final String msg )
		{
			logInfo( msg );
		}

		public void logProgress( final String title, final long current, final long maximum )
		{
			progressConsumer.accept( title, current, maximum );
		}

		public ProgressConsumer progressLogger()
		{
			return progressConsumer;
		}

		public Consumer< String > infoLogger()
		{
			return this::logInfo;
		}

		public Consumer< String > errorLogger()
		{
			return this::logError;
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
}
