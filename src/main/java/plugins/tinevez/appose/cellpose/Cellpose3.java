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

import static plugins.tinevez.appose.ApposeUtils.apposeEzLogger;
import static plugins.tinevez.appose.ApposeUtils.getAxisInfo;
import static plugins.tinevez.appose.ApposeUtils.getGlasbeyDarkColorMap;
import static plugins.tinevez.appose.ApposeUtils.getGlasbeyDarkColors;

import java.awt.Color;
import java.util.List;

import fr.icy.extension.plugin.annotation_.IcyPluginName;
import fr.icy.model.roi.ROI;
import fr.icy.model.sequence.Sequence;
import fr.icy.model.sequence.SequenceUtil;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cellpose.ApposeTaskListener;
import net.imglib2.cellpose.AxisInfo;
import net.imglib2.cellpose.Cellpose;
import net.imglib2.cellpose.Cellpose3BuiltinModels;
import net.imglib2.cellpose.Cellpose3Parameters;
import net.imglib2.cellpose.CellposeOutput;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import plugins.adufour.ezplug.EzGroup;
import plugins.adufour.ezplug.EzLabel;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzVarBoolean;
import plugins.adufour.ezplug.EzVarDouble;
import plugins.adufour.ezplug.EzVarEnum;
import plugins.adufour.ezplug.EzVarInteger;
import plugins.adufour.ezplug.EzVarSequence;
import plugins.adufour.roi.LabelExtractor;
import plugins.adufour.roi.LabelExtractor.ExtractionType;
import plugins.adufour.vars.lang.VarSequence;
import plugins.tinevez.imglib2icy.ImgLib2IcyFunctions;
import plugins.tinevez.imglib2icy.VirtualSequence.DimensionArrangement;

@IcyPluginName( "Cellpose 3" )
public class Cellpose3 extends EzPlug
{

	static final String CELLPOSE_ROI_NAME_PREFIX = "Cellpose3Roi_";

	protected static int resultID = 1;

	protected final EzVarSequence ezSequence;

	// 1. Basic Cellpose parameters group.

	protected final EzVarEnum< Cellpose3BuiltinModels > ezModel;

	protected final EzVarInteger ezChan1;

	protected final EzVarInteger ezChan2;

	protected final EzVarInteger ezDiameter;

	// 2. Segmentation quality group

	private final EzVarDouble ezFlowThreshold;

	private final EzVarDouble ezCellprobThreshold;

	private final EzVarInteger ezMinSize;

	// 3. 3D processing parameters.

	private final EzVarBoolean ezDo3D;

	private final EzVarDouble ezStitchThreshold;

	// 4. Export options.

	protected EzVarBoolean ezExportROI;

	protected EzVarBoolean ezExportSequence;

	protected EzVarBoolean ezExportSwPool;

	// Others.

	protected final EzLabel ezInfo;

	// Unused yet.
	protected VarSequence outputSequence = new VarSequence( "cellpose output", null );

	// Groups.

	private final EzGroup ezCellposeBasicParams;

	private final EzGroup ezExportOptions;

	private final EzGroup ezCellposeAdvancedParams;

	private final EzGroup ez3DProcessing;

	public Cellpose3()
	{
		this.ezSequence = new EzVarSequence( "Input sequence" );

		// Cellpose basic parameters.

		this.ezModel = new EzVarEnum<>( "Pretrained model", Cellpose3BuiltinModels.values(), Cellpose3BuiltinModels.CYTO3 );
		ezModel.setToolTipText( "<html>Select the pretrained Cellpose model to use.</html>" );
		this.ezChan1 = new EzVarInteger( "Main channel", 1, 0, 3, 1 );
		ezChan1.setToolTipText( "<html>Select the main channel to use for segmentation. "
				+ "Use 0 to specify using a merge of all channels.</html>" );
		this.ezChan2 = new EzVarInteger( "Optional channel", 0, 0, 3, 1 );
		ezChan2.setToolTipText( "<html>Select the nuclear channel for the 'cyto' models. "
				+ "Use 0 to skip using this optional channel.</html>" );
		this.ezDiameter = new EzVarInteger( "Diameter (pixels)", 30, 0, Integer.MAX_VALUE, 1 );
		ezDiameter.setToolTipText( "<html>Approximate diameter of the objects to segment, in pixels. "
				+ "If 0 will use the diameter of the training labels used in the model, "
				+ "or with built-in model will estimate diameter for each image</html>" );

		this.ezCellposeBasicParams = new EzGroup( "Cellpose basic parameters",
				ezModel, ezChan1, ezChan2, ezDiameter );

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
		this.ezExportSequence = new EzVarBoolean( "Export labels", false );
		this.ezExportSwPool = new EzVarBoolean( "Prepare for tracking", false );
		ezExportSwPool.setToolTipText(
				"Exports the detected object in a format compatible with the \"Spot Tracking\" plug-in" );

		this.ezExportOptions = new EzGroup( "Export options", ezExportROI, ezExportSequence, ezExportSwPool );

		// Info.

		this.ezInfo = new EzLabel( " " );

		// Listeners.
		ezSequence.addVarChangeListener( ( source, seq ) -> {
			if ( seq != null )
			{
				// Swimming pool export only for time series.
				ezExportSwPool.setVisible( seq != null && seq.getSizeT() > 1 );
				// 3D processing only for 3D stacks.
				final boolean is3D = seq.getSizeZ() > 1;
				ez3DProcessing.setVisible( is3D );
				// Update channels max values.
				final int nC = seq.getSizeC();
				ezChan1.setMaxValue( nC );
				ezChan2.setMaxValue( nC );
			}
		} );
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
		final Sequence sequence = ezSequence.getValue( true );
		final Cellpose3BuiltinModels model = ezModel.getValue();
		final int chan = ezChan1.getValue();
		final int chan2 = ezChan2.getValue();
		final int diameter = ezDiameter.getValue();
		final double flowThreshold = ezFlowThreshold.getValue();
		final double cellprobThreshold = ezCellprobThreshold.getValue();
		final int minSize = ezMinSize.getValue();
		final boolean do3D = ezDo3D.getValue() && sequence.getSizeZ() > 1;
		final double stitchThreshold = sequence.getSizeZ() > 1 ? ezStitchThreshold.getValue() : 0.;

		// ADD: Calculate anisotropy from pixel sizes
		final double pixelSizeZ = sequence.getPixelSizeZ();
		final double pixelSizeXY = sequence.getPixelSizeX();
		final double anisotropy = ( pixelSizeXY > 0. && pixelSizeZ > 0. )
				? pixelSizeZ / pixelSizeXY
				: 1.;

		final Cellpose3Parameters parameters = Cellpose3Parameters.builder()
				.model( model )
				.channels( List.of( chan, chan2 ) )
				.diameter( diameter )
				.flowThreshold( flowThreshold )
				.cellProbThreshold( cellprobThreshold )
				.minSize( minSize )
				.do3D( do3D )
				.anisotropy( anisotropy )
				.stitchThreshold( stitchThreshold )
				.build();

		try
		{
			execute( sequence, parameters );
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
			throw new RuntimeException( "Cellpose failed: " + e.getMessage(), e );
		}
	}

	public void execute( final Sequence sequence, final Cellpose3Parameters parameters ) throws Exception
	{
		final ApposeTaskListener apposeLogger = apposeEzLogger( getClass(), getStatus() );
		final Sequence output = process( sequence, parameters, apposeLogger );

		if ( ezExportROI.getValue() )
		{
			apposeLogger.message( "Converting Cellpose output to ROIs" );
			cleanOldRois( sequence );
			final List< ROI > rois = extractRois( output );
			sequence.addROIs( rois, true );

			if ( getUI() != null )
				ezInfo.setText( rois.size() + " objects detected" );
		}

		if ( ezExportSequence.getValue() || outputSequence.isReferenced() )
		{
			outputSequence.setValue( output );
			if ( !isHeadLess() )
				addSequence( output );
		}
	}

	public static Sequence process(
			final Sequence input,
			final Cellpose3Parameters parameters,
			final ApposeTaskListener apposeLogger ) throws Exception
	{
		@SuppressWarnings( "rawtypes" )
		final Img img = ImgLib2IcyFunctions.wrap( input );
		final AxisInfo axisInfo = getAxisInfo( input );
		@SuppressWarnings( { "unchecked", "rawtypes" } )
		final RandomAccessibleInterval out = process( img, axisInfo, parameters, apposeLogger );

		final DimensionArrangement inputDims = ImgLib2IcyFunctions.getDimensionArrangement( input );
		final DimensionArrangement outputDims = inputDims.dropC();
		@SuppressWarnings( "unchecked" )
		final Sequence tmp = ImgLib2IcyFunctions.wrap( out, outputDims );
		final Sequence output = SequenceUtil.getCopy( tmp );

		output.setPixelSizeX( input.getPixelSizeX() );
		output.setPixelSizeY( input.getPixelSizeY() );
		output.setPixelSizeZ( input.getPixelSizeZ() );
		output.setTimeInterval( input.getTimeInterval() );
		output.setChannelName( 0, "Cellpose labels" );
		output.setColormap( 0, getGlasbeyDarkColorMap(), true );
		final String name = input.getName() + "_Cellpose#" + resultID++;
		output.setName( name );

		return output;
	}

	private static < T extends RealType< T > & NativeType< T > > RandomAccessibleInterval<UnsignedShortType> process(
			final Img< T > img,
			final AxisInfo axisInfo,
			final Cellpose3Parameters parameters,
			final ApposeTaskListener apposeLogger ) throws Exception
	{
		CellposeOutput<UnsignedShortType> output = Cellpose.cellpose3(img, axisInfo, parameters, apposeLogger );
		return output.labels;
	}

	static void cleanOldRois( final Sequence input )
	{
		for ( final ROI roi : input.getROIs() )
			if ( roi.getName().startsWith( CELLPOSE_ROI_NAME_PREFIX ) )
				input.removeROI( roi, false );
	}

	static List< ROI > extractRois( final Sequence output )
	{
		final List< ROI > rois = LabelExtractor.extractLabels( output, ExtractionType.ALL_LABELS_VS_BACKGROUND, 0 );
		final List< Color > colors = getGlasbeyDarkColors();
		final int nRois = rois.size();
		// How many digits for the number of ROIs?
		final int nDigits = nRois > 0 ? ( int ) Math.ceil( Math.log10( nRois ) ) : 1;
		final String format = CELLPOSE_ROI_NAME_PREFIX + "%0" + nDigits + "d";
		for ( int i = 0; i < rois.size(); i++ )
		{
			final ROI roi = rois.get( i );
			final Color color = colors.get( i % colors.size() );
			roi.setColor( color );
			roi.setName( String.format( format, i ) );
		}
		return rois;
	}
}
