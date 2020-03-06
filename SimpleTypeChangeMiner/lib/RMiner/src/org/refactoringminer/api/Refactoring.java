package org.refactoringminer.api;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import gr.uom.java.xmi.LocationInfo;
import io.vavr.Tuple2;

public interface Refactoring extends Serializable, CodeRangeProvider {

	RefactoringType getRefactoringType();
	
	String getName();

	String toString();
	
	List<String> getInvolvedClassesBeforeRefactoring();
	
	List<String> getInvolvedClassesAfterRefactoring();
	
	default String toJSON() {
		StringBuilder sb = new StringBuilder();
		sb.append("{").append("\n");
		sb.append("\t").append("\"").append("type").append("\"").append(": ").append("\"").append(getName()).append("\"").append(",").append("\n");
		sb.append("\t").append("\"").append("description").append("\"").append(": ").append("\"").append(toString().replace('\t', ' ')).append("\"").append(",").append("\n");
		sb.append("\t").append("\"").append("leftSideLocations").append("\"").append(": ").append(leftSide()).append(",").append("\n");
		sb.append("\t").append("\"").append("rightSideLocations").append("\"").append(": ").append(rightSide()).append("\n");
		sb.append("}");
		return sb.toString();
	}


	default boolean isTypeChange() {return false;}



}