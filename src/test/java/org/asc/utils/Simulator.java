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

import java.io.File;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Simple driver for the TypeAnnotationScanner to check it is OK.
 * 
 * @author Andy Clement
 *
 */
public class Simulator {

	public static void main(String[] args) throws Exception {
		loadLotsOfClasses();
		time("oracle/jrockit/jfr/VMJFR.class",1000000);
		time("oracle/jrockit/jfr/VMJFR.class",1000000);
		time("java/lang/Runnable.class",1000000);
		time("java/lang/Runnable.class",1000000);
	}

	private static byte[] loadBytes(String resource) {
		InputStream stream = Simulator.class.getClassLoader().getResourceAsStream(resource);
		byte[] data = TypeAnnotationScanner.loadBytes(stream);
		return data;
	}

	/**
	 * Run some timings for lots of iterations (not very scientific...)
	 * @throws Exception
	 */
	private static void time(String resource, int iterations) throws Exception {
		System.out.println("Processing "+iterations+" iterations on resource "+resource);
		byte[] bytes = loadBytes(resource);
		long stime = System.currentTimeMillis();
		for (int i = 0; i < iterations; i++) {
			checkStreamASM(bytes);
		}
		long etime = System.currentTimeMillis();
		System.out.println("Time = " + (etime - stime) + "ms");
		stime = System.currentTimeMillis();
		for (int i = 0; i < iterations; i++) {
			checkStreamTAS(bytes);
		}
		etime = System.currentTimeMillis();
		System.out.println("Time = " + (etime - stime) + "ms with TypeAnnotationScanner");
	}
	
	// Pure ASM:    ClassCount=21286   trueCount=59 Time = 25745ms
	// Alternative: ClassCount=21286   trueCount=59 Time = 26008ms
	//              ClassCount=21286   trueCount=59 Time = 25667ms
	//              ClassCount=21286   trueCount=59 Time = 25014ms
	/**
	 * Go through all the jars on a classpath and load all the classes from each. This checks
	 * the parsing of a wide variety of class bytecode and also that results are the same as
	 * a regular ASM visitor trying to answer the same question.
	 */
	public static void loadLotsOfClasses() throws Exception {
		long classCount = 0;
		long trueCount = 0;
		String[] cp = getClasspath();
		long stime = System.currentTimeMillis();
		for (String element : cp) {
			if (element.endsWith(".jar")) {
				File f = new File(element);
				if (f.exists()) {
					JarFile jf = new JarFile(f);
					Enumeration<JarEntry> entries = jf.entries();
					while (entries.hasMoreElements()) {
						JarEntry je = entries.nextElement();
						if (je.getName().endsWith("class")) {
							classCount++;
							InputStream is = jf.getInputStream(je);
							byte[] bs = TypeAnnotationScanner.loadBytes(is);
							boolean b1 = checkStreamASM(bs);
							boolean b2 = checkStreamTAS(bs);
							if (b1 != b2) {
								System.out.println("Differing results for " + je.getName() + " b1=" + b1 + " b2=" + b2);
							}
							if (b2)
								trueCount++;
						}
					}
					jf.close();
				}
			}
		}
		long etime = System.currentTimeMillis();
		System.out.println("Classes loaded = #" + classCount);
		System.out.println("How many have FunctionalInterface = " + trueCount);
		System.out.println("Time taken = " + (etime - stime) + "ms");
	}

	private static boolean checkStreamASM(byte[] bs) throws Exception {
		ClassReader cr = new ClassReader(bs);
		cv.reset();
		cr.accept(cv, ClassReader.SKIP_CODE);
		return cv.retval;
	}

	private static boolean checkStreamTAS(byte[] bs) throws Exception {
		return TypeAnnotationScanner.scanClassBytesForAnnotation(bs, "Ljava/lang/FunctionalInterface;", true);
	}

	private static String[] getClasspath() {
		// Load a classpath that will have a bunch of stuff on
		return System.getProperty("sun.boot.class.path").split(":");
	}

	private static TypeAnnoVisitor cv = new TypeAnnoVisitor(Opcodes.ASM5);

	static class TypeAnnoVisitor extends ClassVisitor {

		String fi = "Ljava/lang/FunctionalInterface;";

		void reset() {
			retval = false;
		}

		boolean retval = false;

		public TypeAnnoVisitor(int api) {
			super(api);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			if (desc.equals(fi)) {
				retval = true;
			}
			return null;
		}

	}
}
