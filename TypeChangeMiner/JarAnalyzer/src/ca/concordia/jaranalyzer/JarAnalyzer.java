package ca.concordia.jaranalyzer;


import ca.concordia.jaranalyzer.Models.ClassInfo;
import ca.concordia.jaranalyzer.Models.JarInformation;
import ca.concordia.jaranalyzer.Models.PackageInfo;
import ca.concordia.jaranalyzer.util.Utility;
import io.vavr.Tuple;
import io.vavr.Tuple3;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.ofNullable;


public class JarAnalyzer {


    public TinkerGraph graph;


    public JarAnalyzer() {
        graph = TinkerGraph.open();
        graph.createIndex("Kind", Vertex.class);
    }

    public void toGraph(JarInformation j) {
        Vertex jar = graph.addVertex("Kind", "Jar", "ArtifactId", j.getArtifactId(), "Version", j.getVersion(), "GroupId", j.getGroupId());
        for (PackageInfo p : j.getPackages()) {
            Vertex pkg = graph.addVertex("Kind", "Package", "Name", p.getName());
            jar.addEdge("ContainsPkg", pkg);
            for (ClassInfo c : p.getClasses()) {

                Vertex cls = graph.addVertex("Kind", "Class", "isAbstract", c.isAbstract(), "isInterface",
                        c.isInterface(), "isEnum", c.isEnum(), "Name", c.getName(), "Type", c.getType().toString(), "QName", c.getQualifiedName());
                pkg.addEdge("Contains", cls);

                if (!c.getSuperClassName().isEmpty())
                    cls.addEdge("extends", graph.addVertex("Kind", "SuperClass", "Name", c.getSuperClassName()));
                c.getSuperInterfaceNames().stream()
                        .forEach(e -> cls.addEdge("implements", graph.addVertex("Kind", "SuperInterface", "Name", e)));

                c.getMethods().stream().filter(x -> !x.isPrivate())
                        .forEach(m -> {
                            Vertex x = graph.addVertex("Kind", "Method", "Name", m.getName(), "isAbstract", m.isAbstract()
                                    , "isConstructor", m.isConstructor(), "isStatic", m.isStatic(), "ReturnType", m.getReturnType(), "ParamType", m.getParameterTypes());
                            cls.addEdge("Declares", x);
                        });

                c.getFields().stream().filter(x -> !x.isPrivate())
                        .forEach(f -> cls.addEdge("Declares", graph.addVertex("Kind", "Field", "Name", f.getName(), "ReturnType", f.getType().toString())));

            }
        }
    }

    public static Map<String, Tuple3<List<String>, List<String>, Boolean>> getHierarchyCompositionMap(JarInformation j) {

        return j.getPackages().stream().flatMap(x -> x.getClasses().stream())
                .collect(toMap(ClassInfo::getQualifiedName
                        , x -> Tuple.of(
                                concat(ofNullable(x.getSuperClassName()), x.getSuperInterfaceNames().stream()).collect(toList())
                                , x.getFields().stream().map(f -> f.getType().toString()).collect(toList())
                                , x.isEnum())));
    }


    private JarInformation getJarInfo(String groupId, String artifactId, String version) {
        JarInformation jarInformation;
        String url = "http://central.maven.org/maven2/" + groupId + "/" + artifactId + "/" + version + "/" + artifactId
                + "-" + version + ".jar";
        jarInformation = getAsJarInformation(url, groupId, artifactId, version);

        if (jarInformation == null) {
            url = "http://central.maven.org/maven2/org/" + groupId + "/" + artifactId + "/" + version + "/" + artifactId
                    + "-" + version + ".jar";
            jarInformation = getAsJarInformation(url, groupId, artifactId, version);
        }

        if (jarInformation == null) {
            url = "http://central.maven.org/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version
                    + "/" + artifactId + "-" + version + ".jar";
            jarInformation = getAsJarInformation(url, groupId, artifactId, version);
        }
        return jarInformation;
    }


    public JarInformation getAsJarInformation(String url, String groupId, String artifactId, String version) {
        JarFile jarFile = DownloadJar(url);
        return getAsJarInformation(jarFile, groupId, artifactId, version);
    }

    public JarFile DownloadJar(String jarUrl) {
        String jarName = Utility.getJarName(jarUrl);
        String jarLocation = "";// jarsPath.toString() + '/' + jarName;
        JarFile jarFile = null;
        File file = new File(jarLocation);
        if (file.exists()) {
            try {
                return new JarFile(new File(jarLocation));
            } catch (IOException e) {
                // System.out.println("Cannot open jar: " + jarLocation);
            }
        }
        try {
            Utility.downloadUsingStream(jarUrl, jarLocation);
        } catch (IOException e) {
            // System.out.println("Could not download jar: " + jarUrl);
        }

        try {
            jarFile = new JarFile(new File(jarLocation));
        } catch (IOException e) {
            // System.out.println("Cannot open jar: " + jarLocation);
        }
        return jarFile;
    }

    public JarInformation getAsJarInformation(JarFile jarFile, String groupId, String artifactId, String version) {
        if (jarFile == null)
            return null;

        JarInformation jarInformation = new JarInformation(jarFile, groupId, artifactId, version);
        return jarInformation;
    }


    public void jarToGraph(JarFile jarFile, String groupId, String artifactId, String version) {
        JarInformation ji = new JarInformation(jarFile, groupId, artifactId, version);
        toGraph(ji);
    }
}
