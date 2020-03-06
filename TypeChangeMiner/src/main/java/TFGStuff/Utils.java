package TFGStuff;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.Document;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Utils {


    public static boolean contains(String str, char c, int cnt) {
        int count = 0;
        for (char a : str.toCharArray()) {
            if (a == c)
                count += 1;
        }
        return count == cnt;
    }


    public static Optional<String> processStatement(String str) {

        str = str.replace("\n","");

        if (str.startsWith("if(") || str.startsWith("while(")) {
            return Optional.of(str + ";");
        } else if (str.startsWith("do ") || str.startsWith("do{")) {
            str = str + "{int x = 1;} while(true)";
            return Optional.of(str + ";");
        } else if (str.startsWith("switch(")) {
            return Optional.of(str + "{ default: return x;}");
        } else if (str.startsWith("synchronized(") || str.startsWith("try(")) {
            return Optional.of(str + "{ int x = 10; }");
        } else if (str.startsWith("for(")) {
            if (contains(str, ';', 1) && !str.contains("=")) {
                str = str.replace(";", ":");
                str = str.replace("for(", "for(var ");
            } else if (contains(str, ';', 1) && str.contains("=")) {
                str = str + "#";
                str = str.replace(")#",";)");

            } else if (contains(str, ';', 1)) {
                str = str.replace("for(", "for(int x = 0;");
            }
            return Optional.of(str + ";");
        } else if (str.contains("=") && !str.contains("==") && !str.contains("!=") && !str.contains(">=") && !str.contains("<=")) {
            return Optional.of(str + ";");
        }
        else if (str.startsWith("assert ") && !str.contains(":")) {
            return Optional.empty();
        }
        else if (str.startsWith("return ")) {
            str = str.replace("return ","");
            str = "var x = " + str;
            return Optional.of(str);
        }
        else if(str.endsWith(";")){
            return Optional.of(str);
        }

        return Optional.empty();
    }

    public static ASTNode getASTNode(String str) {
        return processStatement(str)
                .map(Utils::getASTNodeStmt)
                .orElseGet(() -> getASTNodeExpr(processExpression(str)));
    }

    private static String processExpression(String str) {
        str = str.replace("\n","");
        if(str.startsWith("return "))
            str = str.replace("return ","");
        if(str.startsWith("assert "))
            str = str.replace("assert ","");

        if(str.endsWith(";")) {
            str += "#";
            str = str.replace(";#","");
        }




        return str;
    }

    public static ASTNode getASTNodeStmt(String str) {
        ASTParser parser = ASTParser.newParser(AST.JLS11);
        parser.setKind(ASTParser.K_STATEMENTS);
        parser.setEnvironment(new String[]{""}, new String[]{""}, new String[]{"UTF-8"}, true);
        parser.setSource(str.toCharArray());
        return parser.createAST(null);
    }

    public static ASTNode getASTNodeExpr(String str) {
        ASTParser parser = ASTParser.newParser(AST.JLS11);
        parser.setKind(ASTParser.K_EXPRESSION);
        parser.setEnvironment(new String[]{""}, new String[]{""}, new String[]{"UTF-8"}, true);
        parser.setSource(str.toCharArray());
        return parser.createAST(null);
    }

    private static Block getBlock(AST ast) {
        VariableDeclarationFragment v = ast.newVariableDeclarationFragment();
        v.setName(ast.newSimpleName("a"));
        v.setInitializer(ast.newNullLiteral());
        VariableDeclarationStatement vs = ast.newVariableDeclarationStatement(v);
//        vs.setType(generateType(ast,t));
        Block body = ast.newBlock();

        body.statements().add(vs);
        return body;
    }

    public static MemberValuePair getMemberValuePairs(String name, String value, AST ast) {
        MemberValuePair mvp = ast.newMemberValuePair();
        mvp.setName(ast.newSimpleName(name));
        StringLiteral value1 = ast.newStringLiteral();
        value1.setLiteralValue(value);
        mvp.setValue(value1);
        return mvp;
    }

    public static Annotation getAnnotation(AST ast, String typeName, boolean b4Aft) {
        NormalAnnotation na = ast.newNormalAnnotation();
        na.setTypeName(ast.newSimpleName("T2RTemplate"));
        na.values().addAll(Arrays.asList(getMemberValuePairs("name", "e1", ast)
                , getMemberValuePairs("kind", b4Aft ? "Before" : "After", ast)
                , getMemberValuePairs("t", typeName, ast)));
        return na;
    }

//    public static MethodDeclaration getMthdDeclFor(AST ast, TypeSignatureOuterClass.TypeSignature t, boolean b4Afr){
//        List<ImportDeclaration> imports = new ArrayList<>();
//        MethodDeclaration md = ast.newMethodDeclaration();
//        md.setName(ast.newSimpleName(generateMethodName(t)));
//        md.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));
//
//        md.modifiers().add(getAnnotation(ast, prettyType(t),b4Afr));
//
//        Block body = getBlock(ast, t);
//        md.setBody(body);
//        List modifiers = md.modifiers();
//        modifiers.add(ast.newModifier(PUBLIC_KEYWORD));
//        modifiers.add(ast.newModifier(STATIC_KEYWORD));
//        return md;
//    }

//    private static String generateMethodName(TypeSignatureOuterClass.TypeSignature t) {
//        return replaceIfExists(replaceIfExists(replaceIfExists(replaceIfExists(prettyType(t), "<", ""), ">", ""), ",", ""),".","")
//                +"Eg";
//    }
//
//    private static List<ImportDeclaration> getImportDeclarations(AST ast, TypeSignatureOuterClass.TypeSignature type) {
//        String x = prettyType(type);
//        String simpleName = x.substring(x.lastIndexOf(".") + 1);
//        String qualifier = x.substring(0,x.lastIndexOf("."));
//        ImportDeclaration i = ast.newImportDeclaration();
//        i.setName(ast.newQualifiedName(ast.newName(qualifier), ast.newSimpleName(simpleName)));
//        return Arrays.asList(i);
//    }
//


    public static String replaceIfExists(String s, String what, String with) {
        return s.contains(what) ? s.replace(what, with) : s;
    }

//    private static Optional<PrimitiveType.Code> getPrmtvCode(TypeSignatureOuterClass.TypeSignature type){
//        String p = prettyType(type);
//        if(p.equals("long")) return Optional.of(PrimitiveType.LONG);
//        if(p.equals("double")) return Optional.of(PrimitiveType.DOUBLE);
//        if(p.equals("void")) return Optional.of(PrimitiveType.VOID);
//        if(p.equals("boolean")) return Optional.of(PrimitiveType.BOOLEAN);
//        if(p.equals("int")) return Optional.of(PrimitiveType.INT);
//        return Optional.empty();
//    }

//    public  static Type generateType(AST ast, TypeSignatureOuterClass.TypeSignature type){
//        if(getPrmtvCode(type).isPresent()){
//            return ast.newPrimitiveType(getPrmtvCode(type).get());
//        }
//        String x = prettyType(type);
//        String simpleName = x.contains(".") ? x.substring(x.lastIndexOf(".") + 1) : x;
//        return ast.newSimpleType(ast.newSimpleName(simpleName));
//    }
//
//    public  static Type generateType(AST ast, TypeSignatureOuterClass.TypeInfo ti){
//        return generateType(ast, TypeSignatureOuterClass.TypeSignature.newBuilder().setTypeSign(ti).build());
//    }

    enum B4Aftr {
        B4, After
    }


    public static Tuple2<CompilationUnit, Document> getCuFor(Path p) {
        String cuPath = p.toAbsolutePath().toString();
        ASTParser parser = ASTParser.newParser(AST.JLS11);
        Document document;
        try {
            String str = Files.readAllLines(Paths.get(cuPath)).stream().collect(Collectors.joining("\n"));
            parser.setSource(str.toCharArray());
            String source = Files.readAllLines(p.toAbsolutePath()).stream().collect(Collectors.joining("\n"));
            document = new Document(source);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Could not get file");
        }
        parser.setResolveBindings(true);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setBindingsRecovery(true);
        Map options = JavaCore.getOptions();
        parser.setCompilerOptions(options);
        String unitName = Paths.get(cuPath).getFileName().toString();
        parser.setUnitName(unitName);
        String[] sources = {"D:\\MyProjects\\T2RTemplateGenerator"};
        String[] classpath = {"C:\\Program Files\\Java\\jdk-11.0.4\\bin"};
        parser.setEnvironment(classpath, sources, new String[]{"UTF-8"}, true);

        return Tuple.of((CompilationUnit) parser.createAST(null), document);
    }


    public static <U> Tuple2<U, U> nullTuple2() {
        return Tuple.of(null, null);
    }

    public static <T> boolean isNullTuple(Tuple2<T, T> s) {
        return s._1() == null && s._2() == null;
    }

    public static <T> boolean anyNull(Tuple2<T, T> s) {
        return s._1() == null || s._2() == null;
    }

    public static Statement assignToPlaceHolder(AST ast, SimpleName name, Expression clsInst) {
        System.out.println(clsInst.toString());
        Assignment v = ast.newAssignment();
        v.setLeftHandSide(name);
        v.setRightHandSide((Expression) ASTNode.copySubtree(ast, clsInst));
        return ast.newExpressionStatement(v);
    }

    public static SimpleName getPlaceHolderName(MethodDeclaration mthd, AST ast) {
        VariableDeclarationStatement firstStmt = (VariableDeclarationStatement) mthd.getBody().statements().get(0);
        VariableDeclarationFragment frag = (VariableDeclarationFragment) firstStmt.fragments().get(0);
        return ast.newSimpleName(frag.getName().getFullyQualifiedName());
    }

    public static Statement createStmtFor(AST ast, SimpleName name, MappingObj mobj, boolean ba) {
        System.out.println(mobj.getRelevantExprs(ba).toString());
        VariableDeclarationFragment vf = ast.newVariableDeclarationFragment();
        vf.setName(name);
        vf.setInitializer(mobj.getRelevantExprs(ba));
        VariableDeclarationStatement vds = ast.newVariableDeclarationStatement(vf);
//        vds.setType(generateType(ast,getReturnType(mobj,ba)));
        return vds;
    }

}
