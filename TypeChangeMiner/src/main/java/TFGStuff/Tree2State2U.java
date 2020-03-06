package TFGStuff;

import com.t2r.common.models.ast.IdentificationOuterClass.Identification;
import org.eclipse.jdt.core.dom.*;

import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import static TFGStuff.Tree2State.getState;


public class Tree2State2U<U> extends ASTVisitor {

    private final Identification id;
    private U result;
    private final U empty;
    private final BinaryOperator<U> combiner;
    private final Function<State, U> translator;
//    private final TypeInformation t;
    boolean b4;
//    MiningContext context;

    public Tree2State2U(U empty, BinaryOperator<U> combiner, Function<State, U> translator, Identification id) {
        this.empty = empty;
        this.combiner = combiner;
        this.translator = translator;
        this.id = id;
//        this.t = t;
    }

    @Override
    public boolean visit(SimpleName n) {
        result =  translator.apply(getState(n, id, b4));
        return false;
    }

    @Override
    public boolean visit(Block n) {
        List<Statement> sts = n.statements();
        result = sts.stream().map(x->translator.apply(getState(n, id, b4))).reduce(empty,combiner);
        return false;
    }

    @Override
    public boolean visit(ClassInstanceCreation m) {
        result =  translator.apply(getState(m, id, b4));
        return false;
    }

    @Override
    public boolean visit(ThrowStatement m) {
        result =  translator.apply(getState(m, id, b4));
        return false;
    }

    @Override
    public boolean visit(ReturnStatement m) {
        result =  translator.apply(getState(m, id, b4));
        return false;
    }

    @Override
    public boolean visit(ThisExpression m) {
        result =  translator.apply(getState(m, id, b4));
        return false;
    }

    @Override
    public boolean visit(MethodInvocation m) {
        result =  translator.apply(getState(m, id, b4));
        return false;
    }

    @Override
    public boolean visit(DoStatement m) {
        result =  translator.apply(getState(m, id, b4));
        return false;
    }

    @Override
    public boolean visit(NumberLiteral m) {
        result =  translator.apply(getState(m, id, b4));
        return false;
    }

    @Override
    public boolean visit(VariableDeclarationExpression m) {
        result =  translator.apply(getState(m, id, b4));
        return false;
    }
    @Override
    public boolean visit(VariableDeclarationFragment m) {
        result =  translator.apply(getState(m, id, b4));
        return false;
    }

    @Override
    public boolean visit(VariableDeclarationStatement m) {
        result =  translator.apply(getState(m, id, b4));
        return false;
    }

    @Override
    public boolean visit(SingleVariableDeclaration m) {
        result =  translator.apply(getState(m, id, b4));
        return false;
    }

    @Override
    public boolean visit(Assignment a) {
        result =  translator.apply(getState(a, id, b4));
        return false;
    }

    public U getResult(){
        return result;
    }

    public static <U> U getU(Tree2State2U<U> tsu, ASTNode ast){
        Tree2State2U<U> tree2State2U = tsu;
        ast.accept(tree2State2U);
        return tree2State2U.getResult();
    }

}
