package ca.concordia.jaranalyzer;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

public class JFreeChartTests {

	private static final String PROJECT_LOCATION = "C:\\Users\\tsantalis\\runtime-EclipseApplication\\jfreechart-1.0.13";
	private static APIFinder apiFinder;

	@BeforeClass
	public static void oneTimeSetUp() {
		apiFinder = new APIFinderImpl(PROJECT_LOCATION);
	}

	@Test
	public void findMethodInSuperclassOfImport() {
		
		List<String> imports = Arrays.asList(new String[] {
				"java.lang",
				"org.jfree.chart.needle", 
				"java.awt.Graphics2D",
				"java.awt.geom.GeneralPath", "java.awt.geom.Point2D",
				"java.awt.geom.Rectangle2D", "java.io.Serializable"
				});
		Set<MethodInfo> matches = apiFinder.findAllMethods(imports, "getMinX", 0);
		assertEquals("[public double getMinX()]", matches.toString());
		// List<FieldInfo> Fieldmatches = mf.findAllFields(imports,
		// "DEFAULT_HORIZONTAL_ALIGNMENT");
		System.out.println(matches);
	}

	@Test
	public void findMethodInSuperclassOfImportLocatedInAnotherJar() {
		List<String> imports = Arrays.asList(new String[] {
				"java.lang",
				"org.jfree.chart.demo",
				"java.awt.Color", "java.awt.Dimension",
				"java.awt.GradientPaint",
				"org.jfree.chart.ChartFactory",
				"org.jfree.chart.ChartPanel",
				"org.jfree.chart.JFreeChart",
				"org.jfree.chart.axis.CategoryAxis",
				"org.jfree.chart.axis.CategoryLabelPositions",
				"org.jfree.chart.axis.NumberAxis",
				"org.jfree.chart.plot.CategoryPlot",
				"org.jfree.chart.plot.PlotOrientation",
				"org.jfree.chart.renderer.category.BarRenderer",
				"org.jfree.data.category.CategoryDataset",
				"org.jfree.data.category.DefaultCategoryDataset",
				"org.jfree.ui.ApplicationFrame",
				"org.jfree.ui.RefineryUtilities"
				});
		Set<MethodInfo> matches = apiFinder.findAllMethods(imports, "setPreferredSize", 1);
		assertEquals("[public void setPreferredSize(java.awt.Dimension), public void setPreferredSize(java.awt.Dimension)]", matches.toString());
		System.out.println(matches);
	}

	@Test
	public void findMethodInNestedType() {
		List<String> imports = Arrays.asList(new String[] {
				"java.lang", 
				"org.jfree.chart.axis",
				"java.io.Serializable",
				"java.util.ArrayList",
				"java.util.Calendar",
				"java.util.Collections",
				"java.util.Date",
				"java.util.GregorianCalendar",
				"java.util.Iterator",
				"java.util.List",
				"java.util.Locale",
				"java.util.SimpleTimeZone",
				"java.util.TimeZone"
				});
		Set<MethodInfo> matches = apiFinder.findAllMethods(imports, "Segment", 1);
		assertEquals("[protected void SegmentedTimeline$Segment(long)]", matches.toString());
		System.out.println(matches);
	}

	@Test
	public void findClassConstructorWithQualifiedName() {
		List<String> imports = Arrays.asList(new String[] {
				"java.lang",
				});
		Set<MethodInfo> matches = apiFinder.findAllMethods(imports, "java.util.ArrayList", 0);
		assertEquals("[public void ArrayList()]", matches.toString());
		System.out.println(matches);
	}

	@Test
	public void findTypeWithQualifiedName() {
		List<String> imports = Arrays.asList(new String[] {
				"java.lang",
				});
		Set<ClassInfo> matches = apiFinder.findAllTypes(imports, "java.net.URL");
		assertEquals("[public class java.net.URL]", matches.toString());
		System.out.println(matches);
	}

	@Test
	public void findInnerClassConstructorWithoutQualifiedName() {
		List<String> imports = Arrays.asList(new String[] {
				"java.lang", "org.jfree.chart.axis"
				});
		Set<MethodInfo> matches = apiFinder.findAllMethods(imports, "BaseTimelineSegmentRange", 2);
		assertEquals("[public void SegmentedTimeline$BaseTimelineSegmentRange(long, long)]", matches.toString());
		System.out.println(matches);
	}

	@Test
	public void findInnerClassConstructorWithOuterClassConcatenated() {
		List<String> imports = Arrays.asList(new String[] {
				"java.lang",
				"org.jfree.data.time", "java.util.Calendar", "java.util.TimeZone",
				"org.jfree.data.DomainInfo", "org.jfree.data.Range", "org.jfree.data.RangeInfo",
				"org.jfree.data.general.SeriesChangeEvent", "org.jfree.data.xy.AbstractIntervalXYDataset", 
				"org.jfree.data.xy.IntervalXYDataset"
				});
		Set<MethodInfo> matches = apiFinder.findAllMethods(imports, "DynamicTimeSeriesCollection.ValueSequence", 1);
		assertEquals("[public void DynamicTimeSeriesCollection$ValueSequence(int)]", matches.toString());
		System.out.println(matches);
	}

	@Test
	public void findMethod() {
		List<String> imports = Arrays.asList(new String[] {
				"java.lang", "org.jfree.chart.block", 
				"java.awt.Graphics2D", "java.awt.geom.Rectangle2D", 
				"java.io.Serializable", "java.util.List", 
				"org.jfree.ui.Size2D",
				"org.jfree.data.Range"
				// this is the return type of method
				// org.jfree.chart.block.RectangleConstraint.getHeightRange()
				// and is supplied externally
				});
		Set<MethodInfo> matches = apiFinder.findAllMethods(imports, "constrain", 1);
		assertEquals("[public double constrain(double)]", matches.toString());
		System.out.println(matches);
	}

	@Test
	public void findMethodInSuperInterfaceOfImport() {
		List<String> imports = Arrays.asList(new String[] {
				"java.lang", 
				"org.jfree.chart.renderer.category",
				"java.awt.Graphics2D", 
				"java.awt.Paint", 
				"java.awt.Shape",
				"java.awt.Stroke", 
				"java.awt.geom.Line2D", 
				"java.awt.geom.Rectangle2D",
				"java.io.IOException", 
				"java.io.ObjectInputStream", 
				"java.io.ObjectOutputStream",
				"java.io.Serializable", 
				"java.util.List",
				"org.jfree.chart.LegendItem",
				"org.jfree.chart.axis.CategoryAxis",
				"org.jfree.chart.axis.ValueAxis",
				"org.jfree.chart.event.RendererChangeEvent",
				"org.jfree.chart.plot.CategoryPlot",
				"org.jfree.chart.plot.PlotOrientation",
				"org.jfree.data.category.CategoryDataset",
				"org.jfree.data.statistics.MultiValueCategoryDataset",
				"org.jfree.util.BooleanList",
				"org.jfree.util.BooleanUtilities",
				"org.jfree.util.ObjectUtilities", 
				"org.jfree.util.PublicCloneable",
				"org.jfree.util.ShapeUtilities"
				});
		Set<MethodInfo> matches = apiFinder.findAllMethods(imports, "getRowKey", 1);
		assertEquals("[public abstract java.lang.Comparable getRowKey(int)]", matches.toString());
		System.out.println(matches);
	}

}
