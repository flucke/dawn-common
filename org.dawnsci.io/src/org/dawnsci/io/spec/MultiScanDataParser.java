/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */ 
package org.dawnsci.io.spec;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.IntegerDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class deals with multi-scan ascii spec file data.
 * 
 * This file acts like a data struture returned by LoaderFactory but
 * has more levels and deals normally with small data.
 * 
 * Unlike hdf5 everything is read into memory at the start.
 * 
 * This class is a bit Hacked at the moment with methods and 
 * updates and fields with position sensitive data. However it
 * is encapsulated and can be improved when and if we go for something
 * like this.
 * 
 * @author gerring
 *
 */
public class MultiScanDataParser {
	
	private static final Logger logger = LoggerFactory.getLogger(MultiScanDataParser.class);
	
	private int scanNumber = 0;
	private Map<String,Collection<Dataset>> data;
	private MultiScanDataListener listener;
	private boolean parseComplete = false;
	
	/**
	 * Parses everything into memory, blocks until done.
	 * @param input
	 * @throws Exception
	 */
	public MultiScanDataParser(final InputStream input) throws Exception {
		
		data = new LinkedHashMap<String, Collection<Dataset>>(27);
		createData(input);
		finishScan(getScanName());
		parseComplete = true;
	}
	
	private InputStream inputStream;
	/**
	 * Sends everything back to the listener, and keeps no data
	 * in memory. Used for parsing spec files in the workflow tool.
	 * Uses a thread to notify the listener.
	 * 
	 * @param input
	 * @throws Exception
	 */
	public MultiScanDataParser(final InputStream input, final MultiScanDataListener listener) {
		
		this.listener = listener;
		this.data     = new LinkedHashMap<String, Collection<Dataset>>(1);
		this.inputStream = input;
	}
	
	/**
	 * Called to start parsing if SpecDataListener is being used, throws
	 * an 
	 */
	public void start() throws Exception {
		
		if (inputStream==null||listener==null) throw new Exception("Not allowed to start, already parsing or parsed!");
		
		final Thread worker = new Thread(new Runnable() {
			public void run() {
				try {
					createData(inputStream);
					final boolean requireMore = finishScan(getScanName());
					if (!requireMore) return;
					
				} catch (Exception ne) {
					logger.error("Cannot parse spec file!", ne);
				} finally {
					parseComplete = true;
				}
			}
		}, "Spec File Parsing Thread");
		
		worker.setDaemon(true); // You can cancel if it is still running
		worker.start();
	}


	public Collection<Dataset> getSets(final String scanName) {
		return data.get(scanName);
	}
	
	public Collection<Dataset> removeScan(final String scanName) {
		return data.remove(scanName);
	}
	
	public Collection<String> getScanNames() {
		return data.keySet();
	}

	public void clear() {
		data.clear();
	}

	private DefaultMutableTreeNode root;

	public TreeNode getNode() {
		
		if (data==null) return null;
        
		if (root==null) root = new DefaultMutableTreeNode("Spec");
		updateNode(null);
		return root;
	}

	/**
	 * Returns the tree node that was updated or null if nothing or
	 * everything was updated.
	 * 
	 * @param newScanName
	 * @return
	 */
	public TreeNode updateNode(final String newScanName) {
		
		if (newScanName!=null) {
			for (int i = 0; i < root.getChildCount(); i++) {
				final DefaultMutableTreeNode scan = (DefaultMutableTreeNode)root.getChildAt(i);
				
				if (scan.getUserObject().equals(newScanName)) {
					scan.removeAllChildren();
					
					final Collection<Dataset> sets = data.get(newScanName);
					if (sets==null) return null;
					for (Dataset as : sets) {
						scan.add(new DefaultMutableTreeNode(as));
					}
					return scan;
				}
			}
			
			// Did not find it.
			final DefaultMutableTreeNode scan = new DefaultMutableTreeNode(newScanName);
			root.add(scan);
			final Collection<Dataset> sets = data.get(newScanName);
			if (sets==null) return null;
			for (Dataset as : sets) {
				scan.add(new DefaultMutableTreeNode(as));
			}
			return scan;
		
		}
		
		
		// Update everything
		for (String scanName : data.keySet()) {
			updateNode(scanName);
		}
		return null;
	}
	

	private void createData(final InputStream in) throws Exception {
		
		final BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        try {
        	String line = null;
        	boolean firstLine = true;
        	while((line=reader.readLine())!=null) {
        		if (firstLine) {
        			firstLine = false;
        			if (line.trim().startsWith("&")) throw new Exception("Cannot load SRS files with SpecLoader!");
        		}
        		processLine(line);
        	}
        	
        } finally {
        	reader.close();
        }
		
	}

	//private boolean            removedIndex = false;
	private String             previousLine = null;
	private List<NumberObject> currentScans;	
	private List<String>       currentNames;	
	
	/**
	 * Processes a line and adds it to the scan data.
	 * Returns true if data added requires a replot.
	 * @param line
	 */
	public synchronized String processLine(String line) {
		
		if (line==null)      return null;
		line = line.trim();
		if ("".equals(line)) return null;
		
		final Matcher com = SpecSyntax.COMMENT.matcher(line);
		if (com!=null && com.matches()) {
			previousLine = line;
			return null;
		}
		
		final Matcher scan = SpecSyntax.SCAN_LINE.matcher(line);
		if (scan==null || !scan.matches()) return null;
		
		boolean newScan = false;
		if (previousLine!=null) {
			
			final Matcher header = SpecSyntax.HEADER_LINE.matcher(previousLine);
			
			if (header!=null && header.matches()) {
				newScan = true;				
				finishScan(getScanName());
				startScan(header);
			}

		}
 		
		addData(newScan, scan);
		return getScanName(); // Hack
	}


	/**
	 * Converts currentScans to datasets and adds them to data
	 * @return true if listener and it is still interested, 
	 *         true if no listener
	 *         false if listener is finished.
	 */
	private boolean finishScan(final String scanName) {
		update(true);
		
		if (listener!=null) {
			final Collection<Dataset> data = removeScan(scanName);
			if (data==null) return true;
			return listener.specDataPerformed(new MultiScanDataEvent(this, scanName, data));
		}
		
		return true;
	}

	public void update(final boolean endScan) {
		
		if (currentScans==null) return;
		
		final Collection<Dataset> sets = data.get(getScanName());
		
		// We will add new datasets now
		if (sets!=null) {
			sets.clear();
			for (NumberObject<Number> o : currentScans) {
				sets.add(o.toDataset());
			}
		}
		
		if (endScan) currentScans.clear();
	}

	private void startScan(final Matcher header) {
		
		previousLine = null;
		scanNumber++;
		
		if (currentNames==null) currentNames = new ArrayList<String>(27);
		if (currentScans==null) currentScans = new ArrayList<NumberObject>(27);
		currentNames.clear();
		currentScans.clear();
				
		for (int i = 1; i <= header.groupCount(); i++) {
			final String name = header.group(i);
			if (name==null) continue;
			currentNames.add(name.trim());
		}
		
		Collection<Dataset> sets = new ArrayList<Dataset>(currentScans.size());
		data.put(getScanName(), sets);
	}
	
	private String getScanName() {
		return "Scan "+scanNumber;
	}

	private void addData(final boolean newScan, final Matcher scan) {
		
		int index = 0;
		for (int i = 1; i <= scan.groupCount(); i++) {
			
			final String val = scan.group(i);
			if (val==null) continue;
			if ("".equals(val.trim())) continue;
			if (val.toLowerCase().startsWith("e")) continue;
			
			// Pretty inefficient but there we go.
			if (newScan) {
			
				final NumberObject ad = new NumberObject<Float>();
				String name;
				try {
					name = currentNames.get(index);
				} catch (Throwable ne) {
					name = "Column "+i;
				}
				ad.setName(name);
				currentScans.add(ad);
			}
			
			final NumberObject<Number> ad = (NumberObject<Number>)currentScans.get(index);
		    ad.add(Float.parseFloat(val.trim()));
	
			index++;
		}
		previousLine = null;		
	}


	private static class NumberObject<T extends Number> {
		
		private String  name;
		private List<T> numbers;
		NumberObject() {
			setNumbers(new ArrayList<T>(31));
		}
		public Dataset toDataset() {
			if (numbers.isEmpty()) return null;
			
			Dataset ret = null;
            if (numbers.get(0) instanceof Integer) {
            	final Integer[] ia   = numbers.toArray(new Integer[numbers.size()]);
            	final int    [] intA = new int[ia.length];
            	for (int i = 0; i < intA.length; i++) intA[i] = ia[i];
            	ret = new IntegerDataset(intA, intA.length);
            	ret.setName(getName());
            	
            } else if (numbers.get(0) instanceof Float) {
            	try {
	            	final Float[]  fa  = numbers.toArray(new Float[numbers.size()]);
	            	final float[] fltA = new float[fa.length];
	            	for (int i = 0; i < fltA.length; i++) fltA[i] = fa[i];
	            	ret = new FloatDataset(fltA, fltA.length);
	            	ret.setName(getName());
            	} catch (Exception ne) {
            		ne.printStackTrace();
            	}
            }
            
            return ret;
		}
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public List<? extends Number> getNumbers() {
			return numbers;
		}
		public void setNumbers(List<T> numbers) {
			this.numbers = numbers;
		}
		public void add(T n) {
			numbers.add(n);
		}
	}
	
	public boolean isParseComplete() {
		return parseComplete;
	}

	
}
