import com.t2r.common.models.ast.TypeGraphOuterClass.TypeGraph;
import com.t2r.common.models.ast.TypeNodeOuterClass;
import com.t2r.common.models.refactorings.NameSpaceOuterClass;
import com.t2r.common.models.refactorings.ProjectOuterClass.Project;
import com.t2r.common.models.refactorings.TypeChangeCommitOuterClass.TypeChangeCommit;
import com.t2r.common.utilities.GitUtil;
import gr.uom.java.xmi.TypeFactMiner.Visitors;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple4;
import io.vavr.control.Try;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jgit.api.Git;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.t2r.common.utilities.GitUtil.populateCu;
import static com.t2r.common.utilities.PrettyPrinter.pretty;
import static java.util.stream.Collectors.*;

public class MineTypeMigrationAgain {


    public static void main(String a[]) throws IOException {

        Runner.readWriteInputProtos.<Project>readAll("Projects", "Project")
                .parallelStream().forEach(prj -> {

            Map<TypeGraph, Double> migrationRatio = new HashMap<>();
            List<TypeChangeCommit> tcc = Runner.readWriteOutputProtos
                    .readAll("TypeChangeCommit_" + prj.getName(), "TypeChangeCommit");

            Map<TypeGraph, List<Tuple4<String, Integer, TypeGraph, NameSpaceOuterClass.NameSpace>>> tc_commit = tcc.stream().flatMap(tc -> tc.getTypeChangesList().stream()
                    .map(tca -> Tuple.of(Tuple.of(tca.getB4(), tca.getAftr())
                            , Tuple.of(tc.getSha(), tca.getTypeChangeInstancesCount(), tca.getAftr(), tca.getNameSpacesB4()))))
                    .filter(x -> !migrationRatio.containsKey(x._1()))
                    .collect(groupingBy(x -> x._1()._1(), collectingAndThen(toList(), l -> l.stream().map(Tuple2::_2).collect(toList()))));

            System.out.println("-------------" + prj.getName() + "--------------");
            for (var tc : tc_commit.entrySet()) {

                Try<Git> g = GitUtil.tryToClone("", Runner.projectPath.apply(prj.getName()).toAbsolutePath());
                Tuple4<String, Integer, TypeGraph, NameSpaceOuterClass.NameSpace> commitToAnalyze;
                if (tc.getValue().size() > 1) {
                    commitToAnalyze = tc.getValue().get(0);
                } else {
                    String lastCommit = GitUtil.getLastCommitFrom(g.get().getRepository(), tc.getValue().stream().map(x -> x._1()).collect(toList()));
                    commitToAnalyze = tc.getValue().stream().filter(x -> x._1().equals(lastCommit)).findFirst().get();
                }

                Set<CompilationUnit> relevantCus;

                if (tc.getKey().getRoot().getIsTypeVariable())
                    continue;


                if (tc.getKey().getRoot().getKind().equals(TypeNodeOuterClass.TypeNode.TypeKind.Primitive)
                        || pretty(tc.getKey()).startsWith("java.lang")) {
                    relevantCus = populateCu(g.get().getRepository(), commitToAnalyze._1(), c -> true);
                } else {
                    relevantCus = populateCu(g.get().getRepository(), commitToAnalyze._1(), cu -> {
                        boolean packageMatch = cu.getPackage() != null && pretty(tc.getKey()).contains(cu.getPackage().toString());
                        if (packageMatch)
                            return packageMatch;

                        List<ImportDeclaration> imports = cu.imports();
                        boolean importMatch = imports.stream().map(x -> x.getName().getFullyQualifiedName())
                                .anyMatch(i -> pretty(tc.getKey()).contains(i));
                        return importMatch;
                    });
                }

                int typeUSageCounter = 0;
                for (var cu : relevantCus) {
                    List<AbstractTypeDeclaration> typesDeclared = cu.types();
                    Visitors.UsedTypesCounter ut = new Visitors.UsedTypesCounter(tc.getKey());
                    for (var td : typesDeclared) {
                        td.accept(ut);
                        typeUSageCounter += ut.counter;
                    }
                }

                double ratio = typeUSageCounter > 0 ? (double) commitToAnalyze._2() / (commitToAnalyze._2() + typeUSageCounter) : 1.0;


                MigrationDataOuterClass.MigrationData md = MigrationDataOuterClass.MigrationData.newBuilder()
                        .setType(tc.getKey())
                        .setNamespace(commitToAnalyze._4())
                        .setRatio(ratio)
                        .addAllCommitToType(tc.getValue()
                                .stream().map(x -> MigrationDataOuterClass.MigrationData.CommitToType.newBuilder()
                                        .setSha(x._1()).addToType(x._3()).build()).collect(toList()))
                        .build();

                Runner.readWriteMigrationProtos.write(md, "Migration_" + prj.getName(), true);

                System.out.println(pretty(md.getType()) + "   " + md.getRatio() + "  "  + " " + md.getNamespace().name() + "  " + md.getCommitToTypeCount());



            }
        });

    }



}
