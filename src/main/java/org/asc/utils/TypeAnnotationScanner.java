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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Scans a class file for a particular annotation at the type level. It unpacks the minimum it can get away
 * with to discover the annotations. Being passed in the annotation up front allows it to make decisions that
 * something is/isnt a match very early, even as the UTF8 strings are being unpacked character by character
 * from the constant pool entries.
 * 
 * @author Andy Clement
 */
public class TypeAnnotationScanner {

	// Types of thing that can be found in the Constant Pool
	private final static byte CONSTANT_Utf8 = 1;
	private final static byte CONSTANT_Integer = 3;
	private final static byte CONSTANT_Float = 4;
	private final static byte CONSTANT_Long = 5;
	private final static byte CONSTANT_Double = 6;
	private final static byte CONSTANT_Class = 7;
	private final static byte CONSTANT_String = 8;
	private final static byte CONSTANT_Fieldref = 9;
	private final static byte CONSTANT_Methodref = 10;
	private final static byte CONSTANT_InterfaceMethodref = 11;
	private final static byte CONSTANT_NameAndType = 12;
	private final static byte CONSTANT_MethodHandle = 15;
	private final static byte CONSTANT_MethodType = 16;
	private final static byte CONSTANT_InvokeDynamic = 18;

	private int[] constantPool;
	private byte[] bytes;
	private int ptr;

	private TypeAnnotationScanner(byte[] bytes) {
		this.bytes = bytes;
		this.ptr = 0;		
	}
	
	/**
	 * Scan the class stored in the specified bytes for the specified annotation type (name is of the form 
	 * <tt>Ljava/lang/Foo;</tt>. <b>Note:</b> There is a current limitation that the type name should not
	 * include characters that cannot be encoded in single chars.
	 * 
	 * @param classfilebytes the bytecode for the class
	 * @param annotationType the type name of the annotation (form: <tt>Lorg/example/Foo;</tt>
	 * @param hasRuntimeRetention true if the supplied annotation has runtime retention
	 * @return true if the annotation is found as a type level annotation on the supplied class
	 */
	public static boolean scanClassBytesForAnnotation(byte[] classfilebytes,String annotationType, boolean hasRuntimeRetention) {
		return new TypeAnnotationScanner(classfilebytes).consumeClass(annotationType,hasRuntimeRetention);
	}
	
	/**
	 * Scan the class stored in the specified bytes for the specified annotation type.  <b>Note:</b> There is a 
	 * current limitation that the annotation type name should not include characters that cannot be encoded 
	 * in single chars. <b>Note:</b> This version is slower than the alternate <tt>scanClassBytesForAnnotation</tt> if
	 * calling it a lot, due to the string construction.
	 * 
	 * @param classfilebytes the bytecode for the class
	 * @param annotationType the type of the annotation to be searched for
	 * @return true if the annotation is found as a type level annotation on the supplied class
	 */
	public static boolean scanClassBytesForAnnotation(byte[] classfilebytes, Class<?> annotationClass) {
		boolean visible = (annotationClass.getAnnotation(Retention.class).value() == RetentionPolicy.RUNTIME);
		String annotationTypeName = new StringBuilder("L").append(annotationClass.getName().replace(".","/")).append(";").toString();
		return new TypeAnnotationScanner(classfilebytes).consumeClass(annotationTypeName, visible);
	}
	
	/**
	 * Quick (crude) load of a byte array from the input stream. A helper method for caller that have the stream
	 * but not the byte array.
	 * @param stream the input stream containing the bytecode
	 * @return a byte array containing the bytecode
	 */
	public static byte[] loadBytes(InputStream stream) {
		try {
			BufferedInputStream bis = new BufferedInputStream(stream);
			byte[] theData = new byte[1000000];
			int dataReadSoFar = 0;
			byte[] buffer = new byte[1024];
			int read = 0;
			while ((read = bis.read(buffer)) != -1) {
				System.arraycopy(buffer, 0, theData, dataReadSoFar, read);
				dataReadSoFar += read;
			}
			bis.close();
			// Resize to actual data read
			byte[] returnData = new byte[dataReadSoFar];
			System.arraycopy(theData, 0, returnData, 0, dataReadSoFar);
			return returnData;
		} catch (IOException e) {
			throw new RuntimeException("Problem loading bytes from input stream",e);
		}
	}

	/**
	 * @return an int constructed from the next four bytes to be processed
	 */
	private final int readInt() {
		return ((bytes[ptr++] & 0xFF) << 24) + ((bytes[ptr++] & 0xFF) << 16) + ((bytes[ptr++] & 0xFF) << 8)
				+ (bytes[ptr++] & 0xFF);
	}

	/**
	 * @return an unsigned short constructed from the next two bytes to be processed
	 */
	private final int readUnsignedShort() {
		return ((bytes[ptr++] & 0xff) << 8) + (bytes[ptr++] & 0xff);
	}

	/**
	 * @param offset the offset into the byte array where a short should be loaded from
	 * @return an unsigned short constructed from two bytes to be found at the specified offset
	 */
	private final int readUnsignedShort(int offset) {
		return ((bytes[offset++] & 0xff) << 8) + (bytes[offset] & 0xff);
	}

	/**
	 * Parse a class from the byte array, only touching what is necessary to locate the type annotations and
	 * check them against the annotation/retention supplied.
	 * @param annotationType the type name of the annotation (of the form <tt>Lorg/example/Foo;</tt>)
	 * @param hasRuntimeRetention whether the annotation specified has runtime retention
	 * @return true if the annotation is found at the type level
	 */
	private final boolean consumeClass(String annotationType, boolean hasRuntimeRetention) {
		// ClassFile {
		//  u4 magic;
		//  u2 minor_version;
		//  u2 major_version;
		//  u2 constant_pool_count;
		//  cp_info constant_pool[constant_pool_count-1];
		//  u2 access_flags;
		//  u2 this_class;
		//  u2 super_class;
		//  u2 interfaces_count;
		//  u2 interfaces[interfaces_count];
		//  u2 fields_count;
		//  field_info fields[fields_count];
		//  u2 methods_count;
		//  method_info methods[methods_count];
		//  u2 attributes_count;
		//  attribute_info attributes[attributes_count];
		// }
		ptr += 8; // jump magic:4, minor:2, major:2
		consumeConstantPool();
		ptr += 6; // jump access_flags:2, this_class:2, super_class:2
		int interfacesCount = readUnsignedShort();
		ptr += 2 * interfacesCount;
		consumeFields();
		consumeMethods();
		int num_attributes = readUnsignedShort();
		if (num_attributes == 0) {
			return false;
		}
		for (int a = 0; a < num_attributes; a++) {
			int nameIndex = readUnsignedShort();
			if (hasRuntimeRetention && readUTF8(nameIndex, "RuntimeVisibleAnnotations") || 
			    !hasRuntimeRetention && readUTF8(nameIndex,"RuntimeInvisibleAnnotations")) {
				if (consumeRuntimeAnnotation(annotationType)) {
					return true;
				}
				return false;
			} else {
				int bs = readInt();
				ptr += bs; // skip rest of attribute
			}
		}
		return false;
	}

	/**
	 * Consume a runtime annotation attribute (RuntimeVisibleAnnotations or RuntimeInvisibleAnnotations) 
	 * from the bytecode. Runtime(In)visibleAnnotations includes the type level annotations so is where the code should
	 * be looking for the annotation type that was passed in.
	 * 
	 * @param annotationType the annotation type being looked for
	 * @return true if this annotation includes the specified annotation type.
	 */
	private boolean consumeRuntimeAnnotation(String annotationType) {
		// RuntimeVisibleAnnotations_attribute {
		//  u2 attribute_name_index;
		//  u4 attribute_length;
		//  u2 num_annotations;
		//  annotation annotations[num_annotations];
		// }
		// NOTE: Name already consumed
		ptr += 4;
		int num_annotations = readUnsignedShort();
		for (int a = 0; a < num_annotations; a++) {
			if (consumeAnnotation(annotationType)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Consume an individual annotation. If this annotation does represent the one being searched for then
	 * this method returns immediately. If it does not then we jump over the rest of the bytes to look at
	 * any later annotations.
	 * @param annotationType the annotation type being searched for
	 * @return true if this annotation represents the annotation being looked for
	 */
	private boolean consumeAnnotation(String annotationType) {
		// annotation {
		//   u2 type_index;
		//   u2 num_element_value_pairs;
		//   { u2 element_name_index;
		//     element_value value;
		//   } element_value_pairs[num_element_value_pairs];
		// }
		if (readUTF8(readUnsignedShort(), annotationType)) {
			return true;
		}
		int num_element_value_pairs = readUnsignedShort();
		for (int p = 0; p < num_element_value_pairs; p++) {
			ptr += 2;
			consumeElementValue();
		}
		return false;
	}

	/**
	 * Consume an element value packed into an annotation. 
	 */
	private void consumeElementValue() {
		// element_value {
		//  u1 tag;
		//  union {
		//   u2 const_value_index; // sBCDFIJSZ
		//
		//   { u2 type_name_index; // e
		//     u2 const_name_index;
		//   } enum_const_value;
		//
		//   u2 class_info_index; // c
		//
		//   annotation annotation_value; // @
		//
		//   { u2 num_values;
		//     element_value values[num_values];
		//   } array_value; // [
		//  } value;
		// }
		byte type = bytes[ptr++];
		switch (type) {
		case 's':// String
		case 'c':// Class
		case 'B':
		case 'C':
		case 'D':
		case 'F':
		case 'I':
		case 'J':
		case 'S':
		case 'Z':
			ptr += 2;
			break;
		case 'e':// Enum
			ptr += 4;
			break;
		case '@':// AnnotationType
			consumeAnnotation("");
			break;
		case '[':// Array
			int num_values = readUnsignedShort();
			for (int v = 0; v < num_values; v++) {
				consumeElementValue();
			}
			break;
		}
	}


	/**
	 * Consume all the fields as quickly as possible.
	 */
	private void consumeFields() {
		// field_info {
		//  u2 access_flags;
		//  u2 name_index;
		//  u2 descriptor_index;
		//  u2 attributes_count;
		//  attribute_info attributes[attributes_count];
		// }
		int fcount = readUnsignedShort();
		for (int f = 0; f < fcount; f++) {
			ptr += 6;
			consumeAttributeInfos();
		}
	}

	/**
	 * Consume all the methods as quickly as possible.
	 */
	private void consumeMethods() {
		// method_info {
		//  u2 access_flags;
		//  u2 name_index;
		//  u2 descriptor_index;
		//  u2 attributes_count;
		//  attribute_info attributes[attributes_count];
		// }
		int mcount = readUnsignedShort();
		for (int m = 0; m < mcount; m++) {
			ptr += 6;
			consumeAttributeInfos();
		}
	}

	/**
	 * Consume attributes as quickly as possible.
	 */
	private void consumeAttributeInfos() {
		// attribute_info {
		//  u2 attribute_name_index;
		//  u4 attribute_length;
		//  u1 info[attribute_length];
		// }
		int acount = readUnsignedShort();
		for (int a = 0; a < acount; a++) {
			ptr += 2;
			int attribute_length = readInt();
			ptr += attribute_length;
		}
	}

	/**
	 * Rapidly process the constant pool, the only thing to hold onto is the starting position
	 * within the byte array of any Utf8 entries. With the starting position known, it can be unpacked
	 * later on demand.
	 */
	private void consumeConstantPool() {
		int i = 1;
		int constantPoolSize = readUnsignedShort();
		constantPool = new int[constantPoolSize];
		while (i < constantPoolSize) {
			byte b = (byte) bytes[ptr++];
			switch (b) {
			case CONSTANT_Utf8: // Utf8_info { u1 tag; u2 length; u1 bytes[length]; }
				constantPool[i] = ptr;
				int utf8len = readUnsignedShort();
				ptr += utf8len;
				break;
			case CONSTANT_Class:      // Class_info { u1 tag; u2 name_index; }
			case CONSTANT_String:     // String_info { u1 tag; u2 string_index; }
			case CONSTANT_MethodType: // MethodType_info { u1 tag; u2 descriptor_index; }
				ptr += 2;
				break;
			case CONSTANT_Integer:       // Integer_info { u1 tag; u4 bytes; }
			case CONSTANT_Float:         // Float_info { u1 tag; u4 bytes; }
			case CONSTANT_Fieldref:      // Fieldref_info { u1 tag; u2 class_index; u2 name_and_type_index; }
			case CONSTANT_Methodref:     // Methodref_info { u1 tag; u2 class_index; u2 name_and_type_index; }
			case CONSTANT_InterfaceMethodref: // InterfaceMethodref_info { u1 tag; u2 class_index; u2 name_and_type_index; }
			case CONSTANT_NameAndType:   // NameAndType_info { u1 tag; u2 name_index; u2 descriptor_index; }
			case CONSTANT_InvokeDynamic: // InvokeDynamic_info { u1 tag; u2 bootstrap_method_attr_index; u2 name_and_type_index; }
				ptr += 4;
				break;
			case CONSTANT_Long:   // Long_info { u1 tag; u4 high_bytes; u4 low_bytes; }
			case CONSTANT_Double: // Double_info { u1 tag; u4 high_bytes; u4 low_bytes; }
				ptr += 8;
				i++; // double size
				break;
			case CONSTANT_MethodHandle: // MethodHandle_info { u1 tag; u1 reference_kind; u2 reference_index; }
				ptr += 3;
				break;
			default:
				throw new IllegalStateException("???: " + b);
			}
			i++;
		}
	}
	
	/**
	 * Read a UTF8 at the specified constant pool index. Since the value the code is looking for is also passed in
	 * this code can terminate very early if the characters being unpacked from UTF8 encoding can't possibly match.
	 *
	 * @param idx the constant pool index
	 * @param desiredValue the value trying to be found
	 * @return true if the UTF8 parsed string matches the desired value
	 */
	public final boolean readUTF8(int idx, String desiredValue) {
		int p = constantPool[idx];
		int utflen = readUnsignedShort(p);
		int desiredValueLen = desiredValue.length();
		if (utflen != desiredValueLen) {
			// only a valid check if *not* using 'real' unicode chars in desiredValue
			return false;
		}
		p += 2;
		int stop = p+utflen;
		int c;
		int count = 0;
		byte[] varbytes = bytes;
		while (p < stop) {
			c = (int) varbytes[p] & 0xff;
			if (c > 127)
				break;
			if (count < desiredValueLen && c != desiredValue.charAt(count)) {
				return false;
			}
			count++;
			p++;
		}
		// TODO condition not valid if looking for a more complex (utf chars in it) desiredValue
		if (count == desiredValueLen) {
			return true;
		}
		
		// This block just jumps through more complex UTF8 encoded chars. If desiredValue includes
		// characters that don't encode in 1 byte then this code really ought to be unpacking the
		// more complex chars and comparing them to each desiredValue.charAt(...)
		while (p < stop) {
			c = (int) varbytes[p] & 0xff;
			switch (c & 0xf0) {
			case 0x00: case 0x10: case 0x20: case 0x30: case 0x40: case 0x50: case 0x60: case 0x70: // 0xxx xxxx
				p++;
				break;
			case 0xc0: case 0xd0: // 110x xxxx   10xx xxxx
				p += 2;
				break;
			case 0xe0: // 1110 xxxx   10xx xxxx   10xx xxxx
				p += 3;
				break;
			default: // 10xx xxxx   1111 xxxx which are 0x80 and 0xf0
				throw new RuntimeException("unexpected byte in UTF8 encoded byte stream at position " + (p-constantPool[idx]+2));
			}
		}
		return false;
	}
}