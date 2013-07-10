package org.dawnsci.conversion.converters;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ncsa.hdf.object.Dataset;
import ncsa.hdf.object.Datatype;
import ncsa.hdf.object.Group;

import org.dawb.common.services.conversion.IConversionContext;
import org.dawb.hdf5.HierarchicalDataFactory;
import org.dawb.hdf5.IHierarchicalDataFile;
import org.dawb.hdf5.Nexus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.AggregateDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.dataset.ILazyDataset;
import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;

public class Convert1DtoND extends AbstractConversion {
	
	private final static Logger logger = LoggerFactory.getLogger(Convert1DtoND.class);
	
	Map<String, List<ILazyDataset>> dataMap = new HashMap<String, List<ILazyDataset>>();

	public Convert1DtoND(IConversionContext context) throws Exception {
		super(context);
		
		File file = new File(context.getOutputPath());
		
		//overwrite if exists
		if (file.exists()) {
        	file.delete();
        } else {
        	file.getParentFile().mkdirs();   	
        }
		
		//test file can be opened
		IHierarchicalDataFile fileH = HierarchicalDataFactory.getWriter(context.getOutputPath());
		fileH.close();
		
	}

	@Override
	protected void convert(AbstractDataset slice) throws Exception {
		
		if (!dataMap.containsKey(slice.getName()))dataMap.put(slice.getName(), new ArrayList<ILazyDataset>());
		dataMap.get(slice.getName()).add(slice);

	}
	
	@Override
	public void close(IConversionContext context) {

		IHierarchicalDataFile file = null;
		try {
			file = HierarchicalDataFactory.getWriter(context.getOutputPath());
			
			IDataset axis = null;
			int axisLength = -1;
			String axisName = context.getAxisDatasetName();
			
			if (axisName != null) {
				axis = LoaderFactory.getDataSet(context.getFilePaths().get(0), axisName, null);
				axisLength = axis.getShape()[0];
				
			}

			for (String key : dataMap.keySet()) {
				List<ILazyDataset> out = dataMap.get(key);
				String[] paths = getNexusPathAndNameFromKey(key);

				Group entry = file.group(paths[0]);
				file.setNexusAttribute(entry, Nexus.ENTRY);

				if (paths.length>2) {
					for (int i = 1; i < paths.length-1; i++) {
						final String path = paths[i];
						entry = file.group(path, entry);
						if (i<(paths.length-1)) file.setNexusAttribute(entry, Nexus.ENTRY);
					}
				}
				
				if (context.getUserObject() == null ||
					!(context.getUserObject() instanceof Convert1DInfoBean)) {
					
					saveTo2DStack(file, entry, out, paths, key,axisLength);
					
				} else {
					
					Convert1DInfoBean bean = (Convert1DInfoBean)context.getUserObject();
					
					if (bean.fastAxis*bean.slowAxis != out.size()) {
						saveTo2DStack(file, entry, out, paths, key, axisLength);
					} else {
						saveTo3DStack(file, entry, out, paths, key, bean,axisLength);
					}
				}
			}
			if (axis != null) {
				String[] paths = getNexusPathAndNameFromKey(axisName);
				Group entry = file.group(paths[0]);
				file.setNexusAttribute(entry, Nexus.ENTRY);
				
				if (paths.length>2) {
					for (int i = 1; i < paths.length-1; i++) {
						final String path = paths[i];
						entry = file.group(path, entry);
						if (i<(paths.length-1)) file.setNexusAttribute(entry, Nexus.ENTRY);
					}
				}
				saveAxis(file, entry, (AbstractDataset)axis, paths);
			}
			
			
			file.close();
		} catch (Exception e) {
			if (file != null)
				try {
					file.close();
				} catch (Exception e1) {
					logger.error("Problem writing to h5 file :" + e1.getMessage() +" and inner: " + e.getMessage());
				}
		}
	}
	
	private void saveAxis(IHierarchicalDataFile file,Group entry, AbstractDataset out, String[] paths) throws Exception {
		
		String name = paths[paths.length-1];
		Datatype dt = getDatatype(out);
		long[] shape = getLong(out.getShape());
		
		Dataset d = file.createDataset(name, dt, shape, out.getBuffer(), entry);
		
		file.setNexusAttribute(d, Nexus.SDS);
		file.setAttribute(d,"axis","1");
	}
	
	private void saveTo2DStack(IHierarchicalDataFile file,Group entry, List<ILazyDataset> out,String[] paths,String key, int axisLength) throws Exception{
		
		String name = paths[paths.length-1];
		Datatype dt = getDatatype(out.get(0).getSlice());
		long[] shape = getLong(out.get(0).getShape());
		
		AbstractDataset first = ((AbstractDataset)out.get(0).getSlice());
		
		Dataset d = file.appendDataset(name, dt, shape, first.getBuffer(), entry);
		
		if (first.getShape()[0] == axisLength) file.setAttribute(d,"signal","1");
		
		file.setNexusAttribute(d, Nexus.SDS);
		file.setAttribute(d, "original_name", key);				
		
		for (int i = 1; i < out.size(); i++) {
			d = file.appendDataset(name, dt, shape, ((AbstractDataset)out.get(i).getSlice()).getBuffer(), entry);
		}
	}
	
	private void saveTo3DStack(IHierarchicalDataFile file,Group entry, List<ILazyDataset> out,String[] paths,String key, Convert1DInfoBean bean,int axisLength) throws Exception{
		
		ILazyDataset[] lz = new ILazyDataset[bean.fastAxis];
		String name = paths[paths.length-1];

		Datatype dt = getDatatype(out.get(0).getSlice());
		Dataset d = null;
		
		AbstractDataset first = ((AbstractDataset)out.get(0).getSlice());
		
		for (int i = 0; i < bean.slowAxis; i++) {
			
			for (int j = 0; j < bean.fastAxis; j++) {
				lz[j] = out.get(i*bean.fastAxis + j);
			}
			
			AggregateDataset ds = new AggregateDataset(true, lz);
			
			d = file.appendDataset(name, dt, getLong(ds.getShape()), ((AbstractDataset)ds.getSlice()).getBuffer(), entry);
			
		}
		
		file.setNexusAttribute(d, Nexus.SDS);
		file.setAttribute(d, "original_name", key);
		if (first.getShape()[0] == axisLength) file.setAttribute(d,"signal","1");
		
	}
	
	private String[] getNexusPathAndNameFromKey(String key) {
		String[]  paths = key.split("/");
		if ("".equals(paths[0])) paths = Arrays.copyOfRange(paths, 1, paths.length);
		return paths;
	}
	
	public static final class Convert1DInfoBean {
	 public int fastAxis = 0;
	 public int slowAxis = 0;
	}

}
