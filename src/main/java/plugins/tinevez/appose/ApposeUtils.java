package plugins.tinevez.appose;

import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apposed.appose.Builder.ProgressConsumer;
import org.bioimageanalysis.icy.gui.frame.progress.ProgressFrame;

public class ApposeUtils
{

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
}
