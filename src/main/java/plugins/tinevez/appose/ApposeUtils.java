package plugins.tinevez.appose;

import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apposed.appose.Builder.ProgressConsumer;
import org.bioimageanalysis.icy.gui.frame.progress.ProgressFrame;
import org.bioimageanalysis.icy.system.logging.IcyLogger;

public class ApposeUtils
{

	public static IcyApposeLogger apposeLogger( final Class< ? > callerKlass )
	{
		return new IcyApposeLogger( callerKlass );
	}

	public static class IcyApposeLogger implements AutoCloseable
	{
		private final ProgressFrame pf;

		private final Class< ? > klass;

		private final ProgressConsumer progressConsumer;

		private final Logger logger;

		public IcyApposeLogger( final Class< ? > klass )
		{
			this.klass = klass;
			this.pf = new ProgressFrame( klass.getSimpleName() );
			this.progressConsumer = ( title, current, maximum ) -> {
				pf.setMessage( title );
				pf.notifyProgress( current, maximum );
			};
			this.logger = LogManager.getLogger( klass );
		}

		public void logInfo( final String msg )
		{
			IcyLogger.info( klass, msg );
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
