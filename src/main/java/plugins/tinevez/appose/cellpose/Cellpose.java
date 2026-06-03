package plugins.tinevez.appose.cellpose;

import static plugins.tinevez.appose.ApposeUtils.getAxisInfo;
import static plugins.tinevez.appose.ApposeUtils.getGlasbeyDarkColorMap;

import fr.icy.model.sequence.Sequence;
import fr.icy.model.sequence.SequenceUtil;
import net.imglib2.cellpose.ApposeTaskListener;
import net.imglib2.cellpose.AxisInfo;
import net.imglib2.cellpose.Cellpose3Parameters;
import net.imglib2.cellpose.CellposeOutput;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import plugins.tinevez.imglib2icy.ImgLib2IcyFunctions;
import plugins.tinevez.imglib2icy.VirtualSequence.DimensionArrangement;

public class Cellpose
{

	protected static int resultID = 1;

	/**
	 * Run Cellpose 3 on the input Sequence and return the resulting labels as a
	 * new Sequence.
	 *
	 * @param input
	 *            the input Sequence.
	 * @param parameters
	 *            the Cellpose 3 parameters to use.
	 * @param apposeLogger
	 *            the logger to use for logging Cellpose progress and messages.
	 * @return a new Sequence containing the Cellpose labels.
	 * @throws Exception
	 *             if an error occurs during Cellpose processing.
	 */
	public static Sequence cellpose(
			final Sequence input,
			final Cellpose3Parameters parameters,
			final ApposeTaskListener apposeLogger ) throws Exception
	{
		@SuppressWarnings( "rawtypes" )
		final Img img = ImgLib2IcyFunctions.wrap( input );
		final AxisInfo axisInfo = getAxisInfo( input );
		@SuppressWarnings( "unchecked" )
		final
		CellposeOutput< UnsignedShortType > out = net.imglib2.cellpose.Cellpose.cellpose3( img, axisInfo, parameters,				apposeLogger );

		final DimensionArrangement inputDims = ImgLib2IcyFunctions.getDimensionArrangement( input );
		final DimensionArrangement outputDims = inputDims.dropC();
		final Sequence tmp = ImgLib2IcyFunctions.wrap( out.labels, outputDims );
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
}
