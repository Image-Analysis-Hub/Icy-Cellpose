package plugins.tinevez.appose.cellpose;

import static plugins.tinevez.appose.ApposeUtils.getAxisInfo;
import static plugins.tinevez.appose.ApposeUtils.getGlasbeyDarkColorMap;

import java.util.Collections;
import java.util.List;

import fr.icy.common.geom.rectangle.Rectangle5D;
import fr.icy.extension.kernel.roi.roi3d.ROI3DBox;
import fr.icy.model.roi.ROI;
import fr.icy.model.sequence.Sequence;
import fr.icy.model.sequence.SequenceUtil;
import fr.icy.system.logging.IcyLogger;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cellpose.ApposeTaskListener;
import net.imglib2.cellpose.AxisInfo;
import net.imglib2.cellpose.Cellpose3Parameters;
import net.imglib2.cellpose.Cellpose4Parameters;
import net.imglib2.cellpose.CellposeOutput;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
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
	 *         <ol start="0">
	 *         <li>the Sequence containing the Cellpose labels, and</li>
	 *         <li>optionally, if flows were computed, the Sequence containing
	 *         the Cellpose flows.</li>
	 *         </ol>
	 * @throws Exception
	 *             if an error occurs during Cellpose processing.
	 */
	public static List< Sequence > cellpose(
			final Sequence input,
			final Cellpose3Parameters parameters,
			final ApposeTaskListener apposeLogger ) throws Exception
	{
		final ROI roi = getAndCropSelectedRoi( input );
		@SuppressWarnings( "rawtypes" )
		final RandomAccessibleInterval img = toImg( input, roi );
		final AxisInfo axisInfo = getAxisInfo( input );
		@SuppressWarnings( "unchecked" )
		final CellposeOutput< UnsignedShortType > out = net.imglib2.cellpose.Cellpose.cellpose3( img, axisInfo, parameters, apposeLogger );
		return postProcess( out, input, roi, "-3" );
	}

	public static List< Sequence > cellpose(
			final Sequence input,
			final Cellpose4Parameters parameters,
			final ApposeTaskListener apposeLogger ) throws Exception
	{
		final ROI roi = getAndCropSelectedRoi( input );
		@SuppressWarnings( "rawtypes" )
		final RandomAccessibleInterval img = toImg( input, roi );
		final AxisInfo axisInfo = getAxisInfo( input );
		@SuppressWarnings( "unchecked" )
		final CellposeOutput< UnsignedShortType > out = net.imglib2.cellpose.Cellpose.cellpose4( img, axisInfo, parameters, apposeLogger );
		return postProcess( out, input, roi, "-SAM" );
	}

	/**
	 * Get the first selected ROI in the specified sequence, and make a new one
	 * that is its intersection with the bounds of the input sequence.
	 *
	 * @param input
	 *            the input sequence.
	 * @return the intersection of the first selected ROI with the bounds of the
	 *         input sequence, or null if there is no selected ROI.
	 */
	private static final ROI getAndCropSelectedRoi( final Sequence input )
	{
		final ROI selectedRoi = input.getSelectedROI();
		if ( selectedRoi == null )
			return null;

		try
		{
			// Selected ROI
			final ROI roi; // View into 'roi'
			if ( selectedRoi != null )
			{
				// Intersect with the image bounds
				final ROI roiSeq = new ROI3DBox( input.getBounds5D().toRectangle3D() );
				roi = selectedRoi.intersect( roiSeq, true );
				return roi;
			}
		}
		catch ( final Exception e )
		{
			IcyLogger.error( Cellpose.class, e, "Error preprocessing input Sequence for Cellpose: " + e.getMessage() );
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Post-process the Cellpose output by creating new Sequences for the labels
	 * and flows, applying the appropriate calibration and metadata, and
	 * clearing pixels outside the ROI.
	 *
	 * @param out
	 *            the Cellpose output containing the labels and flows.
	 * @param input
	 *            the original input Sequence.
	 * @param roi
	 *            the ROI used for processing, or null if no ROI was used.
	 * @param tag
	 * @return a list made of
	 *         <ol start="0">
	 *         <li>the Sequence containing the Cellpose labels, and</li>
	 *         <li>optionally, if flows were computed, the Sequence containing
	 *         the Cellpose flows.</li>
	 * @throws Exception
	 *             if an error occurs during post-processing.
	 */
	private static List< Sequence > postProcess(
			final CellposeOutput< UnsignedShortType > out,
			final Sequence input,
			final ROI roi, final String tag ) throws Exception
	{
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
		outputLabels.setChannelName( 0, "Cellpose" + tag + " labels" );
		outputLabels.setColormap( 0, getGlasbeyDarkColorMap(), true );
		// Origin of new ROIs.
		final long originX = ( roi == null ) ? 0 : ( long ) roi.getBounds5D().getMinX();
		final long originY = ( roi == null ) ? 0 : ( long ) roi.getBounds5D().getMinY();
		final long originZ = ( roi == null ) ? 0 : ( long ) roi.getBounds5D().getMinZ();
		// Shift the origin if we do ROI processing.
		outputLabels.setPositionX( originX * input.getPixelSizeX() );
		outputLabels.setPositionY( originY * input.getPixelSizeY() );
		outputLabels.setPositionZ( originZ * input.getPixelSizeZ() );
		final String name = input.getName() + "_Cellpose" + tag + "#" + resultID++;
		outputLabels.setName( name );

		// Flows.
		if ( out.flows != null )
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
			outputFlows.setName( input.getName() + "_Cellpose" + tag + "_flows#" + resultID );
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
}
