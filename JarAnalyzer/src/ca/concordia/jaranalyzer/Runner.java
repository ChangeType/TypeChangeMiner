package ca.concordia.jaranalyzer;



import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;
import org.apache.tinkerpop.gremlin.process.traversal.IO;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;


public class Runner {
    public static void main(String args[]) throws Exception {
        if(!Files.exists(Paths.get("D:\\MyProjects\\apache-tinkerpop-gremlin-server-3.4.3\\data\\JavaJars.kryo"))){
            APIFinderImpl apiF = new APIFinderImpl(new JarAnalyzer(),"C:\\Program Files\\Java\\jre1.8.0_171","8");
        }
        final TinkerGraph newGraph = TinkerGraph.open();
        GraphTraversalSource gr = traversal().withRemote(DriverRemoteConnection.using("localhost",8182,"g"));
//                newGraph.traversal().io("D:\\MyProjects\\apache-tinkerpop-gremlin-server-3.4.3\\data\\JavaJars.kryo")
//                .with(IO.reader, IO.gryo)
//                .read().iterate();

        //   System.out.println(gr.V().count().next());
        //   System.out.println(gr.V().has("Kind","Class").count());


        APIFinderImpl apiF = new APIFinderImpl();
//        Map<String, List<String>> map = apiF.getSuperTypes(gr);
        Set<String> classesInJdk = apiF.getAllClasses(gr);
        List<String> xx = classesInJdk.stream().filter(x -> x.contains("HttpServletRequest"))
                .collect(Collectors.toList());
        System.out.println(classesInJdk.size());

//        Map<Boolean, List<String>> m = classesInJDK.stream().collect(groupingBy(x -> x.startsWith("java.lang")));

//        Files.write(Paths.get("D:/MyProjects/JavaLangClasses.txt"), m.get(true), StandardOpenOption.CREATE_NEW);
//        Files.write(Paths.get("D:/MyProjects/JavaClasses.txt"), m.get(false), StandardOpenOption.CREATE_NEW);

        System.out.println(apiF.findTypeInJars(Arrays.asList("javax.servlet.http"),"HttpServletRequest", gr));
        System.out.println(apiF.findTypeInJars(Arrays.asList("java.util.concurrent.atomic"),"LongAdder", newGraph.traversal()));
        newGraph.clear();
        System.out.println();
    }
}

