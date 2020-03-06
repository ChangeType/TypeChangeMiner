package ca.concordia.jaranalyzer;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

public class JHotDrawTests {
	private static final String PROJECT_LOCATION = "C:\\Users\\tsantalis\\runtime-EclipseApplication\\JHotDraw5.2";
	private static APIFinder apiFinder;

	@BeforeClass
	public static void oneTimeSetUp() {
		apiFinder = new APIFinderImpl(PROJECT_LOCATION);
	}
	@Test
	public void findMethodInGrandSuperclassOfImport() {
		List<String> imports = Arrays.asList(new String[] {
				//"java.lang", 
				"CH.ifa.draw.contrib", 
				//"java.awt", "java.awt.event.MouseEvent", 
				//"java.util", "java.io.IOException", 
				"CH.ifa.draw.framework", "CH.ifa.draw.util",
				"CH.ifa.draw.standard"
				});
		Set<MethodInfo> matches = apiFinder.findAllMethods(imports, "size", 0);
		assertEquals("[public java.awt.Dimension size(), public java.awt.Dimension size(), public abstract java.awt.Dimension size(), public static int size()]", matches.toString());
		System.out.println(matches);
	}
}
