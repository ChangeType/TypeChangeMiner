package com.t2r.common.utilities;

import static com.t2r.common.utilities.FileUtils.createFolderIfAbsent;
import static com.t2r.common.utilities.FileUtils.createIfAbsent;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.control.Try;

public class GitUtil {


    /**
     *
     * @param cloneLink Git clone Url
     * @param path Where to clone or find
     * @return Git repository
     */
    public static Try<Git> tryToClone(String cloneLink, Path path) {
        createFolderIfAbsent(path);
        return Try.of(() -> Git.open(path.toFile()))
                .onFailure(e -> System.out.println("Did not find " + cloneLink + " at" + path.toString()))
                .orElse(Try.of(() ->
                        Git.cloneRepository().setURI(cloneLink).setDirectory(path.toFile()).call()))
                .onFailure(e -> System.out.println("Could not clone " + cloneLink));

    }


    /**
     *
     * @param git The Git repository
     * @param order order for output commits
     * @param fromDate first commit date
     * @return list of @RevCommit
     */
    public static List<RevCommit> getCommits(Git git, RevSort order, Date fromDate, List<String> except, List<String> only) {
        List<RevCommit> commits = Try.of(() -> {
            RevWalk walk = new RevWalk(git.getRepository());
            walk.markStart(walk.parseCommit(git.getRepository().resolve("HEAD")));
            walk.sort(order);
            walk.setRevFilter(CommitTimeRevFilter.after(fromDate));
            return walk;
        })
                .map(walk -> {
                    Iterator<RevCommit> iter = walk.iterator();
                    List<RevCommit> l = new ArrayList<>();
                    while (iter.hasNext()) {
                        l.add(iter.next());
                    }
                    walk.dispose();
                    return l;
                })
                .onSuccess(l -> System.out.println(l.size() + " number of commits found for " + git.getRepository().getDirectory().getParentFile().getName()))
                .onFailure(Throwable::printStackTrace)

                .getOrElse(new ArrayList<>())

                .stream()
                .filter(x -> !except.contains(x.getId().getName()))
                .filter(x -> only.isEmpty() || only.contains(x.getId().getName()))
                .collect(toList());
        Collections.reverse(commits);
        return commits;
    }

    /**
     *
     * @param git the Git repository
     * @param c the commit
     * @return DiffEntry for the commit
     */
    public static List<DiffEntry> getDiffEntries(Git git, RevCommit c)  {
        try {

            List<DiffEntry> ds = git.diff().setOldTree(prepareTreeParser(c.getParent(0).getId().getName(), git.getRepository()))
                    .setNewTree(prepareTreeParser(c.getId().getName(), git.getRepository()))
                    .call();
            return ds;
        }catch (Exception e){
            System.out.println("Could not generate git diff");
            e.printStackTrace();
            return new ArrayList<>();
        }
    }


    public static Map<DiffEntry.ChangeType, List<Tuple2<String, String>>> filePathDiffAtCommit(Git git, String c){

        return findCommit(c, git.getRepository())
                .map(cmt -> getDiffEntries(git, cmt).stream()
                        .map(x -> Tuple.of(x.getChangeType(), x.getOldPath(), x.getNewPath()))
                        .collect(groupingBy(Tuple3::_1
                                , collectingAndThen(toList(), l -> l.stream().map(x -> Tuple.of(x._2(), x._3())).collect(toList())))))
                .orElse(new HashMap<>());
    }


    /**
     *
     * @param git the Git repository
     * @param c the commit
     * @return is successful checkout
     */
    public static boolean checkoutCommit(Git git, RevCommit c) {
        return Try.of(() -> git.checkout().setName(c.getId().getName()).call())
                .onFailure(Throwable::printStackTrace).isSuccess();
    }


   private static CanonicalTreeParser prepareTreeParser(String sha, Repository repository) throws
            IOException {
        RevWalk walk = new RevWalk(repository) ;
        RevCommit commit = walk.parseCommit(repository.resolve(sha));
        RevTree tree = walk.parseTree(commit.getTree().getId());
        CanonicalTreeParser treeParser = new CanonicalTreeParser();
        ObjectReader reader = repository.newObjectReader();
        treeParser.reset(reader, tree.getId());
        walk.dispose();
        return treeParser;
    }


    public static Optional<RevCommit> findCommit(String SHAId, Repository repo) {
        return Try.of(() -> new RevWalk(repo))
                .flatMap(x->Try.of(() -> x.parseCommit(ObjectId.fromString(SHAId))))
                .onFailure(e -> e.printStackTrace())
                .toJavaOptional();
    }

    /**
     *
     * @param repository Git repo
     * @param cmt the particular commit
     * @param pred matcher for files to populate the content for
     * @return filePath * content
     */
    public static Map<Path, String> populateFileContents(Repository repository, String cmt,
                                                          Predicate<String> pred) {
        Map<Path, String> fileContents = new HashMap<>();
        Optional<RevCommit> commit = findCommit(cmt, repository);
        if(commit.isPresent()) {
            RevTree parentTree = commit.get().getTree();
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(parentTree);
                treeWalk.setRecursive(true);
                while (treeWalk.next()) {
                    String pathString = treeWalk.getPathString();
                    if (pred.test(pathString)) {
                        ObjectId objectId = treeWalk.getObjectId(0);
                        ObjectLoader loader = repository.open(objectId);
                        StringWriter writer = new StringWriter();
                        IOUtils.copy(loader.openStream(), writer);
                        fileContents.put(Paths.get(pathString), writer.toString());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return fileContents;
    }


    public static boolean isFileAffected(Git git, String c, Predicate<String> fileMatcher){
        return filePathDiffAtCommit(git, c).values().stream()
                .flatMap(x -> x.stream())
                .anyMatch(x -> (x._1()!= null && fileMatcher.test(x._1())) || (x._2()!= null && fileMatcher.test(x._2())));
    }


}