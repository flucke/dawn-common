/*-
 * Copyright 2014 Diamond Light Source Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dawnsci.persistence.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.vecmath.Matrix3d;

import ncsa.hdf.object.Dataset;
import ncsa.hdf.object.Datatype;
import ncsa.hdf.object.Group;
import ncsa.hdf.object.HObject;
import ncsa.hdf.object.h5.H5Datatype;

import org.dawb.common.services.IPersistentFile;
import org.dawb.hdf5.HierarchicalDataFactory;
import org.dawb.hdf5.HierarchicalDataFileUtils;
import org.dawb.hdf5.IHierarchicalDataFile;
import org.dawb.hdf5.Nexus;
import org.dawb.hdf5.nexus.NexusUtils;
import org.dawnsci.io.h5.H5LazyDataset;
import org.dawnsci.io.h5.H5Utils;
import org.dawnsci.persistence.json.IJSonMarshaller;
import org.dawnsci.persistence.json.JacksonMarshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Comparisons;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.dataset.ILazyDataset;
import uk.ac.diamond.scisoft.analysis.diffraction.DetectorProperties;
import uk.ac.diamond.scisoft.analysis.diffraction.DiffractionCrystalEnvironment;
import uk.ac.diamond.scisoft.analysis.fitting.functions.IFunction;
import uk.ac.diamond.scisoft.analysis.io.IDataHolder;
import uk.ac.diamond.scisoft.analysis.io.IDiffractionMetadata;
import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;
import uk.ac.diamond.scisoft.analysis.io.NexusDiffractionMetaReader;
import uk.ac.diamond.scisoft.analysis.monitor.IMonitor;
import uk.ac.diamond.scisoft.analysis.roi.IROI;

/**
 * Implementation of IPersistentFile<br>
 * 
 * This class is internal and not supposed to be used out of this bundle.
 * 
 * @author wqk87977
 *
 */
class PersistentFileImpl implements IPersistentFile {
	
	private static final Logger logger = LoggerFactory.getLogger(PersistentFileImpl.class);
	private IHierarchicalDataFile file;
	private String filePath;

	/**
	 * For save
	 * @param file
	 */
	public PersistentFileImpl(IHierarchicalDataFile file) throws Exception{
		this.file = file;
		this.filePath = file.getPath();

		String currentSite = System.getenv("DAWN_SITE");
		if (currentSite==null || "".equals(currentSite)) {
			logger.debug("DAWN_SITE is not set, persistence layer defaulting to DLS in file meta information.");
			currentSite = "Diamond Light Source";
		}
		setSite(currentSite);
		setVersion(PersistenceConstants.CURRENT_VERSION);
	}

	/**
	 * For read
	 * @param filePath
	 */
	public PersistentFileImpl(String filePath) {
		this.filePath = filePath;
		try {
			this.file = HierarchicalDataFactory.getReader(filePath);
		} catch (Exception e) {
			logger.error("Error getting H5 Reader:" + e);
		}
	}

	@Override
	public void setMasks(Map<String, ? extends IDataset> masks) throws Exception {
		if(file == null)
			file = HierarchicalDataFactory.getWriter(filePath);

		Group parent = createParentEntry(PersistenceConstants.MASK_ENTRY);

		if (masks != null) {
			Set<String> names = masks.keySet();

			Iterator<String> it = names.iterator();
			while(it.hasNext()){
				String name = it.next();
				BooleanDataset bd = (BooleanDataset)masks.get(name);
				// Inverse the dataset
				bd = Comparisons.logicalNot(bd);

				AbstractDataset id = DatasetUtils.cast(bd, AbstractDataset.INT8);
				final Datatype datatype = H5Utils.getDatatype(id);
				final long[] shape = new long[id.getShape().length];
				for (int i = 0; i < shape.length; i++)
					shape[i] = id.getShape()[i];

				final Dataset dataset = file.replaceDataset(name, datatype, shape, id.getBuffer(), parent);
				file.setNexusAttribute(dataset, Nexus.SDS);
			}
		}
	}

	@Override
	public void addMask(String name, IDataset mask, IMonitor mon) throws Exception{
		// Inverse the dataset
		mask = Comparisons.logicalNot((BooleanDataset)mask);
		AbstractDataset id = DatasetUtils.cast((BooleanDataset)mask, AbstractDataset.INT8);
		//check if parent group exists
		Group parent = (Group)file.getData(PersistenceConstants.MASK_ENTRY);
		if(parent == null)
			parent = createParentEntry(PersistenceConstants.MASK_ENTRY);
		final Datatype datatype = H5Utils.getDatatype(id);
		long[] shape = H5Utils.getLong(id.getShape());
		final Dataset dataset = file.replaceDataset(name, datatype, shape, id.getBuffer(), parent);
		file.setNexusAttribute(dataset, Nexus.SDS);
	}

	@Override
	public void setData(IDataset data) throws Exception {
		writeH5Data(data, null, null);
	}
	
	@Override
	public void setHistory(IDataset... sets) throws Exception {
		if(file == null)
			file = HierarchicalDataFactory.getWriter(filePath);
		Group parent = createParentEntry(PersistenceConstants.HISTORY_ENTRY);
		int index = 0;
		for (IDataset data : sets) {
			index++;
			if(data != null) {
				String dataName = !data.getName().equals("") ? data.getName() : "history"+index;
				final Datatype      datatype = H5Utils.getDatatype(data);
				long[] shape = H5Utils.getLong(data.getShape());
				final Dataset dataset = file.replaceDataset(dataName,  datatype, shape, ((AbstractDataset)data).getBuffer(), parent);
				file.setNexusAttribute(dataset, Nexus.SDS);
			}
		}
	}

	@Override
	public void setAxes(List<? extends IDataset> axes) throws Exception{
		writeH5Data(null, axes.get(0), axes.get(1));
	}

	@Override
	public void setROIs(Map<String, IROI> rois) throws Exception {
		if (file == null) file = HierarchicalDataFactory.getWriter(filePath);

		Group parent = createParentEntry(PersistenceConstants.ROI_ENTRY);
		if (rois != null) {
			Iterator<String> it = rois.keySet().iterator();
			while(it.hasNext()){
				String name = it.next();
				IROI roi = rois.get(name);
				writeRoi(file, parent, name, roi);
			}
		}
	}

	@Override
	public void addROI(String name, IROI roiBase) throws Exception {
		if (file == null)
			file = HierarchicalDataFactory.getWriter(filePath);
		Group parent = createParentEntry(PersistenceConstants.ROI_ENTRY);
		writeRoi(file, parent, name, roiBase);
	}

	@Override
	public void setRegionAttribute(String regionName, String attributeName, String attributeValue) throws Exception {
		if ("JSON".equals(attributeName)) throw new Exception("Cannot override the JSON attribute!");
		final HObject node = file.getData(PersistenceConstants.ROI_ENTRY+"/"+regionName);
		file.setAttribute(node, attributeName, attributeValue);
	}

	@Override
	public String getRegionAttribute(String regionName, String attributeName) throws Exception{
		return file.getAttributeValue(PersistenceConstants.ROI_ENTRY+"/"+regionName+"@"+attributeName);
	}

	/**
	 * Used to set the version of the API
	 * @param version
	 * @throws Exception
	 */
	private void setVersion(String version) throws Exception {
		if (file == null) file = HierarchicalDataFactory.getWriter(filePath);
		//check if parent group exists
		Group parent = (Group)file.getData(PersistenceConstants.ENTRY);
		if(parent == null) parent = createParentEntry(PersistenceConstants.ENTRY);
		file.setAttribute(parent, "Version", version);
	}

	@Override
	public void setSite(String site) throws Exception {
		if (file == null) file = HierarchicalDataFactory.getWriter(filePath);
		//check if parent group exists
		Group parent = (Group)file.getData(PersistenceConstants.ENTRY);
		if(parent == null) parent = createParentEntry(PersistenceConstants.ENTRY);
		file.setAttribute(parent, "Site", site);
	}

	@Override
	public ILazyDataset getData(String dataName, IMonitor mon) throws Exception{
		if (file == null)
			file = HierarchicalDataFactory.getReader(filePath);
		dataName = !dataName.equals("") ? dataName : "data";
		Dataset set = (Dataset)file.getData(PersistenceConstants.DATA_ENTRY+"/"+dataName);
		set.getMetadata();
		return new H5LazyDataset(set);
	}

	/**
	 * Method to set datasets which persist history
	 * 
	 * @param data
	 * @throws Exception 
	 */
	@Override
	public Map<String, ILazyDataset> getHistory(IMonitor mon) throws Exception {
		IDataHolder dh = LoaderFactory.getData(filePath, true, mon);
		Map<String, ILazyDataset> sets = new HashMap<String, ILazyDataset>(dh.size());
		for (String name : dh.getNames()) {
			if (name.startsWith(PersistenceConstants.HISTORY_ENTRY)) {
				sets.put(name, dh.getLazyDataset(name));
			}
		}
		return sets;
	}

	@Override
	public List<ILazyDataset> getAxes(String xAxisName, String yAxisName, IMonitor mon) throws Exception {
		List<ILazyDataset> axes = new ArrayList<ILazyDataset>();
		ILazyDataset xaxis = null, yaxis = null;

		IDataHolder dh = LoaderFactory.getData(filePath, true, mon);
		xAxisName = !xAxisName.equals("") ? xAxisName : "X Axis";
		xaxis = readH5Data(dh, xAxisName, PersistenceConstants.DATA_ENTRY);
		if(xaxis != null)
			axes.add(xaxis);
		yAxisName = !yAxisName.equals("") ? yAxisName : "Y Axis";
		yaxis = readH5Data(dh, yAxisName, PersistenceConstants.DATA_ENTRY);
		if(yaxis != null)
			axes.add(yaxis);
		return axes;
	}

	@Override
	public BooleanDataset getMask(String maskName, IMonitor mon) throws Exception {
		if(file == null)
			file = HierarchicalDataFactory.getReader(filePath);
		Dataset data = (Dataset)file.getData(PersistenceConstants.MASK_ENTRY+"/"+maskName);
		Object val = data.read();
		AbstractDataset ret =  H5Utils.getSet(val,data);
		BooleanDataset bd = (BooleanDataset) DatasetUtils.cast(ret, AbstractDataset.BOOL);
		if (getVersionNumber() > 1) {
			// Inverse the dataset
			bd = Comparisons.logicalNot(bd);
		}
		return bd;
	}

	@Override
	public Map<String, IDataset> getMasks(IMonitor mon) throws Exception {
		if(file == null)
			file = HierarchicalDataFactory.getReader(filePath);
		Map<String, IDataset> masks = new HashMap<String, IDataset>();
		List<String> names = getMaskNames(mon);
		Iterator<String> it = names.iterator();
		while (it.hasNext()) {
			String name = (String) it.next();
			Dataset data = (Dataset)file.getData(PersistenceConstants.MASK_ENTRY+"/"+name);
			Object val = data.read();
			AbstractDataset ret =  H5Utils.getSet(val,data);
			BooleanDataset bd = (BooleanDataset) DatasetUtils.cast(ret, AbstractDataset.BOOL);
			if (getVersionNumber() > 1) {
				// Inverse the dataset
				bd = Comparisons.logicalNot(bd);
			}
			masks.put(name, bd);
		}
		return masks;
	}

	private Double getVersionNumber() throws Exception {
		String version = getVersion();
		if (version == null) throw new Exception("No version number could be found in the file");
		String str = version.replace("[", "").replace("]", "");
		return Double.parseDouble(str);
	}

	@Override
	public IROI getROI(String roiName) throws Exception {
		IJSonMarshaller converter = new JacksonMarshaller();
		String json = file.getAttributeValue(PersistenceConstants.ROI_ENTRY+"/"+roiName+"@JSON");
		if(json == null) throw new Exception("Reading Exception: " +PersistenceConstants.ROI_ENTRY+ " entry does not exist in the file " + filePath);
		// JSON deserialization
		json = json.substring(1, json.length()-1); // this is needed as somehow, the getAttribute adds [ ] around the json string...
		IROI roi = (IROI)converter.unmarshal(json);
		return roi;
	}

	@Override
	public Map<String, IROI> getROIs(IMonitor mon) throws Exception {
		Map<String, IROI> rois = new HashMap<String, IROI>();
		if(file == null)
			file = HierarchicalDataFactory.getReader(filePath);
		IJSonMarshaller converter = new JacksonMarshaller();
		List<String> names = getROINames(mon);
		if (names== null) return null;
		Iterator<String> it = names.iterator();
		while (it.hasNext()) {
			String name = (String) it.next();
			String json = file.getAttributeValue(PersistenceConstants.ROI_ENTRY+"/"+name+"@JSON");
			json = json.substring(1, json.length()-1); // this is needed as somehow, the getAttribute adds [ ] around the json string...
			IROI roi = (IROI) converter.unmarshal(json);
			rois.put(name, roi);
		}
		return rois;
	}

	@Override
	public List<String> getDataNames(IMonitor mon) throws Exception{
		return getNames(PersistenceConstants.DATA_ENTRY, mon);
	}

	@Override
	public List<String> getMaskNames(IMonitor mon) throws Exception{
		return getNames(PersistenceConstants.MASK_ENTRY, mon);
	}

	@Override
	public List<String> getROINames(IMonitor mon) throws Exception{
		return getNames(PersistenceConstants.ROI_ENTRY, mon);
	}

	private List<String> getNames(String path, IMonitor mon) throws Exception {
		List<String> names = null;
		IHierarchicalDataFile f = null;
		try {
			f = HierarchicalDataFactory.getReader(getFilePath());
			Group grp = (Group)f.getData(path);
			if (grp==null) throw new Exception("Reading Exception: " + path + " entry does not exist in the file " + filePath);
			List<HObject> children =  grp.getMemberList();
			if (names==null) names = new ArrayList<String>(children.size());
			for (HObject hObject : children) {
				names.add(hObject.getName());
			}
		} finally {
			if (f!=null) f.close();
		}
		return names;
	}

	@Override
	public void setDiffractionMetadata(IDiffractionMetadata metadata) throws Exception {
		if (file == null) file = HierarchicalDataFactory.getWriter(filePath);

		Group parent = HierarchicalDataFileUtils.createParentEntry(file, PersistenceConstants.DIFFRACTIONMETADATA_ENTRY,Nexus.DETECT);

		DetectorProperties detprop = metadata.getDetector2DProperties();

		H5Datatype intType = new H5Datatype(Datatype.CLASS_INTEGER, 32/8, Datatype.NATIVE, Datatype.NATIVE);
		H5Datatype doubleType = new H5Datatype(Datatype.CLASS_FLOAT, 64/8, Datatype.NATIVE, Datatype.NATIVE);

		final Dataset nXPix = file.replaceDataset("x_pixel_number", intType, new long[] {1}, new int[]{detprop.getPx()}, parent);
		file.setAttribute(nXPix,NexusUtils.UNIT, "pixels");
		final Dataset nYPix = file.replaceDataset("y_pixel_number", intType, new long[] {1}, new int[]{detprop.getPy()}, parent);
		file.setAttribute(nYPix,NexusUtils.UNIT , "pixels");

		final Dataset sXPix = file.replaceDataset("x_pixel_size", doubleType, new long[] {1}, new double[]{detprop.getHPxSize()}, parent);
		file.setAttribute(sXPix, NexusUtils.UNIT, "mm");
		final Dataset sYPix = file.replaceDataset("y_pixel_size", doubleType, new long[] {1}, new double[]{detprop.getVPxSize()}, parent);
		file.setAttribute(sYPix, NexusUtils.UNIT, "mm");

		double[] beamVector = new double[3];
		detprop.getBeamVector().get(beamVector);
		file.replaceDataset("beam_vector", doubleType, new long[] {3}, beamVector, parent);

		double[] beamCentre = detprop.getBeamCentreCoords();

		double dist = detprop.getBeamCentreDistance();

		final Dataset centre = file.replaceDataset("beam_centre", doubleType, new long[] {2}, beamCentre, parent);
		file.setAttribute(centre,NexusUtils.UNIT, "pixels");
		final Dataset distance = file.replaceDataset("distance", doubleType, new long[] {1}, new double[] {dist}, parent);
		file.setAttribute(distance, NexusUtils.UNIT, "mm");

		Matrix3d or = detprop.getOrientation();
		double[] orientation = new double[] {or.m00 ,or.m01, or.m02,
				or.m10, or.m11, or.m12,
				or.m20, or.m21, or.m22};

		file.replaceDataset("detector_orientation", doubleType, new long[] {9}, orientation, parent);

		DiffractionCrystalEnvironment crysenv = metadata.getDiffractionCrystalEnvironment();

		final Dataset energy = file.replaceDataset("energy", doubleType, new long[] {1}, new double[]{crysenv.getWavelength()}, parent);
		file.setAttribute(energy, NexusUtils.UNIT, "Angstrom");

		final Dataset count = file.replaceDataset("count_time", doubleType, new long[] {1}, new double[]{crysenv.getExposureTime()}, parent);
		file.setAttribute(count, NexusUtils.UNIT, "s");

		final Dataset phi_start = file.createDataset("phi_start", doubleType, new long[] {1}, new double[]{crysenv.getPhiStart()}, parent);
		file.setAttribute(phi_start, NexusUtils.UNIT, "degrees");

		final Dataset phi_range = file.replaceDataset("phi_range", doubleType, new long[] {1}, new double[]{crysenv.getPhiRange()}, parent);
		file.setAttribute(phi_range, NexusUtils.UNIT, "degrees");
	}

	@Override
	public IDiffractionMetadata getDiffractionMetadata(IMonitor mon) throws Exception {
		//Reverse of the setMetadata.  Would be nice in the future to be able to use the
		//LoaderFactory but work needs to be done on loading specific metadata from nexus
		//files first
		NexusDiffractionMetaReader nexusDiffReader = new NexusDiffractionMetaReader(filePath);
		return nexusDiffReader.getDiffractionMetadataFromNexus(null);	
	}

	@Override
	public String getVersion() throws Exception {
		return file.getAttributeValue(PersistenceConstants.ENTRY+"@Version");
	}

	@Override
	public String getSite() throws Exception {
		return file.getAttributeValue(PersistenceConstants.ENTRY+"@Site");
	}
	
	@Override
	public boolean containsData() {
		return isEntry(PersistenceConstants.DATA_ENTRY,null);
	}

	@Override
	public boolean containsMask() {
		return isEntry(PersistenceConstants.MASK_ENTRY,null);
	}

	@Override
	public boolean containsRegion() {
		return isEntry(PersistenceConstants.ROI_ENTRY,null);
	}

	@Override
	public boolean containsDiffractionMetadata() {
		return isEntry(PersistenceConstants.DIFFRACTIONMETADATA_ENTRY,null);
	}

	@Override
	public boolean containsFunction() {
		return isEntry(PersistenceConstants.FUNCTION_ENTRY,null);
	}

	private boolean isEntry(String entryPath, IMonitor mon)  {
		HObject hOb = null;
		try {
			hOb = file.getData(entryPath);
			return hOb != null;
		} catch (Exception e) {
			logger.debug("Error while reading file: "+ e);
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * Method to write image data (and axis) to an HDF5 file given a specific path entry to save the data.
	 * 
	 * @param data
	 * @param xAxisData
	 * @param yAxisData
	 * @throws Exception
	 */
	private void writeH5Data(final IDataset data, 
							final IDataset xAxisData, 
							final IDataset yAxisData) throws Exception {

		if(file == null)
			file = HierarchicalDataFactory.getWriter(filePath);

		Group parent = createParentEntry(PersistenceConstants.DATA_ENTRY);

		if(data != null){

			String dataName = !data.getName().equals("") ? data.getName() : "data";
			final Datatype      datatype = H5Utils.getDatatype(data);
			long[] shape = H5Utils.getLong(data.getShape());

			final Dataset dataset = file.replaceDataset(dataName,  datatype, shape, ((AbstractDataset)data).getBuffer(), parent);
			file.setNexusAttribute(dataset, Nexus.SDS);
		}
		if(xAxisData != null){
			String xAxisName = !xAxisData.getName().equals("") ? xAxisData.getName() : "X Axis";
			final Datatype      xDatatype = H5Utils.getDatatype(xAxisData);
			long[] shape = H5Utils.getLong(xAxisData.getShape());

			final Dataset xDataset = file.replaceDataset(xAxisName,  xDatatype, shape, ((AbstractDataset)xAxisData).getBuffer(), parent);
			file.setNexusAttribute(xDataset, Nexus.SDS);
		}

		if(yAxisData != null){
			String yAxisName = !yAxisData.getName().equals("") ? yAxisData.getName() : "Y Axis";
			final Datatype      yDatatype = H5Utils.getDatatype(yAxisData);
			long[] shape = H5Utils.getLong(yAxisData.getShape());

			final Dataset yDataset = file.replaceDataset(yAxisName,  yDatatype, shape,((AbstractDataset)yAxisData).getBuffer(), parent);
			file.setNexusAttribute(yDataset, Nexus.SDS);
		}
	}

	/**
	 * Method to write rois data to an HDF5 file given a specific path entry to save the data.<br>
	 * The rois are serialised using GSON and are saved as JSON format in the HDF5 file.
	 * 
	 * @param rois
	 * @throws Exception
	 */
	private HObject writeRoi(IHierarchicalDataFile file, 
			Group   parent,
			String  name,
			IROI    roi) throws Exception {
		long[] dims = {1};
		IJSonMarshaller converter = new JacksonMarshaller();
		String json = converter.marshal(roi);
		// we create the dataset
		Dataset dat = file.replaceDataset(name, new H5Datatype(Datatype.CLASS_INTEGER, 4, Datatype.NATIVE, Datatype.NATIVE), dims, new int[]{0}, parent);
		// we set the JSON attribute
		file.setAttribute(dat, "JSON", json);
		return dat;
	}

	private Group createParentEntry(String fullEntry) throws Exception {
		return HierarchicalDataFileUtils.createParentEntry(file, fullEntry, Nexus.DATA);
	}

	/**
	 * Method to read image data (axes, masks, image) from an HDF5 file and returns an ILazyDataset

	 * @return ILazyDataset
	 * @throws Exception 
	 */
	private ILazyDataset readH5Data(IDataHolder dh, String dataName, String dataEntry) throws Exception{
		ILazyDataset ld = dh.getLazyDataset(dataEntry+"/"+dataName);
		if(ld == null) throw new Exception("Reading Exception: " +dataEntry+ " entry does not exist in the file " + filePath);
		return ld;
	}

	/**
	 * Method to read mask data from an HDF5 file
	 * @return BooleanDataset
	 * @throws Exception 
	 */
	@SuppressWarnings("unused")
	private BooleanDataset readH5Mask(IDataHolder dh, String maskName) throws Exception{
		ILazyDataset ld = dh.getLazyDataset(PersistenceConstants.MASK_ENTRY+"/"+maskName);
		if (ld instanceof H5LazyDataset) {
			return (BooleanDataset)DatasetUtils.cast(((H5LazyDataset)ld).getCompleteData(null), AbstractDataset.BOOL);
		} else {
			return (BooleanDataset)DatasetUtils.cast(dh.getDataset(PersistenceConstants.MASK_ENTRY+"/"+maskName), AbstractDataset.BOOL);
		}
	}

	@Override
	public void close() {
		try {
			if (file != null) {
				file.close();
			}
		} catch (Exception e) {
			logger.debug("Cannot close " + filePath, e);
		}
	}

	public IHierarchicalDataFile getFile(){
		return file;
	}

	public String getFilePath(){
		return filePath;
	}

	@Override
	public boolean isRegionSupported(IROI roi) {
		return JacksonMarshaller.isROISupported(roi);
	}

	@Override
	public void setFunctions(Map<String, IFunction> functions) throws Exception {
		if (file == null) file = HierarchicalDataFactory.getWriter(filePath);
		Group parent = createParentEntry(PersistenceConstants.FUNCTION_ENTRY);
		if (functions != null) {
			IJSonMarshaller converter = new JacksonMarshaller();
			Iterator<String> it = functions.keySet().iterator();
			while(it.hasNext()){
				String name = it.next();
				IFunction function = functions.get(name);
				writeFunction(file, parent, name, function, converter);
			}
		}
	}

	@Override
	public void addFunction(String name, IFunction function) throws Exception {
		if (file == null) file = HierarchicalDataFactory.getWriter(filePath);
		Group parent = createParentEntry(PersistenceConstants.FUNCTION_ENTRY);
		IJSonMarshaller converter = new JacksonMarshaller();
		writeFunction(file, parent, name, function, converter);
	}

	@Override
	public IFunction getFunction(String functionName) throws Exception {
		String json = file.getAttributeValue(PersistenceConstants.FUNCTION_ENTRY+"/"+functionName);
		if(json == null) throw new Exception("Reading Exception: " +PersistenceConstants.FUNCTION_ENTRY+ " entry does not exist in the file " + filePath);
		IJSonMarshaller converter = new JacksonMarshaller();
		//Deserialize the json back to a function
		IFunction function = (IFunction) converter.unmarshal(json);
		return function;
	}

	@Override
	public Map<String, IFunction> getFunctions(IMonitor mon) throws Exception {
		Map<String, IFunction> functions = new HashMap<String, IFunction>();
		if(file == null)
			file = HierarchicalDataFactory.getReader(filePath);
		IJSonMarshaller converter = new JacksonMarshaller();
		List<String> names = getFunctionNames(mon);
		Iterator<String> it = names.iterator();
		while (it.hasNext()) {
			String name = (String) it.next();
			String json = file.getAttributeValue(PersistenceConstants.FUNCTION_ENTRY+"/"+name+"@JSON");
			json = json.substring(1, json.length()-1); // this is needed as somehow, the getAttribute adds [ ] around the json string...
			//Deserialize the json back to a function
			IFunction function = (IFunction) converter.unmarshal(json);
			functions.put(name, function);
		}
		return functions;
	}

	@Override
	public List<String> getFunctionNames(IMonitor mon) throws Exception {
		List<String> names = null;
		IHierarchicalDataFile file = null;
		try {
			file      = HierarchicalDataFactory.getReader(getFilePath());
			Group grp = (Group)file.getData(PersistenceConstants.FUNCTION_ENTRY);
			if (grp==null) throw new Exception("Reading Exception: " +PersistenceConstants.FUNCTION_ENTRY+ " entry does not exist in the file " + filePath);
			List<HObject> children =  grp.getMemberList();
			if (names==null) names = new ArrayList<String>(children.size());
			for (HObject hObject : children) {
				names.add(hObject.getName());
			}
		} finally {
			if (file!=null) file.close();
		}
		return names;
	}

	private HObject writeFunction(IHierarchicalDataFile file, Group parent,
			String name, IFunction function, IJSonMarshaller converter) throws Exception {
		long[] dims = {1};
		String json = converter.marshal(function);
		// we create the dataset
		Dataset dat = file.replaceDataset(name, new H5Datatype(Datatype.CLASS_INTEGER, 4, Datatype.NATIVE, Datatype.NATIVE), dims, new int[]{0}, parent);
		// we set the JSON attribute
		file.setAttribute(dat, "JSON", json);
		return dat;
	}
}
