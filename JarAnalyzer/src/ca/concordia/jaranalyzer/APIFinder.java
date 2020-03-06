package ca.concordia.jaranalyzer;


import ca.concordia.jaranalyzer.Models.ClassInfo;
import ca.concordia.jaranalyzer.Models.FieldInfo;
import ca.concordia.jaranalyzer.Models.MethodInfo;

import java.util.List;
import java.util.Set;

public interface APIFinder {
	Set<MethodInfo> findAllMethods (List<String> imports, String methodName, int numberOfParameters);
	Set<ClassInfo> findAllTypes (List<String> imports, String typeName) throws Exception;
	Set<FieldInfo> findAllFields (List<String> imports, String fieldName);
}
