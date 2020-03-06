package ca.concordia.jaranalyzer;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

public class GitUtilTest {

	private static final String PROJECT_LOCATION = "A:\\Ref-Finder Experiment Projects\\jfreechart\\jfreechart full repo";
	private static APIFinder apiFinder;

	@BeforeClass
	public static void oneTimeSetUp() {
		apiFinder = new APIFinderImpl(PROJECT_LOCATION);
	}

	@Test
	public void findMethod() {
		List<String> imports = Arrays.asList(new String[] {
				"java.lang",
				"java.awt.GradientPaint",
				"java.awt.Paint",
				"java.awt.Stroke",
				"org.jfree.chart.util"
				});
		
		Set<MethodInfo> matches = apiFinder.findAllMethods(imports, "calculateOffsetX", 0);
		assertEquals("[public int calculateOffsetX(), public abstract int calculateOffsetX()]", matches.toString());
		System.out.println(matches);
	}
}
