/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.dawnsci.conversion;

import static org.junit.Assert.assertArrayEquals;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.dawb.common.services.conversion.IConversionContext;
import org.dawb.common.services.conversion.IConversionContext.ConversionScheme;
import org.dawb.common.services.conversion.IConversionService;
import org.dawnsci.conversion.converters.Convert1DtoND.Convert1DInfoBean;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;

public class Convert1DtoNDTest {
	
	private String testfile = "MoKedge_1_15.nxs";
	private String nonNexusTest = "HyperOut.dat";
	
	@Test
	public void test1DSimple() throws Exception {
		
		IConversionService service = new ConversionServiceImpl();
		
		// Determine path to test file
		final String path = getTestFilePath(testfile);
		
		String[] paths = new String[]{path,path,path,path};
		
		final IConversionContext context = service.open(paths);
		
		final File tmp = File.createTempFile("testSimple", ".nxs");
		tmp.deleteOnExit();
        context.setOutputPath(tmp.getAbsolutePath());
        context.setConversionScheme(ConversionScheme.H5_FROM_1D);
        context.setAxisDatasetName("/entry1/counterTimer01/Energy");
        context.setDatasetName("/entry1/counterTimer01/(I0|lnI0It|It)");
        
        service.process(context);
        
        final IDataHolder   dh    = LoaderFactory.getData(tmp.getAbsolutePath());
        final List<String> names = Arrays.asList("/entry1/counterTimer01/I0","/entry1/counterTimer01/lnI0It","/entry1/counterTimer01/It");
        for (String name : names) {
            ILazyDataset ds = dh.getLazyDataset(name);
            assertArrayEquals(new int[] {4,489},ds.getShape());
		}
        
        ILazyDataset ds = dh.getLazyDataset("/entry1/counterTimer01/Energy");
        assertArrayEquals(new int[] {489},ds.getShape());
   	}
	
	@Test
	public void test3DSimple() throws Exception {
		
		IConversionService service = new ConversionServiceImpl();
		
		// Determine path to test file
		final String path = getTestFilePath(testfile);
		
		String[] paths = new String[]{path,path,path,path,path,path,path,path,path,path,path,path};
		
		final IConversionContext context = service.open(paths);
		
		Convert1DInfoBean bean = new Convert1DInfoBean();
		bean.fastAxis = 4;
		bean.slowAxis = 3;
		
		context.setUserObject(bean);
		
		final File tmp = File.createTempFile("testSimple3d", ".nxs");
		tmp.deleteOnExit();
        context.setOutputPath(tmp.getAbsolutePath());
        context.setConversionScheme(ConversionScheme.H5_FROM_1D);
        context.setAxisDatasetName("/entry1/counterTimer01/Energy");
        context.setDatasetName("/entry1/counterTimer01/(I0|lnI0It|It)");
        
        service.process(context);
        
        final IDataHolder   dh    = LoaderFactory.getData(tmp.getAbsolutePath());
        final List<String> names = Arrays.asList("/entry1/counterTimer01/I0","/entry1/counterTimer01/lnI0It","/entry1/counterTimer01/It");
        for (String name : names) {
            ILazyDataset ds = dh.getLazyDataset(name);
            assertArrayEquals(new int[] {3,4,489},ds.getShape());
		}
        ILazyDataset ds = dh.getLazyDataset("/entry1/counterTimer01/Energy");
        assertArrayEquals(new int[] {489},ds.getShape());
   	}
	
	@Test
	public void test1DNotNexus() throws Exception {
		
		IConversionService service = new ConversionServiceImpl();
		
		// Determine path to test file
		final String path = getTestFilePath(nonNexusTest);
		
		String[] paths = new String[]{path,path,path,path};
		
		final IConversionContext context = service.open(paths);
		
		final File tmp = File.createTempFile("testSimple", ".nxs");
		tmp.deleteOnExit();
        context.setOutputPath(tmp.getAbsolutePath());
        context.setConversionScheme(ConversionScheme.H5_FROM_1D);
        context.setAxisDatasetName("x");
        context.setDatasetName("(dataset_0|dataset_1)");
        
        service.process(context);
        
        final IDataHolder   dh    = LoaderFactory.getData(tmp.getAbsolutePath());
        final List<String> names = Arrays.asList("/entry1/dataset_0","/entry1/dataset_1");
        for (String name : names) {
            ILazyDataset ds = dh.getLazyDataset(name);
            assertArrayEquals(new int[] {4,1608},ds.getShape());
		}
        
        ILazyDataset ds = dh.getLazyDataset("/entry1/x");
        assertArrayEquals(new int[] {1608},ds.getShape());
   	}
	
private String getTestFilePath(String fileName) {
		
		final File test = new File("testfiles/"+fileName);
		return test.getAbsolutePath();
	
	}

}
