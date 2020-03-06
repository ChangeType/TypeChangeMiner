import com.t2r.common.models.ast.TypeGraphOuterClass.TypeGraph;
import com.t2r.common.models.ast.TypeNodeOuterClass.TypeNode;
import com.t2r.common.models.refactorings.CodeStatisticsOuterClass.CodeStatistics;
import com.t2r.common.models.refactorings.CodeStatisticsOuterClass.CodeStatistics.Element;
import com.t2r.common.models.refactorings.CommitInfoOuterClass.CommitInfo;
import com.t2r.common.models.refactorings.DependencyUpdateOuterClass.DependencyUpdate;
import com.t2r.common.models.refactorings.JarInfoOuterClass.JarInfo;
import com.t2r.common.models.refactorings.NameSpaceOuterClass.NameSpace;
import com.t2r.common.models.refactorings.ProjectOuterClass.Project;
import com.t2r.common.models.refactorings.TypeChangeAnalysisOuterClass.TypeChangeAnalysis;
import com.t2r.common.models.refactorings.TypeChangeAnalysisOuterClass.TypeChangeAnalysis.TypeChangeInstance;
import com.t2r.common.models.refactorings.TypeChangeAnalysisOuterClass.TypeChangeAnalysis.TypeChangeStatistics;
import com.t2r.common.models.refactorings.TypeChangeCommitOuterClass.TypeChangeCommit;
import com.t2r.common.models.refactorings.TypeChangeCommitOuterClass.TypeChangeCommit.DependencyUpdateImpact;
import com.t2r.common.models.refactorings.TypeChangeCommitOuterClass.TypeChangeCommit.MigrationAnalysis;
import com.t2r.common.models.refactorings.TypeSemOuterClass.TypeSem;
import com.t2r.common.utilities.PrettyPrinter;
import gr.uom.java.xmi.TypeFactMiner.ExtractHierarchyPrimitiveCompositionInfo;
import gr.uom.java.xmi.TypeFactMiner.GlobalContext;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.api.TypeRelatedRefactoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static com.t2r.common.models.ast.TypeNodeOuterClass.TypeNode.TypeKind.Simple;
import static com.t2r.common.models.refactorings.NameSpaceOuterClass.NameSpace.*;
import static com.t2r.common.models.refactorings.TypeSemOuterClass.TypeSem.Enum;
import static com.t2r.common.models.refactorings.TypeSemOuterClass.TypeSem.Object;
import static com.t2r.common.models.refactorings.TypeSemOuterClass.TypeSem.*;
import static com.t2r.common.utilities.PrettyPrinter.pretty;
import static com.t2r.common.utilities.PrettyPrinter.prettyLIST;
import static gr.uom.java.xmi.TypeFactMiner.TypFct.getDependencyInfo;
import static gr.uom.java.xmi.decomposition.UMLOperationBodyMapper.isRelevant;
import static java.util.stream.Collectors.*;
import static java.util.stream.Stream.concat;

public class ChangeTypeMiner extends RefactoringHandler {


    private final Project project;

    public ChangeTypeMiner(Project project){

        this.project = project;
    }


    private static double round(double d) {
        try {
            BigDecimal bd = new BigDecimal(d).setScale(2, RoundingMode.HALF_UP);
            return bd.doubleValue();
        } catch (Exception e) {
            e.printStackTrace();
            return 0.0;
        }
    }

    Set<JarInfo> jarsRequired = new HashSet<>();

    @Override
    public void handle(CommitInfo c, List<Refactoring> refactorings, Tuple2<GlobalContext, GlobalContext> globalContexts, CodeStatistics cs){

        TypeChangeCommit.Builder tcc = TypeChangeCommit.newBuilder()
                .setSha(c.getSha())
                .setDependencyUpdate(c.getDependencyUpdate())
                .setFileDiff(c.getFileDiff())
                .putAllRefactorings(getAllRefactoringsInCommit(c));


        if (refactorings.isEmpty() || refactorings.stream().noneMatch(Refactoring::isTypeRelatedChange)) {
            System.out.println("No CTT");
            Runner.readWriteOutputProtos.write(tcc.build(), "TypeChangeCommit_"+project.getName(), true);
            return;
        }

        var typeRelatedRefactorings = refactorings.stream()
                .filter(Refactoring::isTypeRelatedChange).map(x -> (TypeRelatedRefactoring) x)
                .collect(toList());

        var typeChangeAnalysisList = getTypeChangeAnalyses(globalContexts, typeRelatedRefactorings, cs);

        if (typeChangeAnalysisList.isEmpty()) {
            System.out.println("NO Type Changes found");
            Runner.readWriteOutputProtos.write(tcc.build(), "TypeChangeCommit_"+project.getName(), true);
            return;
        }

        var typeChangeCommit = TypeChangeCommit.newBuilder()
                .setSha(c.getSha())
                .setDependencyUpdate(c.getDependencyUpdate())
                .addDependencyUpdateImpact(DependencyUpdateImpact.newBuilder()
                        .setKind("Updated").addAllTypes(updateImpact(c.getDependencyUpdate(), globalContexts._1(), typeChangeAnalysisList)))
                .addDependencyUpdateImpact(DependencyUpdateImpact.newBuilder()
                        .setKind("Added").addAllTypes(addedImpact(c.getDependencyUpdate(), globalContexts._1(), typeChangeAnalysisList)))
                .addDependencyUpdateImpact(DependencyUpdateImpact.newBuilder()
                        .setKind("Removed").addAllTypes(removedImpact(c.getDependencyUpdate(), globalContexts._1(), typeChangeAnalysisList)))
                .setFileDiff(c.getFileDiff())
                .putAllRefactorings(getAllRefactoringsInCommit(c))
                .addAllTypeChanges(typeChangeAnalysisList)
                .addAllMigrationAnalysis(typeChangeAnalysisList.stream()
                        .flatMap(x -> performMigrationAnalysis(x, globalContexts._1()).stream())
                        .collect(toList()))
                .build();


        System.out.println(PrettyPrinter.prettyCommit(typeChangeCommit));

        typeRelatedRefactorings.stream().filter(x -> !x.isResolved()).forEach(x -> {
            System.out.println("UNRESOLVED");
            System.out.println(((Refactoring) x).getName() + "  "
                    + x.getTypeB4().getTypeStr() + "  ---->   " + x.getTypeAfter().getTypeStr() + "\n");
        });

        Runner.readWriteOutputProtos.write(typeChangeCommit, "TypeChangeCommit_"+project.getName(), true);

        System.out.println("-------------------------------");
        System.out.println();

    }

    @Override
    public void handleException(String commitID, Exception e){
        e.printStackTrace();
    }

    private List<TypeChangeAnalysis> getTypeChangeAnalyses(Tuple2<GlobalContext, GlobalContext> globalContexts, List<TypeRelatedRefactoring> typeRelatedRefactorings, CodeStatistics cs) {
        Map<Tuple2<TypeGraph, TypeGraph>, Set<TypeChangeInstance>> groupedTypeChanges = typeRelatedRefactorings
                .stream()
                .flatMap(x -> {
                    if (x.getRealTypeChanges() == null)
                        return Stream.empty();
                    return x.getRealTypeChanges().stream().map(t -> Tuple.of(t, x.getTypeChangeInstance()));
                })
                .collect(groupingBy(Tuple2::_1
                        , collectingAndThen(toList(), x -> x.stream().map(t -> t._2()).collect(toSet()))));

        return groupedTypeChanges.entrySet().stream()
                .flatMap(x -> performAndGetTypeChangeAnalysis(x, globalContexts._1(), cs).stream())
                .collect(toList());
    }

    private Map<String, Long> getAllRefactoringsInCommit(CommitInfo c) {
        return c.getRefactoringsList().stream().map(x -> Tuple.of(x.getName(), x.getOccurences()))
                .collect(toMap(x -> x._1(), x -> x._2()));
    }


    private Optional<TypeChangeAnalysis> performAndGetTypeChangeAnalysis(Entry<Tuple2<TypeGraph, TypeGraph>, Set<TypeChangeInstance>> entry, GlobalContext gc
            , CodeStatistics cs) {

        var nameSpaceTypeSem = entry.getKey().map(t -> getNameSpaceAndTypeSem(t, gc), t -> getNameSpaceAndTypeSem(t, gc));

        // Remove Type Changes between Type Variables
        if ((nameSpaceTypeSem._1()._1().equals(TypeVariable) ||entry.getKey()._1().getRoot().getIsTypeVariable())
                && (nameSpaceTypeSem._2()._1().equals(TypeVariable) || entry.getKey()._2().getRoot().getIsTypeVariable()))
            return Optional.empty();

        // Remove Type Changes due to Add Remove Type Parameter
        if(entry.getValue().stream().map(x -> x.getSyntacticUpdate().getTransformation()).allMatch(x -> x.equals("Add Type Parameters")
                || x.equals("Remove Type Parameters") || x.equals("Reorder Type Parameters")))
            return Optional.empty();

        Set<JarInfo> allJars = concat(gc.getRequiredJars().stream(), gc.getRequiredJars().stream())
                .collect(toSet());
        var e = new ExtractHierarchyPrimitiveCompositionInfo(gc, allJars, Runner.pathToDependencies);
        TypeChangeAnalysis hierarchyPrimitiveCompositionInfo = e.extract(entry.getKey()._1(), entry.getKey()._2());
        return Optional.of(TypeChangeAnalysis.newBuilder().setB4(entry.getKey()._1()).setAftr(entry.getKey()._2())
                .addAllTypeChangeInstances(entry.getValue())
                .setTypeChangeStats(getTypeChangeStatistics(entry, cs, nameSpaceTypeSem))
                .setNameSpacesB4(nameSpaceTypeSem._1()._1()).setNameSpaceAfter(nameSpaceTypeSem._2()._1())
                .setTypeSemb4(nameSpaceTypeSem._1()._2()).setTypeSemAftr(nameSpaceTypeSem._2()._2())
                .mergeFrom(hierarchyPrimitiveCompositionInfo).build());
    }


    private TypeChangeStatistics getTypeChangeStatistics(Entry<Tuple2<TypeGraph, TypeGraph>, Set<TypeChangeInstance>> entry, CodeStatistics cs
            , Tuple2<Tuple2<NameSpace, TypeSem>, Tuple2<NameSpace, TypeSem>> nameSpaceTypeSem) {

        if (entry.getValue().stream().map(x -> x.getSyntacticUpdate().getTransformation())
                .allMatch(x -> x.equals("Add Type Parameters") || x.equals("Remove Type Parameters")
                        || x.equals("Reorder Type Parameters"))) {
            return TypeChangeStatistics.newBuilder()
                    .putAllVisibilityStats(entry.getValue().stream().collect(toMap(x -> x.getVisibility(), x -> 1.0, (x, y) -> 1.0)))
                    .putAllElementKindStats(entry.getValue().stream().collect(toMap(x -> x.getElementKindAffected().name(), x -> 1.0, (x, y) -> 1.0)))
                    .putAllTransformationStats(entry.getValue().stream().map(x -> x.getSyntacticUpdate().getTransformation()).collect(toMap(x -> x, x -> 1.0, (x, y) -> 1.0)))
                    .putNameSpaceStats(nameSpaceTypeSem._1()._1().name() + " -> " + nameSpaceTypeSem._2()._1().name(), 1.0)
                    .build();
        }

        List<Element> relevantElements = cs.getElementsList()
                .stream().filter(x -> isRelevant(prettyLIST(entry.getKey()._1()), x.getType()))
                .collect(toList());

        Map<String, Long> visibilityMap = relevantElements.stream().collect(groupingBy(x -> x.getVisibility(), counting()));
        Map<String, Long> elemKindMap = relevantElements.stream().collect(groupingBy(x -> x.getElemKind().name(), counting()));
        Map<String, Long> typeKindMap = relevantElements.stream().collect(groupingBy(x -> x.getTypeKind(), counting()));

        Map<String, Long> typeChangeVisibilityMap = entry.getValue().stream().collect(groupingBy(x -> x.getVisibility(), counting()));
        Map<String, Long> typeChangeElemKindMap = entry.getValue().stream().collect(groupingBy(x -> x.getElementKindAffected().name(), counting()));
        Map<Tuple2<String, String>, Long> transformationMap = entry.getValue().stream().map(x -> x.getSyntacticUpdate())
                .collect(groupingBy(x -> Tuple.of(x.getB4().getRoot().getKind().name(), x.getTransformation()), counting()));

        return TypeChangeStatistics.newBuilder()
                .putAllVisibilityStats(divideMaps(typeChangeVisibilityMap, visibilityMap))
                .putAllElementKindStats(divideMaps(typeChangeElemKindMap, elemKindMap))
                .putAllTransformationStats(divideMapss(transformationMap, typeKindMap))
                .putNameSpaceStats(nameSpaceTypeSem._1()._1().name() + " -> " + nameSpaceTypeSem._2()._1().name()
                        , round((double) entry.getValue().size() / relevantElements.size()))
                .build();


    }


    private Map<String, Double> divideMapss(Map<Tuple2<String, String>, Long> num, Map<String, Long> den) {
        return num.entrySet().stream()
                .map(x -> Tuple.of(x.getKey()._2(), x.getValue(), Optional.ofNullable(den.get(x.getKey()._1()))))
                .filter(x -> x._3().isPresent())
                .collect(toMap(x -> x._1(), x -> round((double) x._2() / x._3().get())));
    }


    private Map<String, Double> divideMaps(Map<String, Long> num, Map<String, Long> den) {
        return num.entrySet().stream()
                .map(x -> Tuple.of(x.getKey(), x.getValue(), Optional.ofNullable(den.get(x.getKey()))))
                .filter(x -> x._3().isPresent())
                .collect(toMap(x -> x._1(), x -> round((double) x._2() / x._3().get())));
    }


    private Optional<MigrationAnalysis> performMigrationAnalysis(TypeChangeAnalysis tca, GlobalContext gc) {
        if (tca.getB4().getRoot().getKind().equals(Simple) && tca.getAftr().getRoot().getKind().equals(Simple)) {

            if (tca.getNameSpacesB4().equals(TypeVariable) || tca.getNameSpaceAfter().equals(TypeVariable))
                return Optional.empty();

            String from = pretty(tca.getB4());

            if (gc.getAllJavaLangClasses().anyMatch(x -> x.endsWith(from)))
                return Optional.empty();

            Set<String> classesAffected = tca.getTypeChangeInstancesList().stream().map(x -> x.getCompilationUnit())
                    .collect(toSet());

            boolean isTypeLevelMigration = gc.typesUsesType(classesAffected, from).isEmpty();

            if (!isTypeLevelMigration)
                return Optional.empty();

            if (!gc.packageUsesType(classesAffected, from).isEmpty())
                return Optional.of(MigrationAnalysis.newBuilder()
                        .setType(from).setTypeMigrationLevel("Type Level Migration").build());

            if (!gc.projectUsesType(from))
                return Optional.of(MigrationAnalysis.newBuilder()
                        .setType(from).setTypeMigrationLevel("Package Level Migration").build());

            return Optional.of(MigrationAnalysis.newBuilder()
                    .setType(from).setTypeMigrationLevel("Project Level Migration").build());

        }
        return Optional.empty();
    }

    private List<String> removedImpact(DependencyUpdate du, GlobalContext gc, List<TypeChangeAnalysis> tca) {
        final List<String> typeChanges = getExternalTypesInvolved(tca);
        return getAllTypesIn(du.getRemovedList(), gc)
                .filter(x -> typeChanges.stream().anyMatch(z -> z.contains(x)))
                .collect(toList());
    }


    private List<String> updateImpact(DependencyUpdate du, GlobalContext gc, List<TypeChangeAnalysis> tca) {
        final List<String> typeChanges = getExternalTypesInvolved(tca);
        return getAllTypesIn(du.getUpdateList().stream().flatMap(x -> Stream.of(x.getBefore(), x.getAfter())).collect(toList()), gc)
                .filter(x -> typeChanges.stream().anyMatch(z -> z.contains(x)))
                .collect(toList());
    }


    private List<String> addedImpact(DependencyUpdate du, GlobalContext gc, List<TypeChangeAnalysis> tca) {
        final List<String> typeChanges = getExternalTypesInvolved(tca);
        return getAllTypesIn(du.getAddedList(), gc)
                .filter(x -> typeChanges.stream().anyMatch(z -> z.contains(x)))
                .collect(toList());
    }

    private List<String> getExternalTypesInvolved(List<TypeChangeAnalysis> tca) {
        return tca.stream().filter(x -> x.getNameSpacesB4().equals(External) || x.getNameSpaceAfter().equals(External))
                .flatMap(x -> Stream.of(x.getB4(), x.getAftr()))
                .map(x -> pretty(x))
                .collect(toList());
    }


    public Tuple2<NameSpace, TypeSem> getNameSpaceAndTypeSem(TypeGraph resolvedTypeGraph, GlobalContext gc) {
        switch (resolvedTypeGraph.getRoot().getKind()) {
            case Primitive:
            case Simple:
                return findNameSpaceTypeSemFor(resolvedTypeGraph.getRoot(), gc);

            case WildCard: {
                return (resolvedTypeGraph.getEdgesMap().get("extends") != null)
                        ? getNameSpaceAndTypeSem(resolvedTypeGraph.getEdgesMap().get("extends"), gc)
                        : ((resolvedTypeGraph.getEdgesMap().get("super") != null)
                        ? getNameSpaceAndTypeSem(resolvedTypeGraph.getEdgesMap().get("super"), gc)
                        : Tuple.of(TypeVariable, TypeSem.Object));
            }
            case Array:
                getNameSpaceAndTypeSem(resolvedTypeGraph.getEdgesMap().get("of"), gc);
            case Parameterized:
                return getNameSpaceAndTypeSem(resolvedTypeGraph.getEdgesMap().get("of"), gc);
            case Intersection:
                return getNameSpaceAndTypeSem(resolvedTypeGraph.getEdgesMap().get("Intersection:"), gc);
            case Union:
                return getNameSpaceAndTypeSem(resolvedTypeGraph.getEdgesMap().get("Union:"), gc);

            default:
                return Tuple.of(DontKnow, Dont_Know);
        }

    }


    private Tuple2<NameSpace, TypeSem> findNameSpaceTypeSemFor(TypeNode tn, GlobalContext gc) {
        if (tn.getIsTypeVariable())
            return Tuple.of(TypeVariable, Object);
        if (tn.getKind().equals(TypeNode.TypeKind.Primitive))
            return Tuple.of(Jdk, PrimitiveType);
        if (foundAsInternalClass(tn.getName(), gc))
            return Tuple.of(Internal, Object);
        else if (foundAsInternalEnum(tn.getName(), gc))
            return Tuple.of(Internal, Enum);
        else if (foundAsJdkClass(tn.getName(), gc))
            return Tuple.of(Jdk, Object);
        else if (foundAsJdkEnum(tn.getName(), gc))
            return Tuple.of(Jdk, Enum);
        else if (foundAsExternalEnum(tn.getName(), gc))
            return Tuple.of(External, Enum);
        else if (foundAsExternalClass(tn.getName(), gc))
            return Tuple.of(External, Object);
        else if (gc.getAllInternalPackages().anyMatch(p -> pretty(tn).contains(p)))
            return Tuple.of(Internal, Dont_Know);
        return Tuple.of(DontKnow, Dont_Know);
    }

    private boolean foundAsInternalClass(String name, GlobalContext gc) {
        return gc.getClassesInternal().stream().anyMatch(c -> c.contains(name));
    }

    private boolean foundAsInternalEnum(String name, GlobalContext gc) {
        return gc.getEnumsInternal().stream().anyMatch(c -> c.contains(name));
    }

    private boolean foundAsJdkClass(String name, GlobalContext gc) {
        return gc.getAllJavaClasses().anyMatch(c -> c.contains(name));
    }

    private boolean foundAsJdkEnum(String name, GlobalContext gc) {
        return gc.getJavaEnums().anyMatch(c -> c.contains(name));
    }

    private boolean foundAsExternalEnum(String name, GlobalContext gc) {
        var t = gc.getRequiredJars().stream()
                .map(x -> getDependencyInfo(gc.getPathToJars(), x))
                .flatMap(d -> d.toJavaStream())
                .flatMap(d -> d._2().getEnums().getNamesList().stream().map(x -> Tuple.of(x, d._1())))
                .filter(c -> c._1().contains(name))
                .findFirst();
        t.ifPresent(stringJarInfoTuple2 -> jarsRequired.add(stringJarInfoTuple2._2()));
        return t.isPresent();
    }

    private boolean foundAsExternalClass(String name, GlobalContext gc) {
        var t = gc.getRequiredJars().stream()
                .map(x -> getDependencyInfo(gc.getPathToJars(), x))
                .flatMap(d -> d.toJavaStream())
                .flatMap(d -> d._2().getHierarchicalInfoMap().keySet().stream().map(x -> Tuple.of(x, d._1())))
                .filter(c -> c._1().contains(name)).findFirst();

        t.ifPresent(stringJarInfoTuple2 -> jarsRequired.add(stringJarInfoTuple2._2()));
        return t.isPresent();
    }

    private Stream<String> getAllTypesIn(List<JarInfo> js, GlobalContext gc) {
        return js.stream().map(x -> getDependencyInfo(gc.getPathToJars(), x))
                .flatMap(d -> d.toJavaStream())
                .flatMap(d -> concat(d._2().getHierarchicalInfoMap().keySet().stream()
                        , d._2().getEnums().getNamesList().stream()));
    }


}
