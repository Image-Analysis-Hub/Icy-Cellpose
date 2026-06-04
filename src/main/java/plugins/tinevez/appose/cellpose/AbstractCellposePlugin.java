package plugins.tinevez.appose.cellpose;

import static plugins.tinevez.appose.ApposeUtils.getGlasbeyDarkColors;

import java.awt.Color;
import java.util.List;

import org.apache.groovy.json.FastStringService;

import fr.icy.common.geom.point.Point5D;
import fr.icy.model.roi.ROI;
import fr.icy.model.sequence.Sequence;
import net.imglib2.cellpose.ApposeTaskListener;
import net.imglib2.cellpose.CellposeParameters;
import plugins.adufour.ezplug.EzGroup;
import plugins.adufour.ezplug.EzLabel;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzVarBoolean;
import plugins.adufour.ezplug.EzVarDouble;
import plugins.adufour.ezplug.EzVarInteger;
import plugins.adufour.ezplug.EzVarSequence;
import plugins.adufour.roi.LabelExtractor;
import plugins.adufour.roi.LabelExtractor.ExtractionType;
import plugins.adufour.vars.lang.VarSequence;
import plugins.tinevez.appose.ApposeUtils;

public abstract class AbstractCellposePlugin extends EzPlug
{

	protected final EzVarSequence ezSequence;

	// 1. Basic and commong Cellpose parameters group.

	protected final EzVarInteger ezDiameter;

	// 2. Segmentation quality group

	protected final EzVarDouble ezFlowThreshold;

	protected final EzVarDouble ezCellprobThreshold;

	protected final EzVarInteger ezMinSize;

	// 3. 3D processing parameters.

	protected final EzVarBoolean ezDo3D;

	protected final EzVarDouble ezStitchThreshold;

	// 4. Export options.

	protected EzVarBoolean ezExportROI;

	protected EzVarBoolean ezExportLabels;

	protected EzVarBoolean ezExportFlows;

	protected EzVarBoolean ezExportSwPool;

	// Others.

	protected final EzLabel ezInfo;

	// Unused yet.
	protected VarSequence outputSequence = new VarSequence( "cellpose output", null );

	// Groups.

	protected final EzGroup ezCellposeBasicParams;

	private final EzGroup ezExportOptions;

	private final EzGroup ezCellposeAdvancedParams;

	protected final EzGroup ez3DProcessing;

	protected AbstractCellposePlugin()
	{
		this.ezSequence = new EzVarSequence( "Input sequence" );

		// Cellpose basic parameters.

		this.ezDiameter = new EzVarInteger( "Diameter (pixels)", 30, 0, Integer.MAX_VALUE, 1 );
		ezDiameter.setToolTipText( "<html>Approximate diameter of the objects to segment, in pixels. "
				+ "If 0 will use the diameter of the training labels used in the model, "
				+ "or with built-in model will estimate diameter for each image</html>" );

		// Empty for now, to be filled by implementations.
		this.ezCellposeBasicParams = new EzGroup( "Cellpose basic parameters" );

		// Advanced parameters.

		this.ezFlowThreshold = new EzVarDouble( "Flow threshold", 0.4, 0., 3., 0.1 );
		ezFlowThreshold.setToolTipText( "<html>Threshold for flow error filtering. "
				+ "Lower = more masks (permissive), Higher = fewer masks (strict).</html>" );
		this.ezCellprobThreshold = new EzVarDouble( "Cell probability threshold", 0., -6., 6., 0.5 );
		ezCellprobThreshold.setToolTipText( "<html>Threshold for cell probability. "
				+ "Increase to filter low-confidence detections.</html>" );
		this.ezMinSize = new EzVarInteger( "Minimum size (pixels)", 15, 0, 10000, 1 );
		ezMinSize.setToolTipText( "<html>Minimum object size in pixels. "
				+ "Objects smaller than this are removed.</html>" );

		this.ezCellposeAdvancedParams = new EzGroup( "Advanced parameters",
				ezFlowThreshold, ezCellprobThreshold, ezMinSize );
		ezCellposeAdvancedParams.setFoldedState( true );

		// 3D Processing parameters

		this.ezDo3D = new EzVarBoolean( "3D segmentation", true );
		ezDo3D.setToolTipText( "<html>Run true 3D segmentation instead of slice-by-slice 2D.</html>" );
		this.ezStitchThreshold = new EzVarDouble( "Stitch threshold", 0.0, 0.0, 1.0, 0.1 );
		ezStitchThreshold.setToolTipText( "<html>Stitch masks across Z slices (only if 3D segmentation is off). "
				+ "0 = no stitching.</html>" );

		this.ez3DProcessing = new EzGroup( "3D processing",
				ezDo3D, ezStitchThreshold );
		ez3DProcessing.setFoldedState( true );

		// Export options.

		this.ezExportROI = new EzVarBoolean( "Export ROIs", true );
		this.ezExportLabels = new EzVarBoolean( "Export labels", false );
		this.ezExportFlows = new EzVarBoolean( "Export flows", false );
		this.ezExportSwPool = new EzVarBoolean( "Prepare for tracking", false );
		ezExportSwPool.setToolTipText(
				"Exports the detected object in a format compatible with the \"Spot Tracking\" plug-in" );

		this.ezExportOptions = new EzGroup( "Export options", ezExportROI, ezExportLabels, ezExportFlows, ezExportSwPool );

		// Info.

		this.ezInfo = new EzLabel( " " );
	}

	@Override
	protected void initialize()
	{
		addEzComponent( ezSequence );
		addEzComponent( ezCellposeBasicParams );
		addEzComponent( ezCellposeAdvancedParams );
		addEzComponent( ez3DProcessing );
		addEzComponent( ezExportOptions );
		addEzComponent( ezInfo );
	}

	@Override
	public void clean()
	{
		// Nothing to clean.
	}

	@Override
	protected void execute()
	{
		ezInfo.setText( " " );
		final ApposeTaskListener apposeLogger = ApposeUtils.apposeEzLogger( getClass(), getStatus() );

		try
		{
			/*
			 *  Classloader for FastStringService.
			 *
			 *  Without the line below, we get the following error in Icy:
			 *
			 *  <pre>
java.lang.RuntimeException: Cellpose failed: Unable to load FastStringService
at plugins.tinevez.appose.cellpose.Cellpose3.execute(Cellpose3.java:256)
at plugins.adufour.ezplug.EzPlug.lambda$new$0(EzPlug.java:103)
at java.base/java.lang.Thread.run(Thread.java:1583)
Caused by: java.lang.RuntimeException: Unable to load FastStringService
at org.apache.groovy.json.internal.FastStringUtils.getService(FastStringUtils.java:56)
at org.apache.groovy.json.internal.FastStringUtils.toCharArray(FastStringUtils.java:66)
at org.apache.groovy.json.internal.CharBuf.addJsonFieldName(CharBuf.java:524)
at groovy.json.DefaultJsonGenerator.writeMapEntry(DefaultJsonGenerator.java:400)
at groovy.json.DefaultJsonGenerator.writeMap(DefaultJsonGenerator.java:389)
at groovy.json.DefaultJsonGenerator.writeObject(DefaultJsonGenerator.java:204)
at groovy.json.DefaultJsonGenerator.writeObject(DefaultJsonGenerator.java:168)
at groovy.json.DefaultJsonGenerator.toJson(DefaultJsonGenerator.java:102)
at org.apposed.appose.util.Messages.encode(Messages.java:75)
</pre>
			 */
			Thread.currentThread().setContextClassLoader( FastStringService.class.getClassLoader() );

			// Inputs.
			final Sequence sequence = ezSequence.getValue( true );
			final CellposeParameters parameters = getParameters();

			// Process.
			final List< Sequence > outputs = runCellpose( sequence, parameters, apposeLogger );

			// Post-process and export results.
			final Sequence outputLabels = outputs.get( 0 );

			if ( ezExportROI.getValue() )
			{
				apposeLogger.message( "Converting Cellpose output to ROIs" );
				final String prefix = cellposeRoiNamePrefix();
				cleanOldRois( sequence, prefix );
				final List< ROI > rois = extractRois( outputLabels, prefix );
				sequence.addROIs( rois, true );

				if ( getUI() != null )
					ezInfo.setText( rois.size() + " objects detected" );
			}

			if ( ezExportLabels.getValue() || outputSequence.isReferenced() )
			{
				outputSequence.setValue( outputLabels );
				if ( !isHeadLess() )
					addSequence( outputLabels );
			}

			if ( ezExportFlows.getValue() && outputs.size() > 1 )
			{
				final Sequence outputFlows = outputs.get( 1 );
				addSequence( outputFlows );
			}
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
			throw new RuntimeException( "Cellpose failed: " + e.getMessage(), e );
		}
	}

	/**
	 * Run Cellpose on the given sequence with the specified parameters.
	 *
	 * @param sequence
	 *            the input sequence
	 * @param parameters
	 *            the Cellpose parameters
	 * @param logger
	 *            the logger to use for messages
	 * @return a list of output sequences (labels, flows, etc.)
	 */
	protected abstract List< Sequence > runCellpose( Sequence sequence, CellposeParameters parameters, ApposeTaskListener apposeLogger ) throws Exception;

	/**
	 * Reads the parameters that are set in this UI and returns a
	 * CellposeParameters object that can be used to run Cellpose.
	 *
	 * @return the CellposeParameters object with the parameters set from the
	 *         UI.
	 */
	protected abstract CellposeParameters getParameters();

	/**
	 * Prefix for the name of the ROIs created by this Cellpose plugin.
	 *
	 * @return the prefix.
	 */
	protected abstract String cellposeRoiNamePrefix();

	/**
	 * Removes old ROIs from the sequence that were created by a previous run of
	 * this plugin, identified by the given prefix.
	 *
	 * @param input
	 *            the sequence from which to remove ROIs.
	 * @param prefix
	 *            the prefix identifying the ROIs to remove.
	 */
	protected static void cleanOldRois( final Sequence input, final String prefix )
	{
		for ( final ROI roi : input.getROIs() )
		{
			if ( roi.getName().startsWith( prefix ) )
				input.removeROI( roi, false );
		}
	}

	/**
	 * Extracts ROIs from the Cellpose output labels sequence, and sets their
	 * name with the given prefix.
	 *
	 * @param labels
	 *            the Cellpose output labels sequence.
	 * @param prefix
	 *            the prefix to set for each ROI name.
	 * @return the list of ROIs extracted from the labels.
	 */
	protected static List< ROI > extractRois( final Sequence output, final String prefix )
	{
		final List< ROI > rois = LabelExtractor.extractLabels( output, ExtractionType.ALL_LABELS_VS_BACKGROUND, 0 );
		final List< Color > colors = getGlasbeyDarkColors();
		final int nRois = rois.size();
		// How many digits for the number of ROIs?
		final int nDigits = nRois > 0 ? ( int ) Math.ceil( Math.log10( nRois ) ) : 1;
		final String format = prefix + "%0" + nDigits + "d";
		// Shift origin
		final double[] pos2d = output.getPosition();
		// To pixel coordinates.
		pos2d[ 0 ] /= output.getPixelSizeX();
		pos2d[ 1 ] /= output.getPixelSizeY();
		final Point5D origin = new Point5D.Double( pos2d );
		for ( int i = 0; i < rois.size(); i++ )
		{
			final ROI roi = rois.get( i );
			final Point5D currentPos = roi.getPosition5D();
			currentPos.setX( currentPos.getX() + origin.getX() );
			currentPos.setY( currentPos.getY() + origin.getY() );
			final Color color = colors.get( i % colors.size() );
			roi.setPosition5D( currentPos );
			roi.setColor( color );
			roi.setName( String.format( format, i ) );
		}
		return rois;
	}
}
