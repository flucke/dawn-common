/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.dawnsci.conversion;

import static org.junit.Assert.fail;

import java.io.File;
import java.util.Arrays;

import org.dawb.common.services.conversion.IConversionContext;
import org.dawb.common.services.conversion.IConversionContext.ConversionScheme;
import org.dawb.common.services.conversion.IConversionService;
import org.dawb.common.util.io.FileUtils;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;

public class CompareConvertTest {

	@Test
	public void testDataSimple() throws Exception {
		
		System.out.println("starting testImageSimple");
		doTest("testCompareSimple", 10);
	}


    private void doTest(String testname, int size) throws Exception {

		final File image = new File("testfiles/pCMF48_red_new_36408_1.nxs");
		
		final File dir   = new File(System.getProperty("java.io.tmpdir"), "CompareTest_"+testname);
		try {
			// Copy the file a few times.
			for (int i = 0; i < size; i++) {
				final File nf = new File(dir, "copy_"+i+".nxs");
				nf.deleteOnExit();
				FileUtils.copyNio(image, nf);
			}
			
							
			IConversionService service = new ConversionServiceImpl();
	
			final IConversionContext context = service.open(dir.getAbsolutePath()+"/.*nxs");
			final File output = new File(dir.getAbsolutePath()+"/compare_convert_test.h5");
			context.setOutputPath(output.getAbsolutePath());
			context.setDatasetNames(Arrays.asList("/entry1/instrument/cold_head_temp/cold_head_temp", 
											      "/entry1/instrument/xas_scannable/Energy", 
												  "/entry1/instrument/FFI0/FFI0"));
			context.setConversionScheme(ConversionScheme.COMPARE);
	
			service.process(context);
			
			final IDataHolder holder = LoaderFactory.getData(output.getAbsolutePath());

			ILazyDataset set = holder.getLazyDataset("/entry1/instrument/cold_head_temp/cold_head_temp");
			final int[] shape = new int[]{size, 436};
			if (!Arrays.equals(set.getShape(), shape)) {
				fail(set.getName()+" written with shape "+Arrays.toString(set.getShape())+", but expected shape was "+Arrays.toString(shape));
			}

		} finally {
			FileUtils.recursiveDelete(dir);
		}
					
	}
}
