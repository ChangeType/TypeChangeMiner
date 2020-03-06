package com.t2r.common.utilities;

import com.t2r.common.models.ast.TypeGraphOuterClass.TypeGraph;
import com.t2r.common.models.ast.TypeNodeOuterClass;
import com.t2r.common.models.ast.TypeNodeOuterClass.TypeNode.TypeKind;
import com.t2r.common.models.refactorings.DependencyUpdateOuterClass.DependencyUpdate;
import com.t2r.common.models.refactorings.FileDiffOuterClass.FileDiff;
import com.t2r.common.models.refactorings.JarInfoOuterClass;
import com.t2r.common.models.refactorings.TypeChangeAnalysisOuterClass.TypeChangeAnalysis;
import com.t2r.common.models.refactorings.TypeChangeAnalysisOuterClass.TypeChangeAnalysis.SyntacticTransformation;
import com.t2r.common.models.refactorings.TypeChangeAnalysisOuterClass.TypeChangeAnalysis.TypeChangeStatistics;
import com.t2r.common.models.refactorings.TypeChangeCommitOuterClass.TypeChangeCommit;
import com.t2r.common.models.refactorings.TypeChangeCommitOuterClass.TypeChangeCommit.MigrationAnalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.t2r.common.models.ast.TypeNodeOuterClass.TypeNode.TypeKind.Primitive;
import static com.t2r.common.models.ast.TypeNodeOuterClass.TypeNode.TypeKind.Simple;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class PrettyPrinter {

    public static boolean looselyEqual(TypeGraph tg, TypeGraph tg2) {
        if(!tg.getRoot().getKind().equals(tg2.getRoot().getKind()))
            return false;
        if (tg.getRoot().getKind().equals(Simple) || tg.getRoot().getKind().equals(Primitive)) {
            return tg.getRoot().getName().equals(tg2.getRoot().getName()) || tg.getRoot().getName().endsWith("."+tg2.getRoot().getName())
                    || tg2.getRoot().getName().endsWith("."+tg.getRoot().getName());
        } else if (tg.getRoot().getKind().equals(TypeKind.Parameterized)) {
            return looselyEqual(tg.getEdgesMap().get("of"), tg2.getEdgesMap().get("of"))
                    && tg.getEdgesMap().entrySet().stream().filter(x -> x.getKey().contains("Param")).count() ==
                        tg2.getEdgesMap().entrySet().stream().filter(x -> x.getKey().contains("Param")).count()
                    && tg.getEdgesMap().entrySet().stream().filter(x -> x.getKey().contains("Param"))
                    .allMatch(x -> looselyEqual(x.getValue(), tg2.getEdgesMap().get(x.getKey())));

        } else if (tg.getRoot().getKind().equals(TypeKind.Array)) {
            return looselyEqual(tg.getEdgesMap().get("of"), tg2.getEdgesMap().get("of"));
        } else if (tg.getRoot().getKind().equals(TypeKind.WildCard)) {
            if (tg.getEdgesMap().containsKey("extends") && tg2.getEdgesMap().containsKey("extends")) {
                return looselyEqual(tg.getEdgesMap().get("extends"), tg2.getEdgesMap().get("extends"));
            }
            if (tg.getEdgesMap().containsKey("super") && tg2.getEdgesMap().containsKey("super")) {
                return looselyEqual(tg.getEdgesMap().get("super"), tg2.getEdgesMap().get("super"));
            }
            if(tg.getEdgesCount() == 0 && tg2.getEdgesCount() == 0)
                return true;
        } else if (tg.getRoot().getKind().equals(TypeKind.Union))
            return false;

        return false;
    }


    public static List<String> prettyLIST(TypeGraph tg) {
        if (tg.getRoot().getKind().equals(Simple) || tg.getRoot().getKind().equals(Primitive)) {
            return singletonList(tg.getRoot().getName());
        } else if (tg.getRoot().getKind().equals(TypeKind.Parameterized)) {
            return Stream.concat(Stream.of(pretty(tg.getEdgesMap().get("of")))
                    , tg.getEdgesMap().entrySet().stream().filter(x -> x.getKey().contains("Param"))
                        .flatMap(x -> prettyLIST(x.getValue()).stream())).collect(toList());
        } else if (tg.getRoot().getKind().equals(TypeKind.Array)) {
            return prettyLIST(tg.getEdgesMap().get("of"));
        } else if (tg.getRoot().getKind().equals(TypeKind.WildCard)) {
            if (tg.getEdgesMap().containsKey("extends")) {
                return prettyLIST(tg.getEdgesMap().get("extends"));
            }
            else if (tg.getEdgesMap().containsKey("super")){
                return prettyLIST(tg.getEdgesMap().get("super"));
            }
            else{
                return singletonList("?");
            }
        } else if (tg.getRoot().getKind().equals(TypeKind.Union))
            return tg.getEdgesMap().entrySet().stream().filter(x -> x.getKey().contains("Union") || x.getKey().contains("Intersection"))
                    .flatMap(x -> prettyLIST(x.getValue()).stream())
                    .collect(Collectors.toList());
        return new ArrayList<>();
    }

    public static String pretty(TypeGraph tg) {
        if (tg.getRoot().getKind().equals(Simple) || tg.getRoot().getKind().equals(Primitive)) {
            return tg.getRoot().getName();
        } else if (tg.getRoot().getKind().equals(TypeKind.Parameterized)) {
            return pretty(tg.getEdgesMap().get("of")) + "<"
                    + tg.getEdgesMap().entrySet().stream().filter(x -> x.getKey().contains("Param"))
                    .map(x -> pretty(x.getValue())).collect(joining(", ")) + ">";
        } else if (tg.getRoot().getKind().equals(TypeKind.Array)) {
            return pretty(tg.getEdgesMap().get("of")) + "[]";
        } else if (tg.getRoot().getKind().equals(TypeKind.WildCard)) {
            if (tg.getEdgesMap().containsKey("extends")) {
                return "? extends " + pretty(tg.getEdgesMap().get("extends"));
            }
            if (tg.getEdgesMap().containsKey("super"))
                return "? super " + pretty(tg.getEdgesMap().get("super"));
            else return "?";
        } else if (tg.getRoot().getKind().equals(TypeKind.Union))
            return tg.getEdgesMap().entrySet().stream().filter(x -> x.getKey().contains("Union") || x.getKey().contains("Intersection"))
                    .map(x -> pretty(x.getValue()))
                    .collect(joining(" & "));
        return "";
    }

    public static String prettyName(TypeGraph tg) {
        if (tg.getRoot().getKind().equals(Simple) || tg.getRoot().getKind().equals(Primitive)) {
            return tg.getRoot().getName();
        } else if (tg.getRoot().getKind().equals(TypeKind.Parameterized)) {
            return pretty(tg.getEdgesMap().get("of"));
        } else if (tg.getRoot().getKind().equals(TypeKind.Array)) {
            return pretty(tg.getEdgesMap().get("of"));
        } else if (tg.getRoot().getKind().equals(TypeKind.WildCard)) {
            if (tg.getEdgesMap().containsKey("extends")) {
                return  pretty(tg.getEdgesMap().get("extends"));
            }
            if (tg.getEdgesMap().containsKey("super"))
                return pretty(tg.getEdgesMap().get("super"));
            else return "?";
        } else if (tg.getRoot().getKind().equals(TypeKind.Union))
            return "";
//            return tg.getEdgesMap().entrySet().stream().filter(x -> x.getKey().contains("Union") || x.getKey().contains("Intersection"))
//                    .map(x -> pretty(x.getValue()))
//                    .collect(joining(" & "));
        return "";
    }


    public static String pretty(TypeNodeOuterClass.TypeNode tg) {
        if (tg.getKind().equals(Simple) || tg.getKind().equals(Primitive)) {
            return tg.getName();
        }
        return "";
    }


    public static String prettySyntacticUpdate(SyntacticTransformation t) {
        return (pretty(t.getB4()) + "   " + pretty(t.getAftr()))
                + "\n"
                + String.join("\t", t.getTransformation())
                + "\n"
                + t.getSubTransformationsList().stream().map(PrettyPrinter::prettySyntacticUpdate).collect(joining("\n"));

    }


    public static String prettyCommit(TypeChangeCommit tcc) {

        return String.join("\n", "===========================" + tcc.getSha() + "=========================="
                , prettyDepUpdate(tcc.getDependencyUpdate(), tcc)
                , prettyFileStats(tcc.getFileDiff())
                , tcc.getTypeChangesList().stream().map(x -> prettyTypeChangeAnalysis(x)).collect(joining("\n"))
                , tcc.getMigrationAnalysisCount() > 0 ? "Type Migration : " + tcc.getMigrationAnalysisList().stream()
                        .map(x -> pretty(x)).collect(joining(",")) : ""
                , "==============");
    }


    public static String pretty(MigrationAnalysis ma){
        return ma.getType() + " " + ma.getTypeMigrationLevel();
    }

    public static String prettyFileStats(FileDiff fd) {
        return String.join("\n", "--------File Stats-------"
                , "Added : " + fd.getFilesAdded()
                , "Removed : " + fd.getFilesRemoved()
                , "Renamed : " + fd.getFilesRenamed()
                , "Modified : " + fd.getFilesModified());
    }


    public static String prettyJar(JarInfoOuterClass.JarInfo ji) {
        return String.join(",", ji.getArtifactID(), ji.getGroupID(), ji.getVersion());
    }

    public static String prettyDepUpdate(DependencyUpdate du, TypeChangeCommit tcc) {
        if (du == null)
            return "";
        return Stream.of( "----Dependencies----"
                , du.getRemovedList().stream().map(x -> prettyJar(x)).collect(joining(","))
                , du.getAddedList().stream().map(x -> prettyJar(x)).collect(joining(","))
                , du.getUpdateList().stream().map(x -> prettyJar(x.getBefore()) + " -> " + prettyJar(x.getAfter())).collect(joining(","))
                 , "---------Dependencies-------").filter(x -> !x.isEmpty()).collect(joining("\n"));
    }

    public static String prettyTypeChangeAnalysis(TypeChangeAnalysis tca) {
        return "-----" + "\n" +
                pretty(tca.getB4()) + " to " + pretty(tca.getAftr()) + "\n"
                + "\t" + tca.getNameSpacesB4().name() + " " + tca.getNameSpaceAfter().name() + "\n"
                + "\t" + tca.getTypeSemb4().name() + " " + tca.getTypeSemAftr().name() + "\n"
                + (tca.getHierarchyRelation().isEmpty() ? "" : "\t" + tca.getHierarchyRelation() + "\n")
                + (tca.getB4ComposesAfter() ? "Composition" : "")
                + prettyPrimitiveInfo(tca) + "\n"
                + "\t" + tca.getTypeChangeInstancesCount() + "\n"
                + "\t Total Matched Statements : " + tca.getTypeChangeInstancesList().stream().mapToLong(x -> x.getCodeMappingCount()).sum() +  "\n"
                + "\t Changed Matched statements : " + tca.getTypeChangeInstancesList().stream()
                        .mapToLong(x -> x.getCodeMappingList().stream().filter(z -> !z.getIsSame()).count()).sum() +  "\n"
                + prettyTypeChangeStats(tca.getTypeChangeStats())
                + "-----";
    }


    public static String prettyTypeChangeStats(TypeChangeStatistics tcs){
        return  "---- Type Change Statistics ------" + "\n"
                + "Visibility : " + tcs.getVisibilityStatsMap().toString() + "\n"
                + "ElementKind : " + tcs.getElementKindStatsMap().toString() + "\n"
                + "Syntactic Update :  " + tcs.getTransformationStatsMap() + "\n"
                + "NameSpace " + tcs.getNameSpaceStatsMap()
                + "----------";
    }

    public static String prettyPrimitiveInfo(TypeChangeAnalysis tca) {
        if (!tca.hasPrimitiveInfo())
            return "";
        var pa = tca.getPrimitiveInfo();
        return "\t" + (pa.getBoxing() ? "Boxed\n" : "")
                + (pa.getUnboxing() ? "Unboxed\n" : "")
                + (pa.getWidening() ? "Widened\n" : "")
                + (pa.getNarrowing() ? "Narrowed\n" : "");

    }



}
