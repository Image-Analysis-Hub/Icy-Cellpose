package plugins.tinevez.appose.cellpose;

import static plugins.tinevez.appose.cellpose.Cellpose3.cleanOldRois;
import static plugins.tinevez.appose.cellpose.Cellpose3.extractRois;
import static plugins.tinevez.appose.cellpose.Cellpose3.process;

import java.util.List;

import org.bioimageanalysis.icy.extension.plugin.abstract_.Plugin;
import org.bioimageanalysis.icy.extension.plugin.annotation_.IcyPluginName;
import org.bioimageanalysis.icy.model.roi.ROI;
import org.bioimageanalysis.icy.model.sequence.Sequence;

import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.vars.lang.VarBoolean;
import plugins.adufour.vars.lang.VarEnum;
import plugins.adufour.vars.lang.VarInteger;
import plugins.adufour.vars.lang.VarROIArray;
import plugins.adufour.vars.lang.VarSequence;
import plugins.tinevez.appose.ApposeUtils;
import plugins.tinevez.appose.ApposeUtils.ApposeLogger;

@IcyPluginName( "Cellpose 3" )
public class Cellpose3Block extends Plugin implements Block
{

	protected static int resultID = 1;

	protected final VarSequence input;

	protected final VarEnum< Cellpose3Model > varModel;

	protected final VarInteger varChan1;

	protected final VarInteger varChan2;

	protected final VarInteger varDiameter;

	protected VarBoolean exportROI;

	protected VarBoolean exportSequence;

	protected VarBoolean exportSwPool;

	protected VarSequence outputSequence;

	protected VarROIArray outputROIs;

	public Cellpose3Block()
	{
		this.input = new VarSequence( "Input sequence", null );
		this.varModel = new VarEnum<>( "Pretrained model", Cellpose3Model.CYTO3 );
		this.varChan1 = new VarInteger( "Main channel", 1 );
		this.varChan2 = new VarInteger( "Optional channel", 0 );
		this.varDiameter = new VarInteger( "Diameter (pixels)", 30 );
		this.exportROI = new VarBoolean( "Export ROIs", true );
		this.exportSequence = new VarBoolean( "Export labels", false );
		this.exportSwPool = new VarBoolean( "Prepare for tracking", false );
		this.outputSequence = new VarSequence( "binary sequence", null );
		this.outputROIs = new VarROIArray( "list of ROI" );

	}

	@Override
	public void run()
	{
		final Cellpose3Model model = varModel.getValue();
		final int chan = varChan1.getValue();
		final int chan2 = varChan2.getValue();
		final int diameter = varDiameter.getValue();

		final Cellpose3Parameters parameters = new Cellpose3Parameters.Builder()
				.model( model )
				.channels( List.of( chan, chan2 ) )
				.diameter( diameter )
				.build();
		final Sequence inSeq = input.getValue( true );
		final ApposeLogger logger = ApposeUtils.apposeLogger( getClass() );
		try
		{
			final Sequence outSeq = process( inSeq, parameters, logger );

			if ( exportSequence.getValue() || outputSequence.isReferenced() )
				outputSequence.setValue( outSeq );

			if ( exportROI.getValue() )
			{
				cleanOldRois( inSeq );
				final List< ROI > rois = extractRois( outSeq );
				outputROIs.add( rois.toArray( new ROI[ 0 ] ) );
				inSeq.addROIs( rois, false );
			}
		}
		catch ( final Exception e )
		{
			System.err.print( "Cellpose 3 process error!" );
		}
	}

	@Override
	public void declareInput( final VarList inputMap )
	{
		inputMap.add( "Input", input );
		inputMap.add( "Model", varModel );
		inputMap.add( "Main channel", varChan1 );
		inputMap.add( "Optional channel", varChan2 );
		inputMap.add( "Diameter", varDiameter );

		// force sequence export in box mode
		exportROI.setValue( false );
		exportSwPool.setValue( false );
		exportSequence.setValue( false );
	}

	@Override
	public void declareOutput( final VarList outputMap )
	{
		outputMap.add( "label image", outputSequence );
		outputMap.add( "ROIs", outputROIs );
	}
}
