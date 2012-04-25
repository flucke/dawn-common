package org.dawb.common.ui.plot.region;

import java.util.Collection;
import java.util.HashSet;

import org.eclipse.draw2d.Figure;

import uk.ac.diamond.scisoft.analysis.roi.ROIBase;

/**
 * This is a Figure, disabled for mouse events. 
 * 
 * @author fcp94556
 *
 */
public abstract class AbstractRegion extends Figure implements IRegion, IRegionContainer {

	private Collection<IROIListener> roiListeners;
	private boolean regionEventsActive = true;

	@Override
	public boolean addROIListener(final IROIListener l) {
		if (roiListeners==null) roiListeners = new HashSet<IROIListener>(11);
		return roiListeners.add(l);
	}
	
	@Override
	public boolean removeROIListener(final IROIListener l) {
		if (roiListeners==null) return false;
		return roiListeners.remove(l);
	}
	
	protected void clearListeners() {
		if (roiListeners==null) return;
		roiListeners.clear();
	}
	
	protected void fireROIDragged(ROIBase bounds) {
		if (roiListeners==null) return;
		if (!regionEventsActive) return;
		
		final ROIEvent evt = new ROIEvent(this, bounds);
		for (IROIListener l : roiListeners) {
			l.roiDragged(evt);
		}
	}
	
	protected void fireROIChanged(ROIBase bounds) {
		if (roiListeners==null) return;
		if (!regionEventsActive) return;
		
		final ROIEvent evt = new ROIEvent(this, bounds);
		for (IROIListener l : roiListeners) {
			l.roiChanged(evt);
		}
	}

	protected ROIBase roi;

	@Override
	public ROIBase getROI() {
		return roi;
	}

	@Override
	public void setROI(ROIBase bounds) {
		this.roi = bounds;
		updateROI();
		fireROIChanged(bounds);
	}

	/**
	 * Implement to return the region of interest
	 * @param recordResult if true this calculation changes the recorded absolute position
	 */
	protected abstract ROIBase createROI(boolean recordResult);

	/**
	 * Updates the region, usually called when items have been created and the position of the
	 * region should be updated. Does not fire events.
	 */
	protected void updateROI() {
		if (roi !=null) {
			try {
				this.regionEventsActive = false;
				updateROI(roi);
			} finally {
				this.regionEventsActive = true;
			}
		}
	}
	
	/**
	 * Implement this method to redraw the figure to the axis coordinates (only).
	 * 
	 * @param bounds
	 */
	protected abstract void updateROI(ROIBase bounds);

	public String toString() {
		if (getName()!=null) return getName();
		return super.toString();
	}
	
	protected boolean trackMouse;

	@Override
	public boolean isTrackMouse() {
		return trackMouse;
	}

	@Override
	public void setTrackMouse(boolean trackMouse) {
		this.trackMouse = trackMouse;
	}
	
	private boolean userRegion = true; // Normally a user region.

	@Override
	public boolean isUserRegion() {
		return userRegion;
	}

	@Override
	public void setUserRegion(boolean userRegion) {
		this.userRegion = userRegion;
	}
	
	public IRegion getRegion() {
		return this;
	}

	public void setRegion(IRegion region) {
		// Does nothing
	}
}
