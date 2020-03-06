import TFGStuff.B4After;
import TFGStuff.MappingObj;
import TFGStuff.Utils;
import com.github.gumtreediff.actions.EditScript;
import com.github.gumtreediff.actions.model.*;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.TreeContext;
import com.t2r.common.models.ast.TypeGraphOuterClass.TypeGraph;
import com.t2r.common.models.refactorings.ProcessedCodeMappingsOuterClass.ProcessedCodeMappings;
import com.t2r.common.models.refactorings.ProcessedCodeMappingsOuterClass.ProcessedCodeMappings.ExpressionMapping;
import com.t2r.common.models.refactorings.ProcessedCodeMappingsOuterClass.ProcessedCodeMappings.RelevantStmtMapping;
import com.t2r.common.models.refactorings.ProjectOuterClass.Project;
import com.t2r.common.models.refactorings.TypeChangeAnalysisOuterClass.TypeChangeAnalysis;
import com.t2r.common.models.refactorings.TypeChangeAnalysisOuterClass.TypeChangeAnalysis.CodeMapping;
import com.t2r.common.models.refactorings.TypeChangeAnalysisOuterClass.TypeChangeAnalysis.TypeChangeInstance;
import com.t2r.common.models.refactorings.TypeChangeCommitOuterClass.TypeChangeCommit;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CastExpression;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import static TFGStuff.Utils.processStatement;
import static TFGStuff.Utils.replaceIfExists;
import static com.t2r.common.utilities.PrettyPrinter.prettyLIST;
import static com.t2r.common.utilities.PrettyPrinter.prettyName;
import static gr.uom.java.xmi.TypeFactMiner.TypeGraphUtil.getTypeGraph;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.*;

public class AnalyseChangePatterns {


    public static List<TypeChangeAnalysis> getTypeChangeAnalysisFor(List<TypeChangeAnalysis> l, Tuple2<TypeGraph, TypeGraph> tg) {
        return l.stream().filter(x -> x.getB4().equals(tg._1()) && x.getAftr().equals(tg._2()))
                .collect(toList());
    }


    public static void main(String a[]) {


//        List<ProcessedCodeMappings> processedCodeMappings = Runner.readWriteCodeMappingProtos.readAll("ProcessedCodeMapping", "CodeMapping");

        ArrayList<Project> projects = new ArrayList<>(Runner.readWriteInputProtos.<Project>readAll("Projects", "Project"));

        List<Tuple2<Project, List<TypeChangeCommit>>> procect_tcc = projects.stream()
                .map(z -> Tuple.of(z,Runner.readWriteOutputProtos.<TypeChangeCommit>readAll("TypeChangeCommit_" + z.getName(), "TypeChangeCommit")))
                .collect(toList());

        var typeChangeAnalysiss = procect_tcc.stream().flatMap(x -> x._2().stream()).flatMap(tcc -> tcc.getTypeChangesList().stream())
                .filter(e -> !e.getB4().getRoot().getIsTypeVariable() && !e.getAftr().getRoot().getIsTypeVariable())
                .collect(toList());

        var groupedTci = typeChangeAnalysiss.stream()
                .collect(groupingBy(x -> Tuple.of(x.getB4(), x.getAftr())
                        , collectingAndThen(toList(), l -> l.stream().flatMap(x -> x.getTypeChangeInstancesList().stream()).collect(toList()))))
                .entrySet().stream().sorted(Comparator.comparingInt(x -> x.getValue().size())).collect(toList());

        Collections.reverse(groupedTci);

        System.out.println(groupedTci.size());

        List<ProcessedCodeMappings> processedCodeMappings =
                Runner.readWriteCodeMappingProtos.readAll("ProcessedCodeMapping", "CodeMapping");
//                groupedTci.parallelStream()
//                .map(Tuple::fromEntry)
//                .map(x -> ProcessedCodeMappings.newBuilder()
//                        .setB4(x._1()._1()).setAftr(x._1()._2()).addAllRelevantStmts(tciAnalysis(x._2())).build())
//                .collect(toList());

//        processedCodeMappings.forEach(f -> Runner.readWriteCodeMappingProtos.write(f, "ProcessedCodeMapping", true));


        List<Tuple2<Tuple2<TypeGraph, TypeGraph>, RelevantStmtMapping>> tciMappings = processedCodeMappings.stream()
                .flatMap(pc -> pc.getRelevantStmtsList().stream().map(x -> Tuple.of(Tuple.of(pc.getB4(), pc.getAftr()), x)))
                .filter(x -> x._2().getMappingCount() > 0)
                .collect(toList());



        List<Tuple2<Tuple2<TypeGraph, TypeGraph>, Map<String, Long>>> tciMappingsAsStr =
                tciMappings.stream().map(z -> z.map2(r -> r.getMappingList().stream().collect(groupingBy(m -> m.getReplacement(), counting()))))
                .collect(toList());

        Map<String, List<Tuple2<Tuple2<TypeGraph, TypeGraph>, Map<String, Long>>>> labelledTCI = tciMappingsAsStr.parallelStream()
                .map(x -> Tuple.of(x._1(), Labellers.TCALabeller.apply(getTypeChangeAnalysisFor(typeChangeAnalysiss, x._1())), x._2()))
                .collect(groupingBy(x -> x._2(), collectingAndThen(toList(), l -> l.stream().map(x -> Tuple.of(x._1(), x._3())).collect(toList()))));

        long count = tciMappingsAsStr.stream().filter(x -> !x._2().isEmpty()).count();
        System.out.println("Total Type Changes with edit patterns = " + count);


        for (var ll : labelledTCI.entrySet()) {
            System.out.println("------------");
            System.out.println(ll.getKey());


            long size = ll.getValue().size();

            System.out.println("Total Type Changes : " + size);
            System.out.println("No Change found for : " + ll.getValue().stream().filter(x -> x._2().isEmpty()).count() + " Type Changes");

            ll.getValue().stream()
                    .flatMap(x -> x._2().entrySet().stream().map(p -> Tuple.of(x._1(), p)))
                    .collect(groupingBy(x -> x._2().getKey(), counting()))
                    .entrySet().stream()
                    .sorted(Comparator.comparingLong(x -> x.getValue()))
                    .forEach(e -> System.out.println("\t" + e.getKey() + ll.getKey().toLowerCase() + " --- " + (double) e.getValue() / size));

        }


//        var editPatGroupedByPattern = z.stream().flatMap(x -> x._2().entrySet().stream().map(Tuple::fromEntry))
//                .collect(groupingBy(x -> x._1(), summingLong(x -> x._2())));
//
//        var groupedByTypeChange = z.stream().flatMap(x -> x._2().keySet().stream().map(g -> Tuple.of(x._1(), g)))
//                .collect(groupingBy(g -> g._2(), counting()));
//
//
//        editPatGroupedByPattern.entrySet().forEach(x -> System.out.println(x.getKey() + ", " + x.getValue()));
//        System.out.println("##################################################################################################");
//        groupedByTypeChange.entrySet().forEach(x -> System.out.println("\\newcommand{" + x.getKey() + "}{" + (double) x.getValue() / count + "\\%\\xspace"));
//
//        System.out.println(editPatGroupedByPattern);


    }

    private static List<RelevantStmtMapping> tciAnalysis(List<TypeChangeInstance> typeChangeInstancesList) {
        return typeChangeInstancesList.stream()
                        .flatMap(x -> x.getCodeMappingList().stream()
                                .map(c -> analyzeCodeMapping(x, c, x.getB4(), x.getAftr()))
                                .filter(z -> z.getMappingCount() > 0))
                        .collect(toList());

    }

    public static String relabelChangePattern(String pattern) {
        if (Labellers.editPatternMAp.containsKey(pattern))
            return Labellers.editPatternMAp.get(pattern);
        System.out.println(pattern);
        return "Other";
    }

    private static RelevantStmtMapping analyzeCodeMapping(TypeChangeInstance tci, CodeMapping c, TypeGraph b4, TypeGraph aftr) {
        if (c.getIsSame())
            return RelevantStmtMapping.newBuilder().build();

        var replacementsInferred = c.getReplcementInferredList().stream()
                .filter(x -> !"VARIABLE_NAME".equals(x.getReplacementType())).collect(toList());

        MappingObj mo = new MappingObj(c, new B4After<>(tci.getNameB4(), tci.getNameAfter()));

        var z = mo.getRelevantExprs().map(r -> {
                    var zz = r.map2(x -> x.replace(tci.getNameAfter(), tci.getNameB4()));
                    if (!zz._1().equals(zz._2()))
                        return replacementsInferred.stream()
                                .map(getReplacementInferredStringFunction(zz, b4, aftr))
                                .flatMap(Collection::stream)
//                                .map(x -> Tuple.of(r, x))
                                .collect((toList()));
                    return new ArrayList<ExpressionMapping>();
                }).orElseGet(() -> replacementsInferred.stream()
                    .map(x -> ExpressionMapping.newBuilder()
                            .setB4(x.getB4()).setAftr(x.getAftr()).setReplacement(x.getReplacementType()).build())
                    .collect((toList())));

        if (!tci.getNameAfter().equals(tci.getNameB4())) {
            z.add(ExpressionMapping.newBuilder().setB4(tci.getNameB4()).setAftr(tci.getNameAfter()).setReplacement("VARIABLE-RENAME").build());
        }

        return RelevantStmtMapping.newBuilder()
                .addAllMapping(z.stream()
                        .map(x -> x.toBuilder().setReplacement(relabelChangePattern(x.getReplacement())).build()).collect(toList()))
                .setB4(c.getB4()).setAfter(c.getAfter())
                .setUrlbB4(c.getUrlbB4()).setUrlAftr(c.getUrlAftr()).build();


    }

    private static Function<TypeChangeAnalysis.ReplacementInferred, List<ExpressionMapping>> getReplacementInferredStringFunction
            (Tuple2<String, String> zz, TypeGraph b4, TypeGraph aftr) {
        return x -> {
            switch (x.getReplacementType()) {
                case "METHOD_INVOCATION":
                case "CLASS_INSTANCE_CREATION":
                case "ARGUMENT_REPLACED_WITH_RETURN_EXPRESSION":
                    List<ExpressionMapping> gumTreeEditPatterns = getGumTreeEditPatterns(x.getB4(), x.getAftr());
                    if (gumTreeEditPatterns.isEmpty())
                        gumTreeEditPatterns = getGumTreeEditPatterns(zz._1(), zz._2());
                    if (gumTreeEditPatterns.isEmpty())
                        return singletonList(ExpressionMapping.newBuilder().setB4(x.getB4()).setAftr(x.getAftr()).setReplacement(x.getReplacementType()).build());
                    return gumTreeEditPatterns;
                case "TYPE":
                    if (isCascadingDifferentKind(Tuple.of(x.getB4(), x.getAftr()), Tuple.of(b4, aftr))) {
                        return singletonList(ExpressionMapping.newBuilder().setB4(x.getB4()).setAftr(x.getAftr())
                                .setReplacement(x.getReplacementType() + "-Different").build());
                    }
                    return singletonList(ExpressionMapping.newBuilder().setB4(x.getB4()).setAftr(x.getAftr()).setReplacement(x.getReplacementType()).build());
//                case "ARGUMENT_REPLACED_WITH_VARIABLE":
                case "COMPOSITE":
                    System.out.println();
                default:
                    return singletonList(ExpressionMapping.newBuilder().setB4(x.getB4()).setAftr(x.getAftr()).setReplacement(x.getReplacementType()).build());
            }
        };
    }

    private static boolean isCascadingDifferentKind(Tuple2<String, String> typeChangePattern, Tuple2<TypeGraph, TypeGraph> typeChange) {

        Tuple2<List<String>, List<String>> sourceTypeChange = typeChange.map(x -> prettyLIST(x), x -> prettyLIST(x));

        Tuple2<ASTNode, ASTNode> tcp = typeChangePattern
                .map1(x -> Utils.getASTNodeExpr("(" + replaceIfExists(x, "<>", "") + ")" + "x"))
                .map2(x -> Utils.getASTNodeExpr("(" + replaceIfExists(x, "<>", "") + ")" + "x"));

        if (tcp._1() instanceof CastExpression && tcp._2() instanceof CastExpression) {
            Tuple2<String, String> cascadingTypeChange = tcp.map(x -> (CastExpression) x, x -> (CastExpression) x)
                    .map(x -> prettyName(getTypeGraph(x.getType())), x -> prettyName(getTypeGraph(x.getType())));

            return sourceTypeChange._1().stream().noneMatch(x -> x.equals(cascadingTypeChange._1())
                    || x.endsWith("." + cascadingTypeChange._1()))
                    || sourceTypeChange._2().stream().noneMatch(x -> x.equals(cascadingTypeChange._2())
                    || x.endsWith("." + cascadingTypeChange._2()));
        }
        return false;
    }


    public static List<ExpressionMapping> getGumTreeEditPatterns(String before, String after) {
        try {
            TreeContext dst = getTreeContext(after);
            TreeContext src = getTreeContext(before);
            Matcher m = Matchers.getInstance().getMatcher();
            com.github.gumtreediff.actions.EditScriptGenerator e = new com.github.gumtreediff.actions.SimplifiedChawatheScriptGenerator();
            if(src == null ||  dst == null)
                return new ArrayList<>();
            EditScript editScript = e.computeActions(m.match(src.getRoot(), dst.getRoot()));
            Iterator<Action> it = editScript.iterator();
            List<Tuple2<String, String>> rawActions = new ArrayList<>();
            while (it.hasNext()) {
                Action a = it.next();
                String str_Axn = a.getName() + " " + a.getNode().getType().name + " " + a.getNode().getParent().getType().name;
                if (a.getName().contains("update-node")) {
                    Update upd = (Update) a;
                    rawActions.add(Tuple.of(str_Axn, upd.getNode().getLabel().toString()));
                } else if (a.getName().contains("insert-node")) {
                    Insert ins = (Insert) a;
                    rawActions.add(Tuple.of(str_Axn, ins.getNode().getLabel()));
                } else if (a.getName().contains("delete-node")) {
                    Delete ins = (Delete) a;
                    rawActions.add(Tuple.of(str_Axn, ins.getNode().getLabel()));
                } else if (a.getName().contains("insert-tree")) {
                    TreeInsert ins = (TreeInsert) a;
                    rawActions.add(Tuple.of(str_Axn, ins.getNode().getLabel()));
                } else if (a.getName().contains("delete-tree")) {
                    TreeDelete ins = (TreeDelete) a;
                    rawActions.add(Tuple.of(str_Axn, ins.getNode().getLabel()));
                } else if (a.getName().contains("insert-tree")) {
                    TreeInsert ins = (TreeInsert) a;
                    rawActions.add(Tuple.of(str_Axn, ins.getNode().getLabel()));
                } else {
                    rawActions.add(Tuple.of(str_Axn, ""));
                }
            }
            return Labellers.processRawActions(rawActions);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }


    private static TreeContext getTreeContext(String s) throws IOException {
        TreeContext dst;
        Optional<String> stmt = processStatement(s);
        if (stmt.isEmpty()) {
            dst = new com.github.gumtreediff.gen.jdt.JdtTreeGenerator().generateFrom()
                    .stringExpr(s);
        } else {
            dst = new com.github.gumtreediff.gen.jdt.JdtTreeGenerator().generateFrom()
                    .stringStatement(stmt.get());
        }
        return dst;
    }


}
