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
import static plugins.tinevez.appose.ApposeUtils.getGlasbeyDarkColors;

import java.awt.Color;
import java.util.List;

import org.apache.groovy.json.FastStringService;

import fr.icy.extension.plugin.annotation_.IcyPluginName;
import fr.icy.model.roi.ROI;
import fr.icy.model.sequence.Sequence;
import net.imglib2.cellpose.ApposeTaskListener;
import net.imglib2.cellpose.Cellpose3BuiltinModels;
import net.imglib2.cellpose.Cellpose3Parameters;
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

@IcyPluginName( "Cellpose 3" )
public class Cellpose3Plugin extends EzPlug
{

	static final String CELLPOSE_ROI_NAME_PREFIX = "Cellpose3Roi_";

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

	public Cellpose3Plugin()
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

		this.ezInfo = new EzLabel( "Cellpose 3 plugin" );

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
		final Sequence output = Cellpose.cellpose( sequence, parameters, apposeLogger );

		if ( ezExportROI.getValue() )
		{
			apposeLogger.message( "Converting Cellpose output to ROIs" );
			cleanOldRois( sequence );
			final List< ROI > rois = extractRois( output );
			sequence.addROIs( rois, true );

			if ( getUI() != null )
			{
				ezInfo.setText( rois.size() + " objects detected" );
			}
		}

		if ( ezExportSequence.getValue() || outputSequence.isReferenced() )
		{
			outputSequence.setValue( output );
			if ( !isHeadLess() )
			{
				addSequence( output );
			}
		}
	}

	static void cleanOldRois( final Sequence input )
	{
		for ( final ROI roi : input.getROIs() )
		{
			if ( roi.getName().startsWith( CELLPOSE_ROI_NAME_PREFIX ) )
			{
				input.removeROI( roi, false );
			}
		}
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
