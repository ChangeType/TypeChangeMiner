import com.google.common.collect.ImmutableMap;
import com.t2r.common.models.refactorings.ProcessedCodeMappingsOuterClass.ProcessedCodeMappings.ExpressionMapping;
import com.t2r.common.models.refactorings.TypeChangeAnalysisOuterClass.TypeChangeAnalysis;
import com.t2r.common.models.refactorings.TypeSemOuterClass;
import io.vavr.Tuple2;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.t2r.common.models.ast.TypeNodeOuterClass.TypeNode.TypeKind.Primitive;
import static com.t2r.common.models.ast.TypeNodeOuterClass.TypeNode.TypeKind.WildCard;
import static com.t2r.common.utilities.PrettyPrinter.pretty;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class Labellers {


    public static ImmutableMap<String, String> editPatternMAp = ImmutableMap.<String, String>builder()
            .put("METHOD_RECEIVER", "\\percentModifyReveiver")
            .put("ADD METHOD INVOCATION", "\\percentAddRemoveMthdInvc")
            .put("METHOD_INVOCATION_NAME", "\\percentMthdRename")
            .put("VARIABLE_REPLACED_WITH_NULL_LITERAL", "\\percentIntroduceLiteral")
            .put("ADD null expression", "\\percentIntroduceLiteral")
            .put("BOOLEAN_REPLACED_WITH_VARIABLE", "\\percentIntroduceLiteral")
            .put("CLASS_INSTANCE_CREATION_REPLACED_WITH_METHOD_INVOCATION", "\\percentClsInCrToMthdInvc")
            .put("VARIABLE_REPLACED_WITH_METHOD_INVOCATION", "\\percentAddRemoveMthdInvc")
            .put("ANONYMOUS_CLASS_DECLARATION", "\\percentUpdateAnonymCls")
            .put("VARIABLE_REPLACED_WITH_STRING_LITERAL", "\\percentIntroduceLiteral")
            .put("METHOD_INVOCATION_ARGUMENT", "\\percentMthdInvcArgsUpdate")
            .put("BUILDER_REPLACED_WITH_CLASS_INSTANCE_CREATION", "\\percentClsInCrToMthdInvc")
            .put("CLASS_INSTANCE_CREATION_NAME", "\\percentCascadingTypeDifferent")
            .put("ADD boolean expression", "\\percentIntroduceLiteral")
            .put("METHOD_INVOCATION_ARGUMENTS", "\\percentMthdInvcArgsUpdate")
            .put("UPDATE METHOD_INVOCATION_RECEIVER", "\\percentModifyReveiver")
            .put("ADD CAST", "\\percentCast")
            .put("Un CAST", "\\percentCast")
            .put("ADD METHOD_ARGUMENT","\\percentMthdInvcArgsUpdate")
            .put("ADD METHOD_INVOCATION_ARGUMENTS", "\\percentMthdInvcArgsUpdate")
            .put("ADD METHOD_ARGUMEN", "\\percentMthdInvcArgsUpdate")
            .put("REMOVE METHOD_INVOCATION_ARGUMENTS", "\\percentMthdInvcArgsUpdate")
            .put("REMOVE METHOD INVOCATION", "\\percentAddRemoveMthdInvc")
            .put("UN-WRAP", "\\percentWrap")
            .put("WRAP_WITH_METHOD_INVOCATION", "\\percentWrap")
            .put("METHOD_INVOCATION_ARGUMENT_WRAPPED", "\\percentWrap")
            .put("METHOD_INVOCATION_NAME_AND_ARGUMENT", "\\percentMthdRename")
            .put("CLASS_INSTANCE_CREATION", "\\percentClsInstCr")
            .put("METHOD_INVOCATION_EXPRESSION", "\\percentMthdRename")
            .put("NUMBER_LITERAL", "Update Num Literal")
            .put("STRING_LITERAL", "Update String Literal")
            .put("CLASS_INSTANCE_CREATION_ARGUMENT", "\\percentClsInsCrArgsUpdate")
            .put("METHOD_INVOCATION", "\\percentAddRemoveMthdInvc")
            .put("TYPE", "\\percentCascadingType")
            .put("TYPE-Different","\\percentCascadingTypeDifferent")
            .put("VARIABLE-RENAME","\\percentVarRename")
            .build();

    static Function<List<TypeChangeAnalysis>, String> TCALabeller = tca -> {

        if (tca.isEmpty()) return "No TCA found !!! ";


        String prettyB4 = pretty(tca.stream().findFirst().get().getB4());
        String prettyAfter = pretty(tca.stream().findFirst().get().getAftr());

        if (prettyAfter.equals("var"))
            return "INTRODUCE VAR";

        if (prettyB4.equals("void") || prettyAfter.equals("void"))
            return "TO_FROM_VOID";

        if (prettyB4.equals("java.lang.Object"))
            return "T_SUPER_R";

        if (prettyAfter.equals("java.lang.Object"))
            return "R_SUPER_T";

        return tca.stream()
                .filter(TypeChangeAnalysis::hasPrimitiveInfo).flatMap(p -> {
                    if (p.getPrimitiveInfo().getNarrowing())
                        return Stream.of("NARROW");
                    if (p.getPrimitiveInfo().getWidening())
                        return Stream.of("WIDEN");
                    if (p.getPrimitiveInfo().getBoxing() || p.getPrimitiveInfo().getUnboxing())
                        return Stream.of("BOX-UnBOx");
                    return Stream.empty();
                }).findFirst()
                .or(() -> tca.stream()
                        .filter(x -> x.getTypeSemAftr().equals(TypeSemOuterClass.TypeSem.Enum) || x.getTypeSemb4().equals(TypeSemOuterClass.TypeSem.Enum))
                        .findFirst().map(x -> "TO-FROM-ENUM"))
                .or(() -> tca.stream()
                        .filter(TypeChangeAnalysis::getB4ComposesAfter)
                        .filter(x -> !x.getB4().getRoot().getKind().equals(Primitive))
                        .filter(x -> !x.getAftr().getRoot().getKind().equals(Primitive))
                        .filter(x -> !pretty(x.getB4()).equals("java.lang.String"))
                        .filter(x -> !pretty(x.getAftr()).equals("java.lang.String"))
                        .filter(x -> !pretty(x.getB4()).equals("java.util.UUID"))
                        .filter(x -> !pretty(x.getAftr()).equals("java.util.UUID"))
                        .findFirst().map(x -> "COMPOSITION"))
                .or(() -> tca.stream()
                        .filter(x -> x.getHierarchyRelation() != null && !x.getHierarchyRelation().isEmpty() && !x.getHierarchyRelation().contains("NO"))
                        .findFirst().map(TypeChangeAnalysis::getHierarchyRelation))
                .or(() -> tca.stream()
                        .filter(x -> x.getB4().getRoot().getKind().equals(WildCard) || x.getAftr().getRoot().getKind().equals(WildCard))
                        .findFirst().map(x -> "WILD_CARD"))
                .or(() -> tca.stream()
                        .filter(x -> x.getTypeChangeInstancesList().stream().anyMatch(zz -> zz.getSyntacticUpdate().getTransformation().equals("Add Type Parameters")
                                || zz.getSyntacticUpdate().getTransformation().equals("Remove Type Parameters")))
                        .findFirst().map(x -> "Add Remove Type Param"))
                .orElse("Oops");

    };

    static List<ExpressionMapping> processRawActions(List<Tuple2<String, String>> rawActions) {
        List<ExpressionMapping> actions = new ArrayList<>();

        if (rawActions.stream().anyMatch(x -> x._1().equals(""))) {
            System.out.println();
        }

        var wrap_pattern1 = asList("insert-tree METHOD_INVOCATION_RECEIVER MethodInvocation"
                , "insert-node SimpleName MethodInvocation", "insert-node MethodInvocation METHOD_INVOCATION_ARGUMENTS");
        if (contains(rawActions, wrap_pattern1)) {
            actions.add(ExpressionMapping.newBuilder().setAftr(getValue(rawActions, "insert-node SimpleName MethodInvocation"))
                    .setReplacement("WRAP_WITH_METHOD_INVOCATION").build());
            rawActions = remove(rawActions, wrap_pattern1);
        }
        var wrap_pattern2 = asList("insert-tree METHOD_INVOCATION_RECEIVER MethodInvocation"
                , "insert-node SimpleName MethodInvocation", "insert-tree MethodInvocation METHOD_INVOCATION_ARGUMENTS");
        if (contains(rawActions, wrap_pattern2)) {
            actions.add(ExpressionMapping.newBuilder()
                    .setAftr(getValue(rawActions, "insert-node SimpleName MethodInvocation"))
                    .setReplacement("WRAP_WITH_METHOD_INVOCATION").build());
            rawActions = remove(rawActions, wrap_pattern2);
        }
        var wrap_pattern3 = asList("insert-tree METHOD_INVOCATION_RECEIVER MethodInvocation"
                , "insert-node SimpleName MethodInvocation", "insert-node ClassInstanceCreation METHOD_INVOCATION_ARGUMENTS");
        if (contains(rawActions, wrap_pattern3)) {
            actions.add(ExpressionMapping.newBuilder()
                    .setAftr(getValue(rawActions, "insert-node SimpleName MethodInvocation"))
                    .setReplacement("WRAP_WITH_METHOD_INVOCATION").build());
            rawActions = remove(rawActions, wrap_pattern3);
        }
        var del_cls_creation = asList("delete-tree SimpleType ClassInstanceCreation", "delete-node SimpleName ClassInstanceCreation");
        if (contains(rawActions, del_cls_creation)) {
            actions.add(ExpressionMapping.newBuilder()
                    .setB4(getValue(rawActions, "delete-node SimpleName ClassInstanceCreation"))
                    .setReplacement("REMOVE_CLASS_INSTANCE_CREATION").build());
            rawActions = remove(rawActions, del_cls_creation);
        }
        if (contains(rawActions, singletonList("update-node SimpleName MethodInvocation"))) {

            if (contains(rawActions, asList("insert-node MethodInvocation METHOD_INVOCATION_RECEIVER", "insert-node METHOD_INVOCATION_RECEIVER MethodInvocation", "insert-node SimpleName MethodInvocation"))) {
                actions.add(ExpressionMapping.newBuilder()
                        .setB4(getValue(rawActions, "insert-node SimpleName MethodInvocation"))
                        .setAftr( getValue(rawActions, "insert-node MethodInvocation METHOD_INVOCATION_RECEIVER"))
                        .setReplacement("ADD METHOD INVOCATION").build());
                rawActions = remove(rawActions, asList("insert-node MethodInvocation METHOD_INVOCATION_RECEIVER", "insert-node METHOD_INVOCATION_RECEIVER MethodInvocation", "insert-node SimpleName MethodInvocation"));

            } else if (containsStrtWith(rawActions, asList("delete-node MethodInvocation", "delete-node SimpleName"))) {
                actions.add(ExpressionMapping.newBuilder()
                        .setB4(getValueStrtsWith(rawActions, "delete-node SimpleName"))
                        .setReplacement("REMOVE METHOD INVOCATION").build());
                rawActions = removeStrtWith(rawActions, asList("delete-node MethodInvocation", "delete-node SimpleName"));

            } else {
                actions.add(ExpressionMapping.newBuilder()
                        .setB4(getValue(rawActions, "update-node SimpleName MethodInvocation"))
                        .setAftr(getValue(rawActions, "insert-node SimpleName MethodInvocation"))
                        .setReplacement("METHOD_INVOCATION_NAME").build());
            }
            rawActions = remove(rawActions, asList("update-node SimpleName MethodInvocation"));
        }
        if (contains(rawActions, singletonList("update-node SimpleName ClassInstanceCreation"))) {
            actions.add(ExpressionMapping.newBuilder()
                    .setB4(getValue(rawActions, "update-node SimpleName ClassInstanceCreation"))
                    .setAftr(getValue(rawActions, "insert-node SimpleName ClassInstanceCreation"))
                    .setReplacement("CLASS_INSTANCE_CREATION_NAME").build());
            rawActions = remove(rawActions, asList("update-node SimpleName ClassInstanceCreation", "insert-node SimpleName ClassInstanceCreation"));
        }


        if (contains(rawActions, asList("insert-tree METHOD_INVOCATION_RECEIVER MethodInvocation", "insert-node SimpleName MethodInvocation", "insert-tree METHOD_INVOCATION_ARGUMENTS MethodInvocation"))) {
            actions.add(ExpressionMapping.newBuilder()
                    .setAftr(getValue(rawActions, "insert-node SimpleName MethodInvocation"))
                    .setReplacement("ADD METHOD INVOCATION").build());
            rawActions = remove(rawActions, asList("insert-tree METHOD_INVOCATION_RECEIVER MethodInvocation", "insert-node SimpleName MethodInvocation", "insert-tree METHOD_INVOCATION_ARGUMENTS MethodInvocation"));
        }

        if (contains(rawActions, asList("insert-tree METHOD_INVOCATION_RECEIVER MethodInvocation", "insert-node SimpleName MethodInvocation"))) {
            actions.add(ExpressionMapping.newBuilder()
                    .setAftr(getValue(rawActions, "insert-node SimpleName MethodInvocation"))
                    .setReplacement("ADD METHOD INVOCATION").build());
            rawActions = remove(rawActions, asList("insert-tree METHOD_INVOCATION_RECEIVER MethodInvocation", "insert-node SimpleName MethodInvocation"));
        }

        if (contains(rawActions, singletonList("delete-node METHOD_INVOCATION_ARGUMENTS MethodInvocation"))) {
            if (contains(rawActions, asList("delete-node SimpleName MethodInvocation", "delete-tree METHOD_INVOCATION_RECEIVER MethodInvocation"))) {
                actions.add(ExpressionMapping.newBuilder()
                        .setAftr(getValue(rawActions, "delete-node SimpleName MethodInvocation"))
                        .setReplacement("UN-WRAP").build());
                rawActions = remove(rawActions, asList("delete-node SimpleName MethodInvocation", "delete-tree METHOD_INVOCATION_RECEIVER MethodInvocation"));
            } else {
                actions.add(ExpressionMapping.newBuilder()
                        .setAftr(getValue(rawActions, "delete-node METHOD_INVOCATION_ARGUMENTS MethodInvocation"))
                        .setReplacement("METHOD_INVOCATION_ARGUMENTS").build());
            }
            rawActions = remove(rawActions, singletonList("delete-node METHOD_INVOCATION_ARGUMENTS MethodInvocation"));
        }

        if (contains(rawActions, singletonList("delete-tree METHOD_INVOCATION_ARGUMENTS MethodInvocation"))) {
            if (contains(rawActions, asList("delete-node SimpleName MethodInvocation", "delete-tree METHOD_INVOCATION_RECEIVER MethodInvocation"))) {
                actions.add(ExpressionMapping.newBuilder()
                        .setAftr(getValue(rawActions, "delete-node SimpleName MethodInvocation"))
                        .setReplacement("UN-WRAP").build());
                rawActions = remove(rawActions, asList("delete-node SimpleName MethodInvocation", "delete-tree METHOD_INVOCATION_RECEIVER MethodInvocation"));
            } else {
                actions.add(ExpressionMapping.newBuilder()
                        .setAftr(getValue(rawActions, "delete-tree METHOD_INVOCATION_ARGUMENTS MethodInvocation"))
                        .setReplacement("METHOD_INVOCATION_ARGUMENTS").build());
            }
            rawActions = remove(rawActions, singletonList("delete-tree METHOD_INVOCATION_ARGUMENTS MethodInvocation"));
        }

        if (contains(rawActions, singletonList("delete-node METHOD_INVOCATION_RECEIVER MethodInvocation"))) {
            if (contains(rawActions, asList("delete-node SimpleName MethodInvocation", "delete-tree METHOD_INVOCATION_RECEIVER MethodInvocation"))) {
                actions.add(ExpressionMapping.newBuilder()
                        .setB4(getValue(rawActions, "delete-node SimpleName MethodInvocation"))
                        .setReplacement("VARIABLE_REPLACED_WITH_METHOD_INVOCATION").build());
                rawActions = remove(rawActions, asList("delete-node SimpleName MethodInvocation", "delete-tree METHOD_INVOCATION_RECEIVER MethodInvocation"));
            } else if (contains(rawActions, singletonList("delete-node MethodInvocation METHOD_INVOCATION_RECEIVER"))) {
                actions.add(ExpressionMapping.newBuilder()
                        .setB4(getValue(rawActions, "delete-node METHOD_INVOCATION_RECEIVER MethodInvocation"))
                        .setReplacement("VARIABLE_REPLACED_WITH_METHOD_INVOCATION").build());
                rawActions = remove(rawActions, singletonList("delete-node MethodInvocation METHOD_INVOCATION_RECEIVER"));
            } else if (containsStrtWith(rawActions, singletonList("delete-node MethodInvocation"))) {
                actions.add(ExpressionMapping.newBuilder()
                        .setB4(getValue(rawActions, "delete-node SimpleName MethodInvocation"))
                        .setReplacement("VARIABLE_REPLACED_WITH_METHOD_INVOCATION").build());
                rawActions = removeStrtWith(rawActions, singletonList("delete-node MethodInvocation"));
            } else {
                actions.add(ExpressionMapping.newBuilder()
                        .setB4(getValue(rawActions, "delete-node METHOD_INVOCATION_RECEIVER MethodInvocation"))
                        .setReplacement("METHOD_RECEIVER").build());
            }
            rawActions = remove(rawActions, singletonList("delete-node METHOD_INVOCATION_RECEIVER MethodInvocation"));
        }
        if (contains(rawActions, asList("delete-node SimpleName MethodInvocation", "delete-tree METHOD_INVOCATION_RECEIVER MethodInvocation"))) {
            actions.add(ExpressionMapping.newBuilder()
                    .setB4(getValue(rawActions, "delete-node SimpleName MethodInvocation"))
                    .setReplacement("VARIABLE_REPLACED_WITH_METHOD_INVOCATION").build());
            rawActions = remove(rawActions, asList("delete-node SimpleName MethodInvocation", "delete-tree METHOD_INVOCATION_RECEIVER MethodInvocation"));
        }
        if (contains(rawActions, asList("insert-node MethodInvocation METHOD_INVOCATION_ARGUMENTS", "insert-node METHOD_INVOCATION_RECEIVER MethodInvocation"))) {
            actions.add(ExpressionMapping.newBuilder()
                    .setB4(getValue(rawActions, "insert-node METHOD_INVOCATION_RECEIVER MethodInvocation"))
                    .setReplacement("VARIABLE_REPLACED_WITH_METHOD_INVOCATION").build());
            rawActions = remove(rawActions, asList("insert-node MethodInvocation METHOD_INVOCATION_ARGUMENTS", "insert-node METHOD_INVOCATION_RECEIVER MethodInvocation"));
        }
        if (contains(rawActions, asList("insert-node METHOD_INVOCATION_RECEIVER MethodInvocation", "insert-node MethodInvocation METHOD_INVOCATION_RECEIVER"))) {
            actions.add(ExpressionMapping.newBuilder()
                    .setB4(getValue(rawActions, "delete-node SimpleName MethodInvocation"))
                    .setReplacement("VARIABLE_REPLACED_WITH_METHOD_INVOCATION").build());
            rawActions = remove(rawActions, asList("insert-node METHOD_INVOCATION_RECEIVER MethodInvocation", "insert-node MethodInvocation METHOD_INVOCATION_RECEIVER"));
        }
        if (contains(rawActions, singletonList("insert-node METHOD_INVOCATION_ARGUMENTS MethodInvocation"))) {
            actions.add(ExpressionMapping.newBuilder()
                    .setAftr(getValue(rawActions, "insert-node METHOD_INVOCATION_ARGUMENTS MethodInvocation"))
                    .setReplacement("ADD METHOD_INVOCATION_ARGUMENTS").build());
            rawActions = remove(rawActions, singletonList("insert-node METHOD_INVOCATION_ARGUMENTS MethodInvocation"));
        }
        if (contains(rawActions, singletonList("insert-node SimpleName METHOD_INVOCATION_ARGUMENTS"))) {
            actions.add(ExpressionMapping.newBuilder()
                    .setAftr(getValue(rawActions, "insert-node SimpleName METHOD_INVOCATION_ARGUMENTS"))
                    .setReplacement("ADD METHOD_INVOCATION_ARGUMENTS").build());
            rawActions = remove(rawActions, singletonList("insert-node SimpleName METHOD_INVOCATION_ARGUMENTS"));
        }
        if (contains(rawActions, singletonList("update-node SimpleName METHOD_INVOCATION_RECEIVER"))) {
            actions.add(ExpressionMapping.newBuilder()
                    .setAftr(getValue(rawActions, "update-node SimpleName METHOD_INVOCATION_RECEIVER"))
                    .setReplacement("UPDATE METHOD_INVOCATION_RECEIVER").build());
            rawActions = remove(rawActions, singletonList("update-node SimpleName METHOD_INVOCATION_RECEIVER"));
        }
        if (contains(rawActions, singletonList("insert-tree METHOD_INVOCATION_ARGUMENTS MethodInvocation"))) {
            actions.add(ExpressionMapping.newBuilder()
                    .setAftr(getValue(rawActions, "insert-tree METHOD_INVOCATION_ARGUMENTS MethodInvocation"))
                    .setReplacement("ADD METHOD_INVOCATION_ARGUMENTS").build());
            rawActions = remove(rawActions, singletonList("insert-tree METHOD_INVOCATION_ARGUMENTS MethodInvocation"));
        }
        if (contains(rawActions, singletonList("delete-node SimpleName METHOD_INVOCATION_ARGUMENTS"))) {
            actions.add(ExpressionMapping.newBuilder()
                    .setAftr(getValue(rawActions, "delete-node SimpleName METHOD_INVOCATION_ARGUMENTS"))
                    .setReplacement("REMOVE METHOD_INVOCATION_ARGUMENTS").build());
            rawActions = remove(rawActions, singletonList("delete-node SimpleName METHOD_INVOCATION_ARGUMENTS"));

        }
        if (contains(rawActions, singletonList("delete-node METHOD_INVOCATION_RECEIVER MethodInvocation"))) {
            actions.add(ExpressionMapping.newBuilder()
                    .setAftr(getValue(rawActions, "delete-node METHOD_INVOCATION_RECEIVER MethodInvocation"))
                    .setReplacement("METHOD_RECEIVER").build());
            rawActions = remove(rawActions, singletonList("delete-node METHOD_INVOCATION_RECEIVER MethodInvocation"));
        }

        if (contains(rawActions, singletonList("update-node SimpleName MethodInvocation"))) {

            if (containsStrtWith(rawActions, asList("insert-node MethodInvocation", "insert-node METHOD_INVOCATION_RECEIVER", "insert-node SimpleName"))) {
                actions.add(ExpressionMapping.newBuilder()
                        .setAftr(getValueStrtsWith(rawActions, "insert-node SimpleName "))
                        .setReplacement("ADD METHOD INVOCATION").build());
                rawActions = removeStrtWith(rawActions, asList("insert-node MethodInvocation", "insert-node METHOD_INVOCATION_RECEIVER", "insert-node SimpleName"));
            } else if (containsStrtWith(rawActions, asList("delete-node MethodInvocation", "delete-node SimpleName"))) {
                actions.add(ExpressionMapping.newBuilder()
                        .setAftr(getValueStrtsWith(rawActions, "delete-node SimpleName"))
                        .setReplacement("REMOVE METHOD INVOCATION").build());
                rawActions = removeStrtWith(rawActions, asList("delete-node MethodInvocation", "delete-node SimpleName"));
            } else {
                actions.add(ExpressionMapping.newBuilder()
                        .setB4(getValueStrtsWith(rawActions,"update-node SimpleName MethodInvocation"))
                        .setAftr(getValueStrtsWith(rawActions, "insert-node SimpleName MethodInvocation"))
                        .setReplacement("METHOD_INVOCATION_NAME").build());
                rawActions = remove(rawActions, asList("update-node SimpleName MethodInvocation", "insert-node SimpleName MethodInvocation"));
            }
        }


        if (containsStrtWith(rawActions, singletonList("delete-node CastExpression"))) {
            actions.add(ExpressionMapping.newBuilder()
                    .setB4(getValueStrtsWith(rawActions,"delete-node CastExpression"))
                    .setReplacement("Un CAST").build());
            rawActions = removeStrtWith(rawActions, singletonList("delete-node CastExpression"));
        }

        if (containsStrtWith(rawActions, singletonList("insert-node CastExpression"))) {
            actions.add(ExpressionMapping.newBuilder()
                    .setB4(getValueStrtsWith(rawActions,"insert-node CastExpression"))
                    .setReplacement("ADD CAST").build());
            rawActions = removeStrtWith(rawActions, singletonList("insert-node CastExpression"));
        }

        if (containsStrtWith(rawActions, singletonList("insert-node NullLiteral"))) {
            actions.add(ExpressionMapping.newBuilder()
                    .setB4(getValueStrtsWith(rawActions,"insert-node NullLiteral"))
                    .setReplacement("ADD null expression").build());
            rawActions = removeStrtWith(rawActions, singletonList("insert-node NullLiteral"));
        }
        if (containsStrtWith(rawActions, singletonList("insert-node BooleanLiteral"))) {
            actions.add(ExpressionMapping.newBuilder()
                    .setB4(getValueStrtsWith(rawActions,"insert-node BooleanLiteral"))
                    .setReplacement("ADD boolean expression").build());
            rawActions = removeStrtWith(rawActions, singletonList("insert-node BooleanLiteral"));
        }
        return actions;
    }

    private static boolean contains(List<Tuple2<String, String>> list, List<String> smallList) {
        var l = list.stream().map(Tuple2::_1).collect(toList());
        return l.containsAll(smallList);
    }

    private static boolean containsStrtWith(List<Tuple2<String, String>> list, List<String> smallList) {
        var l = list.stream().map(Tuple2::_1).collect(toList());
        return l.stream().anyMatch(x -> smallList.stream().anyMatch(x::startsWith));
    }

    private static String getValue(List<Tuple2<String, String>> list, String key) {
        return list.stream().filter(x -> x._1().equals(key)).findFirst()
                .map(Tuple2::_2)
                .orElse("");
    }

    private static String getValueStrtsWith(List<Tuple2<String, String>> list, String key) {
        return list.stream().filter(x -> x._1().startsWith(key)).findFirst()
                .map(Tuple2::_2)
                .orElse("");
    }

    private static List<Tuple2<String, String>> remove
            (List<Tuple2<String, String>> list, List<String> smallList) {
        return list.stream()
                .filter(x -> !smallList.contains(x._1()))
                .collect(toList());
    }

    private static List<Tuple2<String, String>> removeStrtWith
            (List<Tuple2<String, String>> list, List<String> smallList) {
        return list.stream()
                .filter(x -> !smallList.stream().anyMatch(s -> x._1().startsWith(s)))
                .collect(toList());
    }
}
