import com.t2r.common.models.ast.TypeGraphOuterClass.TypeGraph;
import com.t2r.common.models.ast.TypeNodeOuterClass;
import com.t2r.common.models.refactorings.ProjectOuterClass.Project;
import com.t2r.common.models.refactorings.TypeChangeCommitOuterClass.TypeChangeCommit;
import com.t2r.common.utilities.GitUtil;
import gr.uom.java.xmi.TypeFactMiner.Visitors;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jgit.api.Git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.*;

import static com.t2r.common.utilities.PrettyPrinter.pretty;
import static java.util.stream.Collectors.*;

public class MineTypeMigration {


    public static void main(String a[]) throws IOException {
        List<Project> projects = new ArrayList<>(Runner.readWriteInputProtos.
                readAll("Projects", "Project"));

//        projects = projects.subList();


        List<Tuple2<Project, List<TypeChangeCommit>>> project_tcc = projects.parallelStream()
                .map(z -> Tuple.of(z, Runner.readWriteOutputProtos.<TypeChangeCommit>readAll("TypeChangeCommit_" + z.getName(), "TypeChangeCommit")))
//            .map(x -> x.map2(l -> l.stream().filter(z -> z.getSha().equals("122cad6aec5839d8d515c5008425ecb34f2fa56b")).collect(toList())))
                .collect(toList());



        Map<String, Map<TypeGraph, Double>> map = new HashMap<>();

        List<Tuple2<String, Set<Set<Tuple2<TypeGraph, TypeGraph>>>>> tmp = projects.stream()
                .flatMap(p -> Runner.readWriteOutputProtos.<TypeChangeCommit>readAll("TypeChangeCommit_" + p.getName(), "TypeChangeCommit")
                        .stream()
                        .flatMap(x -> x.getMigrationAnalysisList().stream()
                                .filter(m -> m.getTypeMigrationLevel().contains("Project")).map(t -> t.getType())
                                .map(z -> Tuple.of(z, x.getTypeChangesList().stream().filter(y -> pretty(y.getB4()).contains(z)).map(h -> Tuple.of(h.getB4(), h.getAftr())).collect(toSet()))))
                        .distinct()
                )
                .collect(groupingBy(x -> x._1(), collectingAndThen(toList(), l -> l.stream().map(x -> x._2()).collect(toSet()))))
                .entrySet().stream().sorted(Comparator.comparingInt(x -> x.getValue().size()))
                .map(Tuple::fromEntry)
                .collect(toList());

        Collections.reverse(tmp);


        for (var prj : projects) {

            Map<TypeGraph, Double> migrationRatio = new HashMap<>();
            List<TypeChangeCommit> tcc = Runner.readWriteOutputProtos
                    .readAll("TypeChangeCommit_" + prj.getName(), "TypeChangeCommit");

            Set<String> detectedMigrations = tcc.stream()
                    .flatMap(x -> x.getMigrationAnalysisList().stream()
                            .map(m -> Tuple.of(m.getType(), m.getTypeMigrationLevel())))
                    .filter(x -> x._2().contains("Project"))
                    .map(x -> x._1())
                    .collect(toSet());

            var tc_commit = tcc.stream().flatMap(tc -> tc.getTypeChangesList().stream()
                    .map(tca -> Tuple.of(Tuple.of(tca.getB4(), tca.getAftr())
                            , Tuple.of(tc.getSha(), tca.getTypeChangeInstancesCount()))))
                    .filter(x -> !migrationRatio.containsKey(x._1()))
                    .collect(groupingBy(x -> x._1()._1(), collectingAndThen(toList(), l -> l.stream().map(Tuple2::_2).collect(toList()))));


            for(var tc : tc_commit.entrySet()){
                if(detectedMigrations.stream().anyMatch(z -> pretty(tc.getKey()).equals(z))){
                    migrationRatio.put((tc.getKey()), 1.00);
                }else{
                    Try<Git> g = GitUtil.tryToClone("", Runner.projectPath.apply(prj.getName()).toAbsolutePath());
                    var commits = tc_commit.get(tc.getKey());
                    Tuple2<String, Integer> commitToAnalyze;
                    if(commits.size() > 1){
                        commitToAnalyze = commits.get(0);
                    }else{
                        commitToAnalyze = Tuple.of(GitUtil.getLastCommitFrom(g.get().getRepository(), commits.stream().map(x->x._1()).collect(toList()))
                                , commits.stream().mapToInt(x->x._2()).sum());
                    }

                    Set<CompilationUnit> relevantCus = GitUtil.populateCu(g.get().getRepository(), commitToAnalyze._1(), cu -> {

                        if(tc.getKey().getRoot().getKind().equals(TypeNodeOuterClass.TypeNode.TypeKind.Primitive)
                                || pretty(tc.getKey()).startsWith("java.lang"))
                            return true;

                        boolean packageMatch = cu.getPackage() != null && pretty(tc.getKey()).contains(cu.getPackage().toString());
                        if (packageMatch)
                            return packageMatch;
                        List<ImportDeclaration> imports = cu.imports();

                        boolean importMatch = imports.stream().map(x -> x.getName().getFullyQualifiedName())
                                .anyMatch(i -> pretty(tc.getKey()).contains(i));
                        return importMatch;
                    });
                    int typeUSageCounter = 0;
                    for (var cu : relevantCus) {
                        List<AbstractTypeDeclaration> typesDeclared = cu.types();
                        Visitors.UsedTypesCounter ut = new Visitors.UsedTypesCounter(tc.getKey());
                        for (var td : typesDeclared) {
                            td.accept(ut);
                            typeUSageCounter += ut.counter;
                        }
                    }

                    if(typeUSageCounter > 0) {
                        System.out.println(pretty(tc.getKey()) + " " + (double) commitToAnalyze._2() / (commitToAnalyze._2() + typeUSageCounter));
                        migrationRatio.put(tc.getKey(), (double) commitToAnalyze._2() / (commitToAnalyze._2() + typeUSageCounter));
//                        System.out.println();
                    }

                }

            }

            Path migrationCsv = Paths.get("/Users/ameya/Research/TypeChangeStudy/migrations.csv");
            DecimalFormat df = new DecimalFormat("#.##");
            var str = migrationRatio.entrySet().stream().map(x->df.format(x.getValue().doubleValue())).collect(joining("\n"));
            if(Files.exists(migrationCsv)){
                Files.write(migrationCsv, str.getBytes(), StandardOpenOption.APPEND);
            }else{
                Files.write(migrationCsv, str.getBytes(), StandardOpenOption.CREATE_NEW);
            }
            map.put(prj.getName(),migrationRatio);

        }
        System.out.println();



        map.forEach((k,v) -> v.entrySet().forEach(e -> System.out.println(k + ", " + pretty(e.getKey()) + ", " + e.getValue())));


    }


}
