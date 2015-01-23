/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.dawnsci.persistence.json;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Interface used to marshall from ROIBean/FunctionBean to JSON
 * and unmarshall from JSON to ROIBean/FunctionBean
 * 
 * @author wqk87977
 *
 */
public interface IJSonMarshaller {

	/**
	 * Returns a JSON String given an object
	 * @param obj
	 * @return
	 * @throws JsonProcessingException 
	 */
	public String marshal(Object obj) throws Exception;

	/**
	 * Returns an object given a JSON String
	 * @param json
	 * @return
	 * @throws Exception
	 */
	public Object unmarshal(String json) throws Exception;
}
