package ca.concordia.jaranalyzer;

import static java.util.stream.Collectors.toMap;


import com.jasongoodwin.monads.Try;

import org.apache.commons.io.IOUtils;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.AbstractMap.SimpleImmutableEntry;

public class Util {





    public static Try<Git> tryCloningRepo(String projectName, String cloneLink, String path) {
        return Try.ofFailable(() -> Git.open(new File(path + projectName)))
                .onFailure(e -> System.out.println("Did not find " + projectName + " at" + path))
                .orElseTry(() ->
                        Git.cloneRepository().setURI(cloneLink).setDirectory(new File(path + projectName)).call())
                .onFailure(e -> System.out.println("Could not clone " + projectName));

    }

    public static Map<String, String> readProjects(String path){
        try {
            return Files.readAllLines(Paths.get(path)).parallelStream()
                    .map(e -> new SimpleImmutableEntry<>(e.split(",")[0], e.split(",")[1]))
                    .collect(toMap(e -> e.getKey(), e -> e.getValue()));
        }catch (Exception e){
            System.out.println("Could not read projects");
            throw new RuntimeException("Could not read projects");
        }
    }

    public static List<RevCommit> getCommits(Git git, RevSort order) {
        SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd");
        String input = "2015-01-01" ;
        return Try.ofFailable(() -> {
            RevWalk walk = new RevWalk(git.getRepository());
            walk.markStart(walk.parseCommit(git.getRepository().resolve("HEAD")));
            walk.sort(order);
            walk.setRevFilter(CommitTimeRevFilter.after(ft.parse(input)));
            return walk;
        })
                .map(walk -> {
                    Iterator<RevCommit> iter = walk.iterator();
                    List<RevCommit> l = new ArrayList<>();
                    while(iter.hasNext()){
                        l.add(iter.next()); }
                    walk.dispose();
                    return l;
                })
                .onSuccess(l -> System.out.println("Total number of commits found : " + l.size()))
                .onFailure(Throwable::printStackTrace)

                .orElse(new ArrayList<>());
    }

    public static CompilationUnit getCuFor(String content){
        ASTParser parser = ASTParser.newParser(AST.JLS11);
        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
        options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
        parser.setCompilerOptions(options);
        parser.setResolveBindings(false);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setStatementsRecovery(true);
        parser.setSource(content.toCharArray());
        return  (CompilationUnit)parser.createAST(null);
    }

    public static String getFileContent(Repository repository, TreeWalk treeWalk) throws IOException {
        ObjectId objectId = treeWalk.getObjectId(0);
        ObjectLoader loader = repository.open(objectId);
        StringWriter writer = new StringWriter();
        IOUtils.copy(loader.openStream(), writer);
        return Optional.ofNullable(writer.toString()).orElse("");
    }

}
