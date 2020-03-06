package TFGStuff;

import com.google.common.collect.Streams;
import com.t2r.common.models.ast.IdentificationOuterClass.Identification;
import com.t2r.common.models.refactorings.TypeChangeAnalysisOuterClass.TypeChangeAnalysis.CodeMapping;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static TFGStuff.Tree2State.*;
import static TFGStuff.Utils.*;
import static java.util.stream.Collectors.toList;

public class MappingObj {

    public static final Identification defaultId = Identification.getDefaultInstance();
    private final Tuple2<String, String> statements;
    private final Tuple2<Identification, Identification> varId;
    private final Tuple2<State, State> relevantExprStates;
    private Tuple2<String, String> relevantExprs;

    public MappingObj(CodeMapping codeMapping, B4After<String> varNames) {
        this.statements = Tuple.of(codeMapping.getB4(), codeMapping.getAfter());
        Tuple2<State, State> states = generateState(statements);
        this.varId = states.map(e1 -> searchForIdInState(e1, varNames.getB4()), e2 -> searchForIdInState(e2, varNames.getAftr()));
        this.relevantExprs = !anyNull(varId) ? matters(states._1(), states._2(), varId._1(), varId._2())
                : (anyNull(states) ? nullTuple2() : Tuple.of(states._1.getRoot().toString(), states._2().getRoot().toString()));
        this.relevantExprStates = anyNull(relevantExprs) ? nullTuple2() : generateState(relevantExprs);
    }

    public Tuple2<State, State> generateState(Tuple2<String, String> codeAsString) {
        Tuple2<ASTNode, ASTNode> stmsAST = codeAsString.map(Utils::getASTNode, Utils::getASTNode);
        if (replaceIfExists(replaceIfExists(replaceIfExists(stmsAST._1().toString(), "\n", ""), " ", ""),"{}","").isEmpty()) {
            System.out.println("Could not generate AST ");
            return nullTuple2();
        }
        if (replaceIfExists(replaceIfExists(replaceIfExists(stmsAST._2().toString(), "\n", ""), " ", ""), "{}","").isEmpty()) {
            System.out.println("Could not generate AST  ");
            return nullTuple2();
        }
        Tuple2<State, State> statess = stmsAST.map(x -> getState(x, defaultId, true), x -> getState(x, defaultId, false));
        if (anyNull(statess)) {
            System.out.println("Could not figure out state for : "
                    + (statess._1() == null ? codeAsString._1() : " ") + (statess._2() == null ? codeAsString._2() : ""));
            return nullTuple2();
        }
        return statess;
    }


    public Optional<Tuple2<String, String>> getRelevantExprs() {
        return isNullTuple(relevantExprs) ? Optional.empty() : Optional.of(relevantExprs);
    }

    public Expression getRelevantExprs(boolean ba) {
        return (ba ? (Expression) getASTNodeExpr(relevantExprs._1()) : (Expression) getASTNodeExpr(relevantExprs._2()));
    }

    public State getRelevantExpressionStates(boolean ba) {
        return ba ? relevantExprStates._1() : relevantExprStates._2();
    }

    public boolean shouldCreateExample() {
        return !isNullTuple(relevantExprStates);
    }

    public State getRelevantStatesAftr() {
        return relevantExprStates._2();
    }


    public static String getRHS(State s) {
        return s.getTypeDependents().stream().filter(x -> x._2().equals("ASSIGNMENT")).findFirst()
                .map(x -> x._1().getRoot().toString()).orElse(s.getRoot().toString());
    }

    public static Tuple2<String, String> matters(State s1, State s2, Identification i1, Identification i2) {
        try {
            // variable of type T has been assigned to something
            // and the RHS changed
            if (areSame(s1.getRootID(), i1) && areSame(s2.getRootID(), i2))
                return Tuple.of(getRHS(s1), getRHS(s2));

            if (s1.getRootID().equals(s2.getRootID())) {
                return getSubStateContaining(s1, i1).flatMap(s11 -> getSubStateContaining(s2, i2).map(
                        s22 -> {
                            if (!areEqual(s11._1(), s22._1(), Tuple.of(i1.getName(), i2.getName())))
                                return matters(s11._1(), s22._1(), i1, i2);
                            else
                                return s11._2().equals(RECEIVER) && s22._2().equals(RECEIVER)
                                        ? Tuple.of(s1.getRoot().toString(), s2.getRoot().toString())
                                        : (s11._2().equals(ASSIGNMENT) && s22._2().equals(ASSIGNMENT)
                                        ? matters(s11._1(), s22._1(), i1, i2)
                                        : Tuple.of(s11._1().getRoot().toString(), s22._1().getRoot().toString()));
                        })).orElse(Tuple.of(s1.getRoot().toString(), s2.getRoot().toString()));
            }

            return Tuple.of(s1.getRoot().toString(), s2.getRoot().toString());
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Could not figure out for : " + s1.getRoot().toString() + " " + s2.getRoot().toString());
            return Tuple.of("", "");
        }
    }

    public static boolean areEqual(State s1, State s2, Tuple2<String, String> rename) {
        return areEqual(s1.getRootID(), s2.getRootID(), rename)
                && ((s1.getTypeDependents().isEmpty()
                && (s2.getTypeDependents().isEmpty()))
                || (s1.getTypeDependents().size() == s2.getTypeDependents().size()
                && Streams.zip(s1.getTypeDependents().stream(), s2.getTypeDependents().stream(), (x, y) -> x._2().equals(y._2()) && x._1().equals(y._1())).allMatch(x -> x)));
    }

    public static boolean areEqual(Identification i1, Identification i2, Tuple2<String, String> varName) {
        return i1.getKind().equals(i2.getKind()) && i1.getName() != null && i1.getName().equals(varName._1()) && i2.getName() != null && i2.getName().equals(varName._2());
    }


    public static Optional<Tuple2<State, String>> getSubStateContaining(State s, Identification i) {
        return s.getTypeDependents().stream()
                .filter(x -> getAllIds(x._1()).stream().anyMatch(id -> id.getName() != null && id.getName().equals(i.getName()) && id.getKind().equals(i.getKind())))
                .findFirst();
    }

    public static List<Identification> getAllIds(State s) {
        if (s == null)
            return new ArrayList<>();
        return Stream.concat(Stream.of(s.getRootID())
                , s.getTypeDependents().stream().flatMap(x -> getAllIds(x._1()).stream())).collect(toList());
    }


    public static Identification searchForIdInState(State s, String n) {
        if (s == null || s.getRootID() == null)
            return null;
        try {
            return s.getRootID().getName() != null && s.getRootID().getName().equals(n)
                    ? s.getRootID()
                    : s.getTypeDependents().stream().filter(x -> x._1() != null).flatMap(x -> Stream.ofNullable(searchForIdInState(x._1(), n)))
                    .findFirst().orElse(null);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Could not figure out ID for: " + n + " in " + s.getRoot().toString());
            return null;
        }
    }

//    public static String getTypeName(TypeSignature t) {
//        if (t.hasTypeSign() && t.getTypeSign().hasOf())
//            return t.getTypeSign().getOf().getInterfaceName();
//        return "";
//    }

    public static boolean areSame(Identification i1, Identification i2) {
        return i1.getName() != null && i2.getName() != null && i1.getName().equals(i2.getName())
                && i1.getKind().equals(i2.getKind());
    }


}
