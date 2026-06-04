package plugins.tinevez.appose.cellpose;

import static plugins.tinevez.appose.ApposeUtils.getAxisInfo;
import static plugins.tinevez.appose.ApposeUtils.getGlasbeyDarkColorMap;

import java.util.Collections;
import java.util.List;

import fr.icy.extension.kernel.roi.roi3d.ROI3DBox;
import fr.icy.model.roi.ROI;
import fr.icy.model.sequence.Sequence;
import fr.icy.model.sequence.SequenceUtil;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cellpose.ApposeTaskListener;
import net.imglib2.cellpose.AxisInfo;
import net.imglib2.cellpose.Cellpose3Parameters;
import net.imglib2.cellpose.CellposeOutput;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import plugins.tinevez.appose.ApposeUtils;
import plugins.tinevez.imglib2icy.ImgLib2IcyFunctions;
import plugins.tinevez.imglib2icy.VirtualSequence.DimensionArrangement;

public class Cellpose
{

	protected static int resultID = 1;

	/**
	 * Run Cellpose 3 on the input Sequence and return the resulting labels and
	 * flows as new Sequences.
	 *
	 * @param input
	 *            the input Sequence.
	 * @param parameters
	 *            the Cellpose 3 parameters to use.
	 * @param apposeLogger
	 *            the logger to use for logging Cellpose progress and messages.
	 * @return a list made of
	 * <ol start="0">
	 * <li>the Sequence containing the Cellpose labels, and</li>
	 * <li>optionally, if flows were computed, the Sequence containing the Cellpose
	 * flows.</li>
	 * </ol>
	 * @throws Exception
	 *             if an error occurs during Cellpose processing.
	 */
	public static List< Sequence > cellpose(
			final Sequence input,
			final Cellpose3Parameters parameters,
			final ApposeTaskListener apposeLogger ) throws Exception
	{
		// Selected ROI
		final ROI selectedRoi = input.getSelectedROI();
		final ROI roi; // View into 'roi'
		if ( selectedRoi != null )
		{
			// Intersect with the image bounds
			final ROI roiSeq = new ROI3DBox( input.getBounds5D().toRectangle3D() );
			roi = selectedRoi.intersect( roiSeq, true );
		}
		else
		{
			roi = null;
		}
		@SuppressWarnings( "rawtypes" )
		final RandomAccessibleInterval img = ApposeUtils.toImg( input, roi );
		final AxisInfo axisInfo = getAxisInfo( input );
		@SuppressWarnings( "unchecked" )
		final CellposeOutput< UnsignedShortType > out = net.imglib2.cellpose.Cellpose.cellpose3( img, axisInfo, parameters, apposeLogger );

		final DimensionArrangement inputDims = ImgLib2IcyFunctions.getDimensionArrangement( input );
		final DimensionArrangement outputDims = inputDims.dropC();
		final Sequence tmpLabels = ImgLib2IcyFunctions.wrap( out.labels, outputDims );
		final Sequence outputLabels = SequenceUtil.getCopy( tmpLabels );
		ApposeUtils.clearOutsideRoi( outputLabels, roi );
		// Calibration.
		outputLabels.setPixelSizeX( input.getPixelSizeX() );
		outputLabels.setPixelSizeY( input.getPixelSizeY() );
		outputLabels.setPixelSizeZ( input.getPixelSizeZ() );
		outputLabels.setTimeInterval( input.getTimeInterval() );
		outputLabels.setChannelName( 0, "Cellpose labels" );
		outputLabels.setColormap( 0, getGlasbeyDarkColorMap(), true );
		// Origin of new ROIs.
		final long originX = ( roi == null ) ? 0 : ( long ) roi.getBounds5D().getMinX();
		final long originY = ( roi == null ) ? 0 : ( long ) roi.getBounds5D().getMinY();
		final long originZ = ( roi == null ) ? 0 : ( long ) roi.getBounds5D().getMinZ();
		// Shift the origin if we do ROI processing.
		outputLabels.setPositionX( originX * input.getPixelSizeX() );
		outputLabels.setPositionY( originY * input.getPixelSizeY() );
		outputLabels.setPositionZ( originZ * input.getPixelSizeZ() );
		final String name = input.getName() + "_Cellpose#" + resultID++;
		outputLabels.setName( name );

		// Flows.
		if ( parameters.computeFlows )
		{
			// Add a C dimension if missing.
			final DimensionArrangement outputFlowDims;
			switch ( inputDims )
			{
			case XYC:
			case XYCT:
			case XYCZ:
			case XYCZT:
				outputFlowDims = inputDims;
				break;
			case XY:
				outputFlowDims = DimensionArrangement.XYC;
				break;
			case XYT:
				outputFlowDims = DimensionArrangement.XYCT;
				break;
			case XYZ:
				outputFlowDims = DimensionArrangement.XYCZ;
				break;
			case XYZT:
				outputFlowDims = DimensionArrangement.XYCZT;
				break;
			default:
				throw new IllegalArgumentException( "Unknown input dimension arrangement: " + inputDims );
			}

			final Sequence tmpFlows = ImgLib2IcyFunctions.wrap( out.flows, outputFlowDims );
			final Sequence outputFlows = SequenceUtil.getCopy( tmpFlows );
			ApposeUtils.clearOutsideRoi( outputFlows, roi );
			outputFlows.setName( input.getName() + "_Cellpose_flows#" + resultID );
			outputFlows.setPixelSizeX( input.getPixelSizeX() );
			outputFlows.setPixelSizeY( input.getPixelSizeY() );
			outputFlows.setPixelSizeZ( input.getPixelSizeZ() );
			outputFlows.setTimeInterval( input.getTimeInterval() );
			// Shift the origin if we do ROI processing.
			outputFlows.setPositionX( originX * input.getPixelSizeX() );
			outputFlows.setPositionY( originY * input.getPixelSizeY() );
			outputFlows.setPositionZ( originZ * input.getPixelSizeZ() );

			return List.of( outputLabels, outputFlows );
		}

		return Collections.singletonList( outputLabels );
	}
}
