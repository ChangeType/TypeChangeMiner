package com.main.parse;


import com.library.source.DownloadLibrary;

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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;


public class DownloadAllJarClient {

//    static String pathToSaveJAVALibrary=   "/librariesClasses/jar";
//    private static String pathToCorpus = "/Users/ameya/FinalResults/diffTools/Corpus/";




    public static Set<String> getDependencies(String content){
        return new CollectorClient().listOfJavaProjectLibraryFromEffectivePom(content);
    }


    public static void downloadDependencies(String s,Set<String> dependencies){
        DownloadLibrary downloader = new DownloadLibrary(s);
        dependencies.forEach(dep ->  downloader.download(dep, false));
    }

    public static void run(String projectName, String url){
//        String s = pathToCorpus + "Project_" + projectName + "/";
//            try {
//                if (Files.exists(Paths.get(s))) {
//                    Files.walk(Paths.get(s))
//                            .sorted(Comparator.reverseOrder())
//                            .map(Path::toFile)
//                            .forEach(File::delete);
//                }
//                if (!Files.exists(Paths.get(s))) {
//                    Files.createDirectory(Paths.get(s));
//                    Files.createDirectory(Paths.get(s + projectName));
//                    Git.cloneRepository().setURI(url).setDirectory(new File(s + projectName)).call();
//                }
//
//                String projectPath = s;
//                Git g = Git.open(new File(projectPath + projectName));
//                TerminalCommand terminalCommand = new TerminalCommand();
//                terminalCommand.createFolder(Paths.get(".").toAbsolutePath().normalize().toString() + pathToSaveJAVALibrary + "/tfs");
//                List<JarInfo> latestJars = new ArrayList<>();
//                StringBuilder output = new StringBuilder();
//
//                if (Files.exists(Paths.get(projectPath + "CommitJars.csv")))
//                    return;
//
//                for (RevCommit c : getCommits(g, RevSort.COMMIT_TIME_DESC)) {
//                    if (pomAffected(g, c) || latestJars.isEmpty()) {
//                        List<JarInfo> jars = new ArrayList<>();
//                        String versionLibraries = new CollectorClient().listOfJavaProjectLibraryFromPom(getPomContent(g.getRepository(), c, projectName + "/pom.xml"));
//                        if (versionLibraries.isEmpty()) {
//                            output.append(String.join(",", "", "", "", c.getId().getName()))
//                                    .append("\n");
//                        }
//                        for (String libraryInfo : versionLibraries.split(",")) {
//                            Optional<JarInfo> jio = getJarInfo(libraryInfo);
//                            try {
//                                if (jio.isPresent()) {
//                                    JarInfo ji = jio.get();
//                                    new DownloadLibrary(pathToSaveJAVALibrary).download(libraryInfo, false);
//                                    output.append(String.join(",", ji.artifactID, ji.groupid, ji.version, c.getId().getName())).append("\n");
//                                    jars.add(ji);
//                                } else {
//                                    output.append(String.join(",", "", "", "", c.getId().getName())).append("\n");
//                                }
//                            } catch (Exception e) {
//                                System.out.println("Could not analyse jar" + e.getMessage());
//                                output.append(String.join(",", "", "", "", c.getId().getName())).append("\n");
//                            }
//                        }
//                        latestJars = jars;
//                    } else {
//                        for (JarInfo ji : latestJars) {
//                            output.append(String.join(",", ji.artifactID, ji.groupid, ji.version, c.getId().getName()))
//                                    .append("\n");
//                        }
//                    }
//                }
//
//                FileWriter fw = new FileWriter(projectPath + "CommitJars.csv");
//                fw.write(output.toString());
//                fw.close();
//
//                System.out.println("=========Done ============");
//
//
//            } catch (Exception e) {
//                e.printStackTrace();
//                System.out.println("Could not find project " + projectName + " at  " + pathToCorpus);
//            }
//
    }



    private static Optional<JarInfo> getJarInfo(String libraryInfo) {
        String[] LibraryInfos = libraryInfo.split(":");
        if(LibraryInfos.length ==3)
            return Optional.of(new JarInfo(LibraryInfos[1], LibraryInfos[0], LibraryInfos[2]));
        return Optional.empty();
    }

    public static String getPomContent(Repository repository, RevCommit commit){
        final RevTree parentTree = commit.getTree();
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(parentTree);
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                String pathString = treeWalk.getPathString();
                if(pathString.equals("pom.xml")){
                    ObjectId objectId = treeWalk.getObjectId(0);
                    ObjectLoader loader = repository.open(objectId);
                    StringWriter writer = new StringWriter();
                    IOUtils.copy(loader.openStream(), writer);
                    return writer.toString();
                    }
                }
            }
        catch(Exception e){
            e.printStackTrace();
        }
        return "";
    }


    public static List<RevCommit> getCommits(Git git, RevSort order) {
        SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd");
        String input = "2015-01-01" ;
        try {
            RevWalk walk = new RevWalk(git.getRepository());
            walk.markStart(walk.parseCommit(git.getRepository().resolve("HEAD")));
            walk.sort(order);
            walk.setRevFilter(CommitTimeRevFilter.after(ft.parse(input)));
            Iterator<RevCommit> iter = walk.iterator();
            List<RevCommit> l = new ArrayList<>();
            while(iter.hasNext()){
                l.add(iter.next()); }
            walk.dispose();
            System.out.println("Total number of commits found : " + l.size());
            return l;
        }catch (Exception e){
            System.out.println("Should not happen, could not fetch commits");
            return new ArrayList<>();
        }

    }


    public static boolean pomAffected(Git g, RevCommit c){
        try {
            return getDiffEntries(g.getRepository(), g, c)
                    .stream()
                    .map(x -> x.getOldPath())
                    .anyMatch(x -> x.equals("pom.xml"));
        }catch (Exception e){
            System.out.println("Could not get Diff");
            return false;
        }

    }

    public static List<DiffEntry> getDiffEntries(Repository repo, Git g, RevCommit c)  {
        try {
            List<DiffEntry> ds = g.diff().setOldTree(prepareTreeParser(c.getParent(0).getId().getName(), repo))
                    .setNewTree(prepareTreeParser(c.getId().getName(), repo))
                    .call();
            return ds;
        }catch (Exception e){
            System.out.println("Could not generate git diff");
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    public static CanonicalTreeParser prepareTreeParser(String sha, Repository repository) throws
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


    public static class JarInfo{
        public final String groupid;
        public final String artifactID;
        public final String version;

        public JarInfo(String groupid, String artifactID, String version) {
            this.groupid = groupid;
            this.artifactID = artifactID;
            this.version = version;
        }
    }



}