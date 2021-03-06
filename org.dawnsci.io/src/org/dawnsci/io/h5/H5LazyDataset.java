/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.dawnsci.io.h5;

import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.LazyDataset;
import org.eclipse.dawnsci.hdf5.H5Utils;
import org.eclipse.dawnsci.hdf5.IHierarchicalDataFile;

public class H5LazyDataset extends LazyDataset {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4375441355891182709L;

	public H5LazyDataset(final IHierarchicalDataFile file, String path) throws Exception {
		this((ncsa.hdf.object.Dataset)file.getData(path));
	}
	
	/**
	 * You must ensure the meta data is loaded for this data set before using this
	 * constructor, 		set.getMetadata();
	 * @param set
	 * @param filePath
	 * @throws Exception 
	 */
	public H5LazyDataset(final ncsa.hdf.object.Dataset set) throws Exception {
		
	    super(set.getFullName(), 
              H5Utils.getDataType(set.getDatatype()), 
              H5Utils.getInt(set.getDims()),
			  new H5LazyLoader(set.getFile(), set.getFullName()));
	}
	
	public Dataset getCompleteData(IMonitor monitor) throws Exception {
		return ((H5LazyLoader)this.loader).getCompleteData(monitor);
	}
}
