/*
 * Copyright 2016 Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.asc.utils;

import java.io.InputStream;

import junit.framework.TestCase;

/**
 * Basic tests for TypeAnnotationScanner.
 * 
 * @author Andy Clement
 */
public class TypeAnnotationScannerTests extends TestCase {

	public void testBasic() {
		Class<?> functionalInterfaceClass = FunctionalInterface.class;
		byte[] runnableBytes = loadBytes("java/lang/Runnable.class");
		byte[] stringBytes = loadBytes("java/lang/String.class");

		assertTrue(TypeAnnotationScanner.scanClassBytesForAnnotation(runnableBytes, functionalInterfaceClass));
		assertFalse(TypeAnnotationScanner.scanClassBytesForAnnotation(stringBytes, functionalInterfaceClass));

		assertTrue(TypeAnnotationScanner.scanClassBytesForAnnotation(runnableBytes, "Ljava/lang/FunctionalInterface;", true));
		assertFalse(TypeAnnotationScanner.scanClassBytesForAnnotation(stringBytes, "Ljava/lang/FunctionalInterface;", true));

		assertFalse(TypeAnnotationScanner.scanClassBytesForAnnotation(runnableBytes, "Ljava/lang/FunctionalInterface;", false));
		assertFalse(TypeAnnotationScanner.scanClassBytesForAnnotation(stringBytes, "Ljava/lang/FunctionalInterface;", false));
	}
	
	private byte[] loadBytes(String resourceName) {
		InputStream stream = TypeAnnotationScannerTests.class.getClassLoader().getResourceAsStream(resourceName);
		return TypeAnnotationScanner.loadBytes(stream);
	}
	
}
