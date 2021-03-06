/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */ 
package org.dawb.common.python.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;

/**
 * We no longer run jep tests in the unit tests, more interesting is the
 * python RPC tests.
 * 
 * Just a collection of suites
 * @author gerring
 *
 */
@RunWith(org.junit.runners.Suite.class)
@SuiteClasses({ 
	
	org.dawb.common.python.test.rpc.NumpyRpcTest.class

	// TODO Recreate these using the new python rpc link.
	//org.dawb.common.python.test.PythonCommandTest.class,      
    //org.dawb.common.python.test.JepTest.class,
	//org.dawb.common.python.test.JepThreadTest.class,
	//org.dawb.common.python.test.NumpyTest.class,
	//org.dawb.common.python.test.NumpyThreadTest.class
})
public class Suite {
	// Run this as a junit plugin test and all the links will be satisfied.
}
