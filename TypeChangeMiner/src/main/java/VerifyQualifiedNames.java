import com.jasongoodwin.monads.Try;
import com.t2r.common.models.ast.TypeGraphOuterClass.TypeGraph;
import com.t2r.common.models.ast.TypeNodeOuterClass;
import com.t2r.common.models.refactorings.NameSpaceOuterClass;
import com.t2r.common.models.refactorings.ProjectOuterClass;
import com.t2r.common.models.refactorings.TypeChangeAnalysisOuterClass.TypeChangeAnalysis.TypeChangeInstance;
import com.t2r.common.models.refactorings.TypeChangeCommitOuterClass.TypeChangeCommit;
import com.t2r.common.models.refactorings.VerificationOuterClass;
import com.t2r.common.utilities.GitUtil;
import gr.uom.java.xmi.TypeFactMiner.TypeGraphUtil;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.api.Git;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.t2r.common.models.ast.TypeNodeOuterClass.TypeNode.TypeKind.Primitive;
import static com.t2r.common.models.ast.TypeNodeOuterClass.TypeNode.TypeKind.Simple;
import static com.t2r.common.utilities.PrettyPrinter.pretty;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.*;

public class VerifyQualifiedNames {

    public static Path pathToCorpus = Paths.get("/Users/ameya/Research/TypeChangeStudy/BuildCorpus/");


    public static void main(String a[]) throws Exception {
        buildCommits("guava");
    }


    public static void buildCommits(String prjct) throws Exception {

        Map<String, List<String>> goals = new HashMap<>();

        goals.put(prjct, Arrays.asList("clean", "install", "-DskipTests"));
//        goals.put("CoreNLP", Arrays.asList("clean", "package", "-DskipTests"));

        File repo = new File(pathToCorpus + "/Project_" + prjct + "/" + prjct);

        List<TypeChangeCommit> commits = Runner.readWriteInputProtos.<ProjectOuterClass.Project>readAll("Projects", "Project").stream()
                .filter(p -> p.getName().contains(prjct))
                .map(prc -> Tuple.of(prc, Runner.readWriteOutputProtos.<TypeChangeCommit>readAll("TypeChangeCommit_" + prc.getName(), "TypeChangeCommit")))
                .flatMap(x -> x._2().stream())
//                .filter(x -> x.getSha().equals("a7acd431fa71b18a09310216c47cef48533fe109"))
                .collect(toList());

        for (var c : commits) {

            Git r = Git.open(repo);

            GitUtil.checkout(r.getRepository(), c.getSha());



            List<TypeChangeInstance> tcis = c.getTypeChangesList().stream()
//                    .filter(x -> !x.getNameSpacesB4().equals(NameSpaceOuterClass.NameSpace.External))
                    .filter(x -> !x.getNameSpacesB4().equals(NameSpaceOuterClass.NameSpace.TypeVariable))
                    .flatMap(x -> x.getTypeChangeInstancesList().stream())
                    .collect(toList());

            for (var tci : tcis) {
                if (tci.getB4().getRoot().getKind().equals(Primitive)) {
                    var v = VerificationOuterClass.Verification.newBuilder()
                            .setType(tci.getAftr())
                            .setMatched(true)
                            .setProject(prjct)
                            .build();
                    Runner.readWriteVerificationProtos.write(v, "Verification_" + prjct, true);
                    System.out.println(pretty(v.getType()) + " " + "Verified: " + v.getMatched());
                    continue;
                }

                List<CompilationUnit> compilationUnits = getCuFor(tci.getCompilationUnit(), prjct);
                if (!compilationUnits.isEmpty()) {

                    List<Tuple2<String, TypeGraph>> possibleMAtches = compilationUnits.stream()
                            .flatMap(cu -> {
                                Qualifier Qvisitor = new Qualifier(tci.getElementKindAffected().name(), tci.getAftr(), tci.getNameAfter());
                                cu.accept(Qvisitor);
                                return Qvisitor.v.stream();
                            })
                            .collect(toList());


                    List<String> imports = compilationUnits.stream().map(x -> (List<ImportDeclaration>) x.imports()).flatMap(i -> i.stream()).map(i -> i.getName().getFullyQualifiedName()).collect(toList());
                    var v = VerificationOuterClass.Verification.newBuilder()
                            .setType(tci.getAftr())
                            .setMatched(
                                    possibleMAtches.isEmpty() ||
                                    possibleMAtches.stream().anyMatch(qv -> prettyEqual(tci.getAftr(),qv._2(), imports))
                                    || findInImports(tci.getAftr()
                                    , imports))
                            .setProject(prjct)
                            .setFailed(possibleMAtches.isEmpty())
                            .build();
                    Runner.readWriteVerificationProtos.write(v, "Verification_" + prjct, true);
                    System.out.println(pretty(v.getType()) + " " + "Verified: " + v.getMatched() + "  " + "Failed: " + v.getFailed());

                    if (!v.getMatched() && !v.getFailed()) {
                        System.out.println("Did not match " + possibleMAtches.stream().map(z->pretty(z._2())).collect(joining(",")) + "   Expected : " + pretty(tci.getAftr()));
                        possibleMAtches.stream().anyMatch(qv -> prettyEqual(tci.getAftr(),qv._2(), imports)|| findInImports(tci.getAftr()
                                , imports));
                    }
                }
            }
        }
    }

    private static boolean findInImports(TypeGraph aftr, List<String> collect) {
        List<String> pl = prettyLIST(aftr);
        return pl.stream().allMatch(p -> collect.contains(p));
    }

    public static List<String> prettyLIST(TypeGraph tg) {
        if(tg.getRoot().getIsTypeVariable())
            return new ArrayList<>();
        if (tg.getRoot().getKind().equals(Simple) || tg.getRoot().getKind().equals(Primitive)) {
            return singletonList(tg.getRoot().getName());
        } else if (tg.getRoot().getKind().equals(TypeNodeOuterClass.TypeNode.TypeKind.Parameterized)) {
            return Stream.concat(Stream.of(pretty(tg.getEdgesMap().get("of")))
                    , tg.getEdgesMap().entrySet().stream().filter(x -> x.getKey().contains("Param"))
                            .flatMap(x -> prettyLIST(x.getValue()).stream())).collect(toList());
        } else if (tg.getRoot().getKind().equals(TypeNodeOuterClass.TypeNode.TypeKind.Array)) {
            return prettyLIST(tg.getEdgesMap().get("of"));
        } else if (tg.getRoot().getKind().equals(TypeNodeOuterClass.TypeNode.TypeKind.WildCard)) {
            if (tg.getEdgesMap().containsKey("extends")) {
                return prettyLIST(tg.getEdgesMap().get("extends"));
            }
            else if (tg.getEdgesMap().containsKey("super")){
                return prettyLIST(tg.getEdgesMap().get("super"));
            }
            else{
                return singletonList("?");
            }
        } else if (tg.getRoot().getKind().equals(TypeNodeOuterClass.TypeNode.TypeKind.Union))
            return tg.getEdgesMap().entrySet().stream().filter(x -> x.getKey().contains("Union") || x.getKey().contains("Intersection"))
                    .flatMap(x -> prettyLIST(x.getValue()).stream())
                    .collect(Collectors.toList());
        return new ArrayList<>();
    }


    public static class Qualifier extends ASTVisitor {

        public List<Tuple2<String, TypeGraph>> v = new ArrayList<>();
        private String kind;
        private TypeGraph tg;
        private String name;

        private boolean matched;


        Qualifier(String kind, TypeGraph tg, String name) {
            this.kind = kind;
            this.tg = tg;
            this.name = name;
            this.matched = false;
        }

        @Override
        public boolean visit(TypeDeclaration node) {
            FieldDeclaration[] fds = node.getFields();
            for(var fd: fds){
                visit(fd);
            }
            return super.visit(node);
        }

        @Override
        public boolean visit(FieldDeclaration node) {
            try {
                List<VariableDeclarationFragment> fragments = node.fragments();
                for (var frg : fragments) {
                    if (frg.getName().getFullyQualifiedName().equals(name)) {
//                        if(node.getType().resolveBinding().isRecovered())
                            v.add(Tuple.of(name, getTypeGraph(node.getType())));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return super.visit(node);
        }

        @Override
        public boolean visit(MethodDeclaration node) {
            try {
                if (node.getReturnType2() != null) {
                    if (node.getName().getFullyQualifiedName().equals(name)) {
//                        if(node.getReturnType2().resolveBinding().isRecovered())
                            v.add(Tuple.of(name, getTypeGraph(node.getReturnType2())));
                    }
                }

                List<SingleVariableDeclaration> params = node.parameters();
                for(var p : params){
                    if (p.getName().getFullyQualifiedName().equals(name))
//                        if(p.getType().resolveBinding().isRecovered())
                             v.add(Tuple.of(name, getTypeGraph(p.getType())));
                }



            } catch (Exception e) {
                e.printStackTrace();
            }
            return super.visit(node);
        }

        @Override
        public boolean visit(SingleVariableDeclaration node) {
            try {
                if (node.getName().getFullyQualifiedName().equals(name))
//                    if(node.getType().resolveBinding().isRecovered())
                        v.add(Tuple.of(name, getTypeGraph(node.getType())));
            } catch (Exception e) {
            }
            return super.visit(node);
        }


        @Override
        public boolean visit(VariableDeclarationStatement node) {
            try {
                List<VariableDeclarationFragment> fragments = node.fragments();
                for (var frg : fragments) {
                    if (frg.getName().getFullyQualifiedName().equals(name)) {
//                        if(node.getType().resolveBinding().isRecovered())
                            v.add(Tuple.of(name, getTypeGraph(node.getType())));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return super.visit(node);
        }


        @Override
        public boolean visit(VariableDeclarationExpression node) {
            try {
                List<VariableDeclarationFragment> fragments = node.fragments();
                for (var frg : fragments) {
                    if (frg.getName().getFullyQualifiedName().equals(name)) {
//                        if(node.getType().resolveBinding().isRecovered())
                            v.add(Tuple.of(name, getTypeGraph(node.getType())));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return super.visit(node);
        }

    }

    public static List<CompilationUnit> getCuFor(String cuPath, String project) throws IOException {


        List<Path> paths = Files.walk(pathToCorpus.resolve("Project_" + project).resolve(project))
                .filter(x -> {
                    String replace = cuPath.replaceAll("\\.", "/") + ".java";
                    return x.toAbsolutePath().toString().contains(replace);
                })
                .collect(toList());



        return paths.stream()
                .map(p ->  Try.ofFailable(() -> Files.readString(p)).orElse(""))
                .map(str -> {

                    ASTParser parser = ASTParser.newParser(AST.JLS11);
                    parser.setResolveBindings(true);
                    parser.setKind(ASTParser.K_COMPILATION_UNIT);

                    parser.setBindingsRecovery(true);

                    Map options = JavaCore.getOptions();
                    parser.setCompilerOptions(options);

                    String unitName = Paths.get(cuPath).getFileName().toString();
                    parser.setUnitName(unitName);

                    String[] sources = {pathToCorpus + "/Project_" + project + "/" + project};
                    String[] classpath = {"/Library/Java/JavaVirtualMachines/jdk-11.0.6.jdk/Contents/Home/"};

                    parser.setEnvironment(classpath, sources, new String[]{"UTF-8"}, true);
                    parser.setSource(str.toCharArray());

                    CompilationUnit cu = (CompilationUnit) parser.createAST(null);

                    if (cu.getAST().hasBindingsRecovery()) {
                        System.out.println("Binding activated.");
                        return cu;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(toList());
    }


    public static TypeGraph getTypeGraph(Type t) {
        if (t.isQualifiedType())
            return getTypeGraph((QualifiedType) t);
        else if (t.isNameQualifiedType())
            return getTypeGraph((NameQualifiedType) t);
        else if (t.isSimpleType())
            return getTypeGraph((SimpleType) t);
        else if (t.isParameterizedType())
            return getTypeGraph((ParameterizedType) t);
        else if(t.isWildcardType())
            return getTypeGraph( (WildcardType) t);
        else if(t.isPrimitiveType())
            return getTypeGraph((PrimitiveType) t);
        else if(t.isArrayType())
            return getTypeGraph((ArrayType) t);
//        else if(t.isIntersectionType())
//            return getTypeGraph((IntersectionType) t);
//        else if(t.isUnionType())
//            return getTypeGraph((UnionType) t);
        else
            throw new RuntimeException("Could not figure out type");
    }


    private static TypeGraph getTypeGraph(SimpleType st) {
        final List<Annotation> ann = st.annotations();
        final List<String> annotation = ann.stream().map(a -> "@" + a.getTypeName().getFullyQualifiedName())
                .collect(toList());
        ITypeBinding iTypeBinding = st.resolveBinding();
        if(iTypeBinding.getBinaryName() != null)
            return of(getTypeNode(iTypeBinding.getErasure().getQualifiedName(), annotation, TypeNodeOuterClass.TypeNode.TypeKind.Simple));
        else
            return of(getTypeNode(st.getName().getFullyQualifiedName(), annotation, TypeNodeOuterClass.TypeNode.TypeKind.Simple));

    }

    private static TypeGraph getTypeGraph(NameQualifiedType st) {
        final List<Annotation> ann = st.annotations();
        final List<String> annotation = ann.stream().map(a -> "@" + a.getTypeName().getFullyQualifiedName())
                .collect(toList());
        ITypeBinding iTypeBinding = st.getQualifier().resolveTypeBinding();
        if(iTypeBinding.getBinaryName() != null) {
            return of(getTypeNode(iTypeBinding.getQualifiedName() + "." + st.getName().toString(), annotation, TypeNodeOuterClass.TypeNode.TypeKind.Simple));
        }else{
            return of(getTypeNode(st.getQualifier().getFullyQualifiedName() + "." + st.getName().toString(), annotation, TypeNodeOuterClass.TypeNode.TypeKind.Simple));
        }

    }

    private static TypeGraph getTypeGraph(QualifiedType st) {
        final List<Annotation> ann = st.annotations();
        final List<String> annotation = ann.stream().map(a -> "@" + a.getTypeName().getFullyQualifiedName())
                .collect(toList());

        ITypeBinding iTypeBinding = st.getQualifier().resolveBinding();
        if(iTypeBinding != null)
            return of(getTypeNode(iTypeBinding.getQualifiedName() + "." + st.getName().toString(), annotation, TypeNodeOuterClass.TypeNode.TypeKind.Simple));
        else
            return of(getTypeNode(st.getQualifier() + "." + st.getName().toString(), annotation, TypeNodeOuterClass.TypeNode.TypeKind.Simple));

    }

    private static TypeGraph getTypeGraph(ParameterizedType pt) {
        final List<Type> ps = pt.typeArguments();
        final List<TypeGraph> params = ps.stream().map(TypeGraphUtil::getTypeGraph).collect(toList());
        final TypeNodeOuterClass.TypeNode root = getTypeNode(TypeNodeOuterClass.TypeNode.TypeKind.Parameterized);
        return of(root).toBuilder()
                .putEdges("of", getTypeGraph(pt.getType()))
                .putAllEdges(IntStream.range(0, params.size()).mapToObj(x -> x)
                        .collect(toMap(x -> ("Param:" + x), x -> params.get(x))))
                .build();
    }

    private static TypeGraph getTypeGraphStripParam(WildcardType wt) {
        return getTypeGraph(wt.getBound());
    }


    private static TypeGraph getTypeGraph(PrimitiveType pt) {
        final List<Annotation> ann = pt.annotations();
        final List<String> annotation = ann.stream().map(a -> "@" + a.getTypeName().getFullyQualifiedName())
                .collect(toList());
        return of(getTypeNode(pt.getPrimitiveTypeCode().toString(), annotation, Primitive));
    }

    private static TypeNodeOuterClass.TypeNode getTypeNode(String name, List<String> annotations, TypeNodeOuterClass.TypeNode.TypeKind
            tk) {
        if (name.contains("\n")) {
            name = name.replaceAll("\n", "");
        }
        return TypeNodeOuterClass.TypeNode.newBuilder().setKind(tk).setName(name).setIsResolved(true).build();
    }

    private static TypeNodeOuterClass.TypeNode getTypeNode(TypeNodeOuterClass.TypeNode.TypeKind tk) {
        return TypeNodeOuterClass.TypeNode.newBuilder().setKind(tk).build();
    }

    private static TypeNodeOuterClass.TypeNode getTypeNode(TypeNodeOuterClass.TypeNode.TypeKind tk, List<String> annotataions) {
        return TypeNodeOuterClass.TypeNode.newBuilder().setKind(tk).build();
    }

    private static TypeGraph of(TypeNodeOuterClass.TypeNode root) {
        return TypeGraph.newBuilder().setRoot(root).build();
    }


    public static boolean prettyEqual(TypeGraph tg, TypeGraph tg2, List<String> imports) {
        if (!tg.getRoot().getKind().equals(tg2.getRoot().getKind()))
            return false;
        if (tg.getRoot().getKind().equals(Simple) || tg.getRoot().getKind().equals(Primitive)) {
            return tg.getRoot().getName().equals(tg2.getRoot().getName())
                    || tg.getRoot().getName().endsWith("." + tg2.getRoot().getName())
                    || tg2.getRoot().getName().endsWith("." + tg.getRoot().getName())
                    || imports.contains(tg.getRoot().getName())
                    || tg.getRoot().getName().startsWith("java.lang");
        } else if (tg.getRoot().getKind().equals(TypeNodeOuterClass.TypeNode.TypeKind.Parameterized)) {
            return prettyEqual(tg.getEdgesMap().get("of"), tg2.getEdgesMap().get("of"), imports)
                    && tg.getEdgesMap().entrySet().stream().filter(x -> x.getKey().contains("Param")).count() ==
                    tg2.getEdgesMap().entrySet().stream().filter(x -> x.getKey().contains("Param")).count()
                    && tg.getEdgesMap().entrySet().stream().filter(x -> x.getKey().contains("Param"))
                    .allMatch(x -> prettyEqual(x.getValue(), tg2.getEdgesMap().get(x.getKey()), imports));

        } else if (tg.getRoot().getKind().equals(TypeNodeOuterClass.TypeNode.TypeKind.Array)) {
            return prettyEqual(tg.getEdgesMap().get("of"), tg2.getEdgesMap().get("of"), imports);
        } else if (tg.getRoot().getKind().equals(TypeNodeOuterClass.TypeNode.TypeKind.WildCard)) {
            if (tg.getEdgesMap().containsKey("extends") && tg2.getEdgesMap().containsKey("extends")) {
                return prettyEqual(tg.getEdgesMap().get("extends"), tg2.getEdgesMap().get("extends"), imports);
            }
            if (tg.getEdgesMap().containsKey("super") && tg2.getEdgesMap().containsKey("super")) {
                return prettyEqual(tg.getEdgesMap().get("super"), tg2.getEdgesMap().get("super"), imports);
            }
            if (tg.getEdgesCount() == 0 && tg2.getEdgesCount() == 0)
                return true;
        } else if (tg.getRoot().getKind().equals(TypeNodeOuterClass.TypeNode.TypeKind.Union))
            return false;

        return false;
    }


    private static TypeGraph getTypeGraph(WildcardType wt) {
        final List<Annotation> ann = wt.annotations();
        final List<String> annotation = ann.stream().map(a -> "@" + a.getTypeName().getFullyQualifiedName())
                .collect(toList());
        final TypeNodeOuterClass.TypeNode root = getTypeNode(TypeNodeOuterClass.TypeNode.TypeKind.WildCard, annotation);
        TypeGraph.Builder bldr = of(root).toBuilder();
        if(wt.getBound()!=null){
            bldr.putEdges(wt.isUpperBound() ? "extends" : "super", getTypeGraph(wt.getBound()));
        }
        return bldr.build();

    }


    private static TypeGraph getTypeGraph(ArrayType at){
        final List<Dimension> ds = at.dimensions();
        final List<String> annotation = new ArrayList<>();
        for(Dimension d : ds){
            List<Annotation> aa = d.annotations();
            for(Annotation a: aa){
                annotation.add("@" + a.getTypeName().getFullyQualifiedName());
            }
        }
        final TypeNodeOuterClass.TypeNode root = getTypeNode(TypeNodeOuterClass.TypeNode.TypeKind.Array, annotation);
        return of(root).toBuilder()
                .putEdges("of", getTypeGraph(at.getElementType())).build();
    }

}






