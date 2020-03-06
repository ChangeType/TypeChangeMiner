package TFGStuff;


import com.t2r.common.models.ast.IdentificationOuterClass.Identification;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static TFGStuff.Tree2Id.getId;


public class Tree2State extends ASTVisitor {

    public static final String ASSIGNMENT = "ASSIGNMENT";
    public static final String RETURNS = "RETURNS";
    public static final String ARGUMENT = "ARGUMENT";
    public static final String RECEIVER = "RECEIVER";
    public static final String IMPLEMENTS = "IMPLEMENTS";
    public static final String EXTENDS = "EXTENDS";
    public static final String INITIALIZED_AS = "INITIALIZED_AS";
    public static final String PARAM = "PARAM";
    public static final String BIN_OP = "BIN_OP";
    public static final String THROWS = "THROWS";


    private State s;
    private final Identification parentID;
//    private Context context;
    private boolean b4;
    private static final String DO_STATEMENT = "DO STATEMENT";

//   public Tree2State(Identification parentID context, boolean b4) {
    public Tree2State(Identification parentID, boolean b4) {
        this.parentID = parentID;
//       this.context = context;
       this.b4 = b4;
   }

    @Override
    public boolean visit(ClassInstanceCreation root) {
        Identification rootID = getId(root, parentID, b4);
        List<Tuple2<State, String>> typeDependents = new ArrayList<>();
        if(null != root.arguments()) {
            for (int i = 0; i < root.arguments().size(); i++) {
                State param = getState((ASTNode) root.arguments().get(i), rootID, b4);
                typeDependents.add(Tuple.of(param, ARGUMENT + i));
            }
        }
        s = new State(root, rootID, typeDependents);
        return false;
    }

    @Override
    public boolean visit(MethodInvocation root) {
        Identification rootID = getId(root, parentID, b4);
        List<Tuple2<State, String>> typeDependents = new ArrayList<>();
        if(null != root.arguments()) {
            for (int i = 0; i < root.arguments().size(); i++) {
                State param = getState((ASTNode) root.arguments().get(i), rootID, b4);
                typeDependents.add(Tuple.of(param, ARGUMENT + i));
            }
        }
        if(root.getExpression()!=null)
            typeDependents.add(Tuple.of(getState(root.getExpression(),rootID, b4), RECEIVER));
        s = new State(root, rootID, typeDependents);
        return false;
    }

    @Override
    public boolean visit(InfixExpression root) {
        Identification rootID = getId(root, parentID, b4);
        List<Tuple2<State, String>> typeDependents = new ArrayList<>();
        typeDependents.add(Tuple.of(getState(root.getRightOperand(),rootID, b4), "RHS"));
        typeDependents.add(Tuple.of(getState(root.getLeftOperand(),rootID, b4), "LHS"));
        List<Expression> extendedOperands = root.extendedOperands();
        IntStream.range(0,extendedOperands.size())
                .forEach(i -> typeDependents.add(Tuple.of(getState(extendedOperands.get(i), rootID, b4),"OPERAND" + i)));
        s = new State(root, rootID, typeDependents);
        return false;
    }

    @Override
    public boolean visit(ThisExpression root) {
        s = new State(root, getId(root, parentID, b4), new ArrayList<>());
        return false;
    }

    @Override
    public boolean visit(ConditionalExpression root) {
        Identification rootID = getId(root, parentID, b4);
        List<Tuple2<State, String>> typeDependents = new ArrayList<>();
        typeDependents.add(Tuple.of(getState(root.getExpression(),rootID, b4), "CONDITION"));
        typeDependents.add(Tuple.of(getState(root.getThenExpression(),rootID, b4), "THEN"));
        typeDependents.add(Tuple.of(getState(root.getElseExpression(),rootID, b4), "ELSE"));
        s = new State(root, rootID, typeDependents);
        return false;
    }

    @Override
    public boolean visit(VariableDeclarationExpression varDecl){
        if(varDecl.fragments().size() != 1) throw new RuntimeException("Can't handle this !!!");
        VariableDeclarationFragment frag = (VariableDeclarationFragment) varDecl.fragments().get(0);
        final Identification lhs = getId(frag.getName(), parentID, b4);
//                .toBuilder().setType(getTypeSign(varDecl.getType())).build();
        s = new State(varDecl, lhs, frag.getInitializer()!=null
                ? Arrays.asList(Tuple.of(Tree2State.getState(frag.getInitializer(),lhs, b4), ASSIGNMENT))
                : new ArrayList<>());
        return false;
    }

    @Override
    public boolean visit(VariableDeclarationStatement varDecl){
            if(varDecl.fragments().size() != 1) throw new RuntimeException("Can't handle this !!!");
        VariableDeclarationFragment frag = (VariableDeclarationFragment) varDecl.fragments().get(0);
        final Identification lhs = getId(frag.getName(), parentID, b4);
//                .toBuilder()
//                .setType(getTypeSign(varDecl.getType())).build();
        s = new State(varDecl, lhs, frag.getInitializer()!=null
                ? Arrays.asList(Tuple.of(Tree2State.getState(frag.getInitializer(),lhs, b4), ASSIGNMENT))
                : new ArrayList<>());
        return false;
    }

    @Override
    public boolean visit(SingleVariableDeclaration varDecl){
        final Identification lhs = getId(varDecl.getName(), parentID, b4);
//                .toBuilder().setType(getTypeSign(varDecl.getType())).build();

        s = new State(varDecl, lhs, varDecl.getInitializer()!=null
                ? Arrays.asList(Tuple.of(Tree2State.getState(varDecl.getInitializer(),lhs, b4), ASSIGNMENT))
                : new ArrayList<>());
        return false;
    }

    @Override
    public boolean visit(DoStatement frag){
        final Identification doStmt = getId(frag, parentID, b4);
        s = new State(frag, doStmt,  Arrays.asList(Tuple.of(Tree2State.getState(frag.getBody(),doStmt, b4), DO_STATEMENT)));
        return false;
    }

    @Override
    public boolean visit(WhileStatement frag){
        final Identification doStmt = getId(frag, parentID, b4);
        s = new State(frag, doStmt,  Arrays.asList(Tuple.of(Tree2State.getState(frag.getExpression(),doStmt, b4), DO_STATEMENT)));
        return false;
    }


    @Override
    public boolean visit(ForStatement frag){
        final Identification forStmt = getId(frag, parentID, b4);
        List<Tuple2<State, String>> typeDependents = new ArrayList<>();
        typeDependents.add(Tuple.of(getState(frag.getExpression(),forStmt, b4), "EXPRESSION"));
        int cnt = 0;
        for(Expression e : (List<Expression>) frag.initializers()){
            typeDependents.add(Tuple.of(getState(e,forStmt, b4), "INITIALIZER "+ cnt));
            cnt += 1;
        }
        cnt = 0;
        for(Expression e : (List<Expression>) frag.updaters()){
            typeDependents.add(Tuple.of(getState(e,forStmt, b4), "UPDATERS "+ cnt));
            cnt += 1;
        }

        s = new State(frag, forStmt,  typeDependents);
        return false;
    }

    @Override
    public boolean visit(TryStatement frag){
        final Identification tryStmt = getId(frag, parentID, b4);
        List<Tuple2<State, String>> typeDependents = new ArrayList<>();
        int cnt = 0;
        for(Expression e : (List<Expression>) frag.resources()){
            typeDependents.add(Tuple.of(getState(e,tryStmt, b4), "INITIALIZER "+ cnt));
            cnt += 1;
        }
        s = new State(frag, tryStmt,  typeDependents);
        return false;
    }

    @Override
    public boolean visit(VariableDeclarationFragment frag){
        final Identification lhs = getId(frag.getName(), parentID, b4);
        s = new State(frag, lhs, frag.getInitializer()!=null
                ? Arrays.asList(Tuple.of(Tree2State.getState(frag.getInitializer(),lhs, b4), ASSIGNMENT))
                : new ArrayList<>());
        return false;
    }

    @Override
    public boolean visit(SimpleName root){
        s = new State(root, getId(root, parentID, b4), new ArrayList<>());
        return false;
    }

    @Override
    public boolean visit(FieldAccess root){
        s = new State(root, getId(root.getExpression(), parentID, b4), new ArrayList<>());
        return false;
    }

    @Override
    public boolean visit(NumberLiteral root){
        s = new State(root, getId(root, parentID, b4), new ArrayList<>());
        return false;
    }

    @Override
    public boolean visit(BooleanLiteral root){
        s = new State(root, getId(root, parentID, b4), new ArrayList<>());
        return false;
    }

    @Override
    public boolean visit(TypeLiteral root){
        s = new State(root, getId(root, parentID, b4), new ArrayList<>());
        return false;
    }

    @Override
    public boolean visit(StringLiteral root){
        s = new State(root, getId(root, parentID, b4), new ArrayList<>());
        return false;
    }

    @Override
    public boolean visit(NullLiteral root){
        s = new State(root, getId(root, parentID, b4), new ArrayList<>());
        return false;
    }

    @Override
    public boolean visit(CharacterLiteral root){
        s = new State(root, getId(root, parentID, b4), new ArrayList<>());
        return false;
    }

    @Override
    public boolean visit(ThrowStatement a){
        final Identification lhs = getId(a, parentID, b4);
        s = new State(a, lhs, Arrays.asList(Tuple.of(Tree2State.getState(a.getExpression(),lhs, b4), THROWS)));
        return false;
    }

    @Override
    public boolean visit(ReturnStatement a){
        if(a.getExpression() != null)
            s =Tree2State.getState(a.getExpression(), parentID, b4);
        s = new State(a, getId(a, parentID, b4), new ArrayList<>());
        return false;
    }

    @Override
    public boolean visit(Assignment a){
        final Identification lhs = getId(a.getLeftHandSide(), parentID, b4);
        s = new State(a, lhs, Arrays.asList(Tuple.of(Tree2State.getState(a.getRightHandSide(),lhs, b4), ASSIGNMENT)));
        return false;
    }

    public State getStateGen(){
        return s;
    }

    public static State getState(ASTNode ast, Identification s, boolean b4){
        Tree2State tree2state = new Tree2State(s, b4);
        try {
            ast.accept(tree2state);
        }catch (Exception e){
            System.out.println("Could not figure out for " + ast.toString());
            return null;
        }
        State stateGen = tree2state.getStateGen();
        if(stateGen == null){
            System.out.println("Could not figure out for " + ast.toString());
        }
        return stateGen;
    }

}
