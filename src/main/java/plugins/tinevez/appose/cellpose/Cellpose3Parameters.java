package plugins.tinevez.appose.cellpose;

import java.util.List;
import java.util.Optional;

/**
 * Data class to hold parameters for Cellpose segmentation.
 */
public record Cellpose3Parameters(
		// Core parameters
		Cellpose3Model model,
		double diameter,
		boolean do3D,

		// Channel specifications
		List< Integer > channels,
		boolean invert,
		boolean normalize,

		// Advanced parameters
		double flowThreshold,
		double cellProbThreshold,
		boolean useGpu,
		int device,
		int batchSize,

		// Output options
		boolean saveOutput,
		String outputDirectory,
		String outputType,

		// Additional options
		boolean fastMode,
		boolean diamMean,
		boolean netAvg,
		Optional< Double > minSize,
		Optional< Double > maxSize,

		// Transformer-specific parameters
		Optional< Integer > transformerNumLayers,
		Optional< Integer > transformerNumHeads,

		// Model-specific parameters
		Optional< String > pretrainedModelPath,
		Optional< String > modelDir )
{
	// Default constructor with validation
	public Cellpose3Parameters
	{
		// Validate channels
		if ( channels == null || channels.size() != 2 )
			throw new IllegalArgumentException( "Channels must be a list of 2 integers" );

		// Validate thresholds
		if ( flowThreshold < 0 || flowThreshold > 1 )
			throw new IllegalArgumentException( "Flow threshold must be between 0 and 1" );
		if ( cellProbThreshold < 0 || cellProbThreshold > 1 )
			throw new IllegalArgumentException( "Cell probability threshold must be between 0 and 1" );

		// Validate diameter
		if ( diameter < 0 && diameter != 0 )
		{ // 0 is allowed for auto-diameter
			throw new IllegalArgumentException( "Diameter must be positive or 0 for auto-detection" );
		}

		// Validate batch size
		if ( batchSize <= 0 )
			throw new IllegalArgumentException( "Batch size must be positive" );
	}

	// Static builder method for convenience
	public static Builder builder()
	{
		return new Builder();
	}

	// Builder class for fluent construction
	public static class Builder
	{
		private Cellpose3Model model = Cellpose3Model.CYTO3;

		private double diameter = 30.0;

		private boolean do3D = false;

		private List< Integer > channels = List.of( 0, 0 );

		private boolean invert = false;

		private boolean normalize = true;

		private double flowThreshold = 0.4;

		private double cellProbThreshold = 0.0;

		private boolean useGpu = true;

		private int device = 0;

		private int batchSize = 8;

		private boolean saveOutput = false;

		private String outputDirectory = ".";

		private String outputType = "png";

		private boolean fastMode = false;

		private boolean diamMean = true;

		private boolean netAvg = false;

		private Optional< Double > minSize = Optional.empty();

		private Optional< Double > maxSize = Optional.empty();

		private Optional< Integer > transformerNumLayers = Optional.empty();

		private Optional< Integer > transformerNumHeads = Optional.empty();

		private Optional< String > pretrainedModelPath = Optional.empty();

		private Optional< String > modelDir = Optional.empty();

		public Builder model( final Cellpose3Model model )
		{
			this.model = model;
			return this;
		}

		public Builder diameter( final double diameter )
		{
			this.diameter = diameter;
			return this;
		}

		public Builder do3D( final boolean do3D )
		{
			this.do3D = do3D;
			return this;
		}

		public Builder channels( final List< Integer > channels )
		{
			this.channels = channels;
			return this;
		}

		public Builder channels( final int channel1, final int channel2 )
		{
			this.channels = List.of( channel1, channel2 );
			return this;
		}

		public Builder invert( final boolean invert )
		{
			this.invert = invert;
			return this;
		}

		public Builder normalize( final boolean normalize )
		{
			this.normalize = normalize;
			return this;
		}

		public Builder flowThreshold( final double flowThreshold )
		{
			this.flowThreshold = flowThreshold;
			return this;
		}

		public Builder cellProbThreshold( final double cellProbThreshold )
		{
			this.cellProbThreshold = cellProbThreshold;
			return this;
		}

		public Builder useGpu( final boolean useGpu )
		{
			this.useGpu = useGpu;
			return this;
		}

		public Builder device( final int device )
		{
			this.device = device;
			return this;
		}

		public Builder batchSize( final int batchSize )
		{
			this.batchSize = batchSize;
			return this;
		}

		public Builder saveOutput( final boolean saveOutput )
		{
			this.saveOutput = saveOutput;
			return this;
		}

		public Builder outputDirectory( final String outputDirectory )
		{
			this.outputDirectory = outputDirectory;
			return this;
		}

		public Builder outputType( final String outputType )
		{
			this.outputType = outputType;
			return this;
		}

		public Builder fastMode( final boolean fastMode )
		{
			this.fastMode = fastMode;
			return this;
		}

		public Builder diamMean( final boolean diamMean )
		{
			this.diamMean = diamMean;
			return this;
		}

		public Builder netAvg( final boolean netAvg )
		{
			this.netAvg = netAvg;
			return this;
		}

		public Builder minSize( final double minSize )
		{
			this.minSize = Optional.of( minSize );
			return this;
		}

		public Builder maxSize( final double maxSize )
		{
			this.maxSize = Optional.of( maxSize );
			return this;
		}

		public Builder transformerNumLayers( final int numLayers )
		{
			this.transformerNumLayers = Optional.of( numLayers );
			return this;
		}

		public Builder transformerNumHeads( final int numHeads )
		{
			this.transformerNumHeads = Optional.of( numHeads );
			return this;
		}

		public Builder pretrainedModelPath( final String path )
		{
			this.pretrainedModelPath = Optional.of( path );
			return this;
		}

		public Builder modelDir( final String dir )
		{
			this.modelDir = Optional.of( dir );
			return this;
		}

		public Cellpose3Parameters build()
		{
			return new Cellpose3Parameters(
					model, diameter, do3D, channels, invert, normalize,
					flowThreshold, cellProbThreshold, useGpu, device, batchSize,
					saveOutput, outputDirectory, outputType, fastMode, diamMean,
					netAvg, minSize, maxSize, transformerNumLayers, transformerNumHeads,
					pretrainedModelPath, modelDir );
		}
	}

	// Default parameters for common use cases
	public static Cellpose3Parameters defaultCytoParameters()
	{
		return builder()
				.model( Cellpose3Model.CYTO3 )
				.channels( 0, 0 )
				.diameter( 30.0 )
				.build();
	}

	public static Cellpose3Parameters defaultNucleiParameters()
	{
		return builder()
				.model( Cellpose3Model.NUCLEI )
				.channels( 0, 0 )
				.diameter( 17.0 )
				.build();
	}
}
