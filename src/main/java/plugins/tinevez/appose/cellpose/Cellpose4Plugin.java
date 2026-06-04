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

import java.util.List;

import fr.icy.extension.plugin.annotation_.IcyPluginName;
import fr.icy.model.sequence.Sequence;
import net.imglib2.cellpose.ApposeTaskListener;
import net.imglib2.cellpose.Cellpose4Parameters;
import net.imglib2.cellpose.CellposeParameters;
import plugins.adufour.ezplug.EzVarInteger;

@IcyPluginName( "Cellpose SAM" )
public class Cellpose4Plugin extends AbstractCellposePlugin
{

	static final String CELLPOSE_ROI_NAME_PREFIX = "Cellpose4Roi_";

	protected final EzVarInteger ezChan1;

	protected final EzVarInteger ezChan2;

	protected final EzVarInteger ezChan3;

	public Cellpose4Plugin()
	{
		this.ezChan1 = new EzVarInteger( "Channel 1", 1, 0, 3, 1 );
		ezChan1.setToolTipText( "<html>Select the first channel to pass on to Cellpose SAM. Set to 0 to skip.</html>" );
		this.ezChan2 = new EzVarInteger( "Channel 2", 0, 0, 3, 1 );
		ezChan2.setToolTipText( "<html>Select the second channel to pass on to Cellpose SAM. Set to 0 to skip.</html>" );
		this.ezChan3 = new EzVarInteger( "Channel 3", 0, 0, 3, 1 );
		ezChan3.setToolTipText( "<html>Select the third channel to pass on to Cellpose SAM. Set to 0 to skip.</html>" );

		// Add them and diameter to the group.
		ezCellposeBasicParams.add( ezChan1, ezChan2, ezChan3, ezDiameter );

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
				ezChan3.setMaxValue( nC );
			}
		} );
	}

	@Override
	protected List< Sequence > runCellpose( final Sequence sequence, final CellposeParameters parameters, final ApposeTaskListener apposeLogger ) throws Exception
	{
		return Cellpose.cellpose( sequence, ( Cellpose4Parameters ) parameters, apposeLogger );
	}

	@Override
	protected Cellpose4Parameters getParameters()
	{
		final Sequence sequence = ezSequence.getValue( true );

		final int chan0 = ezChan1.getValue();
		final int chan1 = ezChan2.getValue();
		final int chan2 = ezChan3.getValue();

		final int diameter = ezDiameter.getValue();
		final double flowThreshold = ezFlowThreshold.getValue();
		final double cellprobThreshold = ezCellprobThreshold.getValue();
		final int minSize = ezMinSize.getValue();
		final boolean do3D = ezDo3D.getValue() && sequence.getSizeZ() > 1;
		final double stitchThreshold = sequence.getSizeZ() > 1 ? ezStitchThreshold.getValue() : 0.;
		final boolean computeFlows = ezExportFlows.getValue();

		// ADD: Calculate anisotropy from pixel sizes
		final double pixelSizeZ = sequence.getPixelSizeZ();
		final double pixelSizeXY = sequence.getPixelSizeX();
		final double anisotropy = ( pixelSizeXY > 0. && pixelSizeZ > 0. )
				? pixelSizeZ / pixelSizeXY
				: 1.;

		return Cellpose4Parameters.builder()
				.chan0( chan0 == 0 ? null : chan0 - 1 )
				.chan1( chan1 == 0 ? null : chan1 - 1 )
				.chan2( chan2 == 0 ? null : chan2 - 1 )
				.diameter( diameter )
				.flowThreshold( flowThreshold )
				.cellProbThreshold( cellprobThreshold )
				.minSize( minSize )
				.do3D( do3D )
				.anisotropy( anisotropy )
				.stitchThreshold( stitchThreshold )
				.computeFlows( computeFlows )
				.resample( true )
				.build();
	}

	@Override
	protected String cellposeRoiNamePrefix()
	{
		return CELLPOSE_ROI_NAME_PREFIX;
	}
}
