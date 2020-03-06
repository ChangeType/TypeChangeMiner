package TFGStuff;

import com.t2r.common.models.ast.IdentificationOuterClass.Identification;
import org.eclipse.jdt.core.dom.*;

public class Tree2Id extends ASTVisitor {

    private Identification id;

    private final Identification parent;
    //    private final Context context;
    private final boolean b4;

    //    public Tree2Id(Identification parent, Context context, boolean b4) {
    public Tree2Id(Identification parent, boolean b4) {
        this.parent = parent;
        this.b4 = b4;
    }

    @Override
    public boolean visit(SimpleName s) {
        id = Identification.newBuilder()
                .setName(s.getIdentifier())
                .setKind("IDENTIFIER")
//                .setType(context.getTypeSignFor(s.getIdentifier(), b4))
                .build();
        return false;
    }

    @Override
    public boolean visit(ThisExpression s) {
        id = Identification.newBuilder()
                .setName(s.toString())
                .setKind("THIS")
//                .setType(getTypeSign(context.getClassInvolved(b4)))
                .build();
        return false;
    }

    @Override
    public boolean visit(QualifiedName s) {
        id = Identification.newBuilder()
                .setName(s.getFullyQualifiedName())
                .setKind("IDENTIFIER")
                //  .setType(TypeSignatureOuterClass.TypeSignature.newBuilder().build())
                .build();
        return false;
    }

    @Override
    public boolean visit(InfixExpression s) {
        id = Identification.newBuilder()
                .setName(s.getOperator().toString())
                .setKind("OPERATOR")
                //.setType(TypeSignatureOuterClass.TypeSignature.newBuilder().build())
                .build();
        return false;
    }

    @Override
    public boolean visit(MethodInvocation mi) {
//        TypeSignatureOuterClass.TypeSignature rcvrType = defaultType;
        id = Identification.newBuilder()
                .setName(mi.getName().getIdentifier())
                .setKind("METHOD_INVOCATION")
                .build();
//        if (mi.getExpression() != null) {
//            rcvrType = getId(mi.getExpression(), parent, context, b4).getType();
//            id = id.toBuilder()
//                    .setType(context.getMethodSignature(rcvrType, id.getName(), mi.arguments().size())).build();
//        }
        return false;
    }

    @Override
    public boolean visit(NullLiteral n) {
        id = Identification.newBuilder()
                .setName(n.toString())
                .setKind("NULL_LITERAL")
                //         .setType(TypeSignatureOuterClass.TypeSignature.newBuilder().build())
                .setOwner(parent).build();
        return false;
    }

    @Override
    public boolean visit(NumberLiteral n) {
        id = Identification.newBuilder()
                .setName(n.toString())
                .setKind("NUMBER_LITERAL")
                //       .setType(TypeSignatureOuterClass.TypeSignature.newBuilder().build())
                .setOwner(parent).build();
        return false;
    }

    @Override
    public boolean visit(BooleanLiteral n) {
        id = Identification.newBuilder()
                .setName(n.toString())
                .setKind("BOOLEAN_LITERAL")
//                .setType(getTypeSign("boolean"))
                .setOwner(parent).build();
        return false;
    }

    @Override
    public boolean visit(ConditionalExpression n) {
        id = Identification.newBuilder()
                .setName("")
                .setKind("CONDITIONAL_EXPRESSION")
                //      .setType(TypeSignatureOuterClass.TypeSignature.newBuilder().build())
                .setOwner(parent).build();
        return false;
    }


    @Override
    public boolean visit(CharacterLiteral n) {
        id = Identification.newBuilder()
                .setName(n.toString())
                .setKind("CHARACTER_LITERAL")
//                .setType(getTypeSign("char"))
                .setOwner(parent).build();
        return false;
    }

    @Override
    public boolean visit(DoStatement n) {
        id = Identification.newBuilder()
                .setName(n.toString())
                .setKind("DO_STATEMENT")
                //     .setType(TypeSignatureOuterClass.TypeSignature.newBuilder().build())
                .setOwner(parent).build();
        return false;
    }

    @Override
    public boolean visit(ReturnStatement n) {
        id = Identification.newBuilder()
                .setName(n.toString())
                .setKind("RETURN")
                //      .setType(TypeSignatureOuterClass.TypeSignature.newBuilder().build())
                .setOwner(parent).build();
        return false;
    }

    @Override
    public boolean visit(StringLiteral n) {
        id = Identification.newBuilder()
                .setName(n.toString())
                .setKind("STRING_LITERAL")
//                .setType(getTypeSign("java.lang.String"))
                .setOwner(parent).build();
        return false;
    }

    @Override
    public boolean visit(TypeLiteral root){
        id = Identification.newBuilder()
                .setName(root.getType().toString())
                .setKind("TYPE_LITERAL")
                //        .setType(TypeSignatureOuterClass.TypeSignature.newBuilder().build())
                .setOwner(parent).build();
        return false;
    }

    @Override
    public boolean visit(ThrowStatement n) {
        id = Identification.newBuilder()
                .setName("")
                .setKind("THROWS_STATEMENT")
                //        .setType(TypeSignatureOuterClass.TypeSignature.newBuilder().build())
                .setOwner(parent).build();
        return false;
    }

    @Override
    public boolean visit(ForStatement n) {
        id = Identification.newBuilder()
                .setName("")
                .setKind("FOR_STATEMENT")
                //        .setType(TypeSignatureOuterClass.TypeSignature.newBuilder().build())
                .setOwner(parent).build();
        return false;
    }

    @Override
    public boolean visit(WhileStatement n) {
        id = Identification.newBuilder()
                .setName("")
                .setKind("WHILE_STATEMENT")
                //        .setType(TypeSignatureOuterClass.TypeSignature.newBuilder().build())
                .setOwner(parent).build();
        return false;
    }

    @Override
    public boolean visit(ClassInstanceCreation n) {
        id = Identification.newBuilder()
                .setName(n.getType().toString())
                .setKind("CLASS_INSTANCE_CREATION")
//                .setType(context.getConstructorSignature(n.getType().toString(), n.arguments().size()))
                .setOwner(parent).build();
        return false;
    }



    public Identification getIdGen() {
        return id;
    }

    public static Identification getId(ASTNode ast, Identification p, boolean b4) {
        Tree2Id tree2ID = new Tree2Id(p, b4);
        ast.accept(tree2ID);
        return tree2ID.getIdGen();
    }



}
