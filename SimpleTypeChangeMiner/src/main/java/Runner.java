import static com.main.parse.DownloadAllJarClient.downloadDependencies;
import static com.t2r.common.utilities.FileUtils.deleteDirectory;
import static com.t2r.common.utilities.FileUtils.parseCsv;
import static com.t2r.common.utilities.FileUtils.readFile;
import static com.t2r.common.utilities.GitUtil.filePathDiffAtCommit;
import static com.t2r.common.utilities.GitUtil.getCommits;
import static com.t2r.common.utilities.GitUtil.isFileAffected;
import static com.t2r.common.utilities.GitUtil.tryToClone;
import static com.t2r.common.utilities.ProtoUtil.ReadWriteAt;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.eclipse.jgit.revwalk.RevSort.COMMIT_TIME_DESC;

import com.main.parse.DownloadAllJarClient;
import com.t2r.common.models.refactorings.CommitInfoOuterClass.CommitInfo;
import com.t2r.common.models.refactorings.CommitInfoOuterClass.CommitInfo.Builder;
import com.t2r.common.models.refactorings.CommitInfoOuterClass.CommitInfo.DependencyPair;
import com.t2r.common.models.refactorings.CommitInfoOuterClass.CommitInfo.DependencyUpdate;
import com.t2r.common.models.refactorings.CommitInfoOuterClass.CommitInfo.FileDiff;
import com.t2r.common.models.refactorings.CommitInfoOuterClass.CommitInfo.JarInfo;
import com.t2r.common.models.refactorings.CommitInfoOuterClass.CommitInfo.RefactoringUrl;
import com.t2r.common.models.refactorings.ProjectOuterClass.Project;
import com.t2r.common.utilities.FileUtils;
import com.t2r.common.utilities.GitUtil;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.refactoringminer.LocationFor;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;


public class Runner {

    private static final Logger LOGGER = Logger.getLogger("Runner");
    static FileHandler fh;
    public static Properties prop;
    public static Path pathToCorpus;
    public static Path projectList;
    public static Function<String,Path> projectPath;
    public static Date epochStart;
    public static Path outputFolder;
    public static ReadWriteAt readWriteProtos;
    public static ReadWriteAt readWriteCleanProtos;
    public static String mavenHome;

    static{
        try {
            fh = new FileHandler("./Runner.log");
            fh.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fh);
            prop = new Properties();
            InputStream input = new FileInputStream("paths.properties");
            prop.load(input);
            pathToCorpus = Paths.get(prop.getProperty("PathToCorpus"));
            projectList = pathToCorpus.resolve(prop.getProperty("InputProjects"));
            projectPath = p -> pathToCorpus.resolve("Project_"+p).resolve(p);
            epochStart = new SimpleDateFormat("YYYY-mm-dd").parse(prop.getProperty("epoch"));
            outputFolder = Paths.get(".").toAbsolutePath().resolve(prop.getProperty("output"));
            readWriteProtos = new ReadWriteAt(outputFolder.resolve("ProtosOut"));
            readWriteCleanProtos = new ReadWriteAt(outputFolder.resolve("CleanProtos"));
            mavenHome = prop.getProperty("mavenHome");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public static List<RevCommit> filterAlreadyProcessed(List<RevCommit> commits, String projectName){
        List<String> processedCommits = readWriteProtos.<CommitInfo>readAll("commits_" + projectName, "CommitInfo").stream().map(x->x.getSha()).collect(toList());
        return commits.stream().filter(x -> !processedCommits.contains(x.getId().getName())).collect(toList());
    }


    public static void main(String a[]) {

        List<Tuple2<String, String>> projects = parseCsv(projectList, x -> Tuple.of(x[0], x[1]));

        List<Tuple2<Tuple2<String, String>, Git>> projectsGit =
                projects.stream()
                        .map(x -> Tuple.of(x, tryToClone(x._2(), projectPath.apply(x._1()))))
                        .filter(x -> x._2().isSuccess())
                        .map(x->x.map2(Try::get)).collect(toList());
//                        .map(x -> Tuple.of(x, getCommits(x._2(), COMMIT_TIME_DESC, epochStart, new ArrayList<>(), new ArrayList<>())))
//                        .collect(toList());

        List<Project> alreadyProcessed = readWriteProtos.<Project>readAll("projects", "Project");

        List<String> ignoreCommits = Arrays.asList("11cc13b043c9d73c49134f27aef5e4c05dc6f30a","927c61205c818fcfd3c743bcb8ddea47a8b04f7f");

        projectsGit.parallelStream().forEach(prc -> {
            LOGGER.info("Analysing project : " + prc._1()._1());
            Git git = prc._2();
            List<RevCommit> commits = getCommits(prc._2(), COMMIT_TIME_DESC, epochStart, new ArrayList<>(), new ArrayList<>());
            Project project = getProject(prc._1(), commits.size());
            GitHistoryRefactoringMiner g = new GitHistoryRefactoringMinerImpl();
            Set<JarInfo> jarsBefore = new HashSet<>();
            int counter = 0;

            if(alreadyProcessed.stream().noneMatch(x->x.getName().equals(project.getName())))
                readWriteProtos.write(project, "projects", true);

            List<RevCommit> commitstoAnalyze = filterAlreadyProcessed(commits, prc._1()._1()).stream()
                    .filter(x-> ignoreCommits.stream().noneMatch(s -> s.equals(x.getId().getName())))
                    .collect(toList());

            for(var commit: commitstoAnalyze) {
                String sha = commit.toObjectId().getName();
//                if (alreadyProcessed.values().stream().flatMap(Collection::stream).noneMatch(x -> x.getSha().equals(sha))) {
                LOGGER.info("Analysing commit: " + sha);
                ChangeMiner ct = new ChangeMiner(prc._1()._2());
                try {
                    g.detectAtCommit(git.getRepository(), sha, ct, 120);
                    CommitInfo c = getCommitInfo(prc._2(), sha, counter, jarsBefore, ct.getRefactoringsReported(), ct.getException());
                    readWriteProtos.write(c, "commits_" + prc._1()._1(), true);
                    counter += 1;
                    jarsBefore = new HashSet<>(c.getDependenciesList());
                }
                catch (Exception e){
                    LOGGER.severe("Could not analyze commit: " + sha);
                    LOGGER.severe(e.toString());
                    LOGGER.severe(e.getStackTrace().toString());
                    CommitInfo c = getCommitInfo(prc._2(), sha, counter, jarsBefore, new ArrayList<>(),e.toString());
                    readWriteProtos.write(c, "commits_" + prc._1()._1(), true);
                    counter += 1;
                }
                catch (Throwable t) {
                    LOGGER.severe("Could not analyze commit: " + sha);
                    LOGGER.severe(t.toString());
                    LOGGER.severe(t.getStackTrace().toString());
                    CommitInfo c = getCommitInfo(prc._2(), sha, counter, jarsBefore, new ArrayList<>(),t.toString());
                    readWriteProtos.write(c, "commits_" + prc._1()._1(), true);
                }
                LOGGER.info("Finished analysing commit: " + sha);
                LOGGER.info("------------------------------------------------------------------");

            }
            LOGGER.info("Finished analysing project: " + prc._1()._1());
            LOGGER.info("====================================================================================");


        });
    }



    public static Project getProject(Tuple2<String, String> pr, long n){
        return Project.newBuilder().setName(pr._1()).setUrl(pr._2()).setTotalCommits(n).build();
    }


    private static CommitInfo getCommitInfo(Git git, String commit, int counter, Set<JarInfo> depsBefore, List<CommitInfo.Refactoring> refactorings, String exception){
        final Map<ChangeType, List<Tuple2<String, String>>> diffs = filePathDiffAtCommit(git, commit);
        Builder cmt = CommitInfo.newBuilder().setSha(commit).setCounter(counter);

        FileDiff fileDiff = FileDiff.newBuilder().setFilesAdded(Optional.ofNullable(diffs.get(ChangeType.ADD)).map(List::size).orElse(0))
                .setFilesRemoved(Optional.ofNullable(diffs.get(ChangeType.DELETE)).map(List::size).orElse(0))
                .setFilesRenamed(Optional.ofNullable(diffs.get(ChangeType.RENAME)).map(List::size).orElse(0))
                .setFilesModified(Optional.ofNullable(diffs.get(ChangeType.MODIFY)).map(List::size).orElse(0)).build();
        cmt.setFileDiff(fileDiff);

        if(isFileAffected(git,commit,fileName -> fileName.endsWith("pom.xml"))){
            LOGGER.info("POM Affected");
            Set<JarInfo> depsCurr = getDependenciesFromEffectivePom(commit, git.getRepository());
            Optional<DependencyUpdate> upd = getDepUpd(depsBefore, depsCurr);
            if(!upd.isPresent()) {
                cmt.addAllDependencies(depsBefore);
            }else{
                cmt.setDependencyUpdate(upd.get());
                cmt.addAllDependencies(depsCurr);
                downloadDependencies("/Output/dependencies/", depsCurr.stream().map(x->String.join(":", x.getArtifactID(),x.getGroupID(), x.getVersion())).collect(toSet()));
            }
        }else{
            cmt.addAllDependencies(depsBefore);
        }

        cmt.addAllRefactorings(refactorings);

        if(!exception.isEmpty()) {
            LOGGER.warning(exception);
            cmt.setException(exception);
        }

        cmt.setIsTypeChangeReported(refactorings.stream().anyMatch(r -> r.getName().contains("Type")));

        return cmt.build();

    }








    private static class ChangeMiner extends RefactoringHandler {

        private Map<String,List<Refactoring>> refactoringsReportedWithLocation = new HashMap<>();
        private List<CommitInfo.Refactoring> refactoringsReported = new ArrayList<>();

        private String exception = "";
        private String cloneLink;

        public ChangeMiner(String cloneLink){
            this.cloneLink = cloneLink;
        }


        public Map<String,RefactoringUrl> getRefactoringUrl(List<Refactoring> rs, String c){
            return rs.stream().collect(toMap(r->r.toString(), r -> {
                if (r instanceof LocationFor) {
                    LocationFor l = (LocationFor) r;
                    String url = cloneLink.replace(".git", "/commit/" + c + "?diff=split#diff-");
                    return l.getUrlsToElement(Tuple.of(url, url))
                            .apply((lhs, rhs) -> (RefactoringUrl.newBuilder().setLhs(lhs).setRhs(rhs).build()));
                }
                return RefactoringUrl.getDefaultInstance();
            }, (a,b) -> a));
        }



        public List<CommitInfo.Refactoring> getRefactoring(String c){
            return refactoringsReportedWithLocation.entrySet()
                        .stream().map(x-> CommitInfo.Refactoring.newBuilder().setName(x.getKey()).setOccurences(x.getValue().size())
                    .putAllDescriptionAndurl(getRefactoringUrl(x.getValue(),c )).build()).collect(toList());
        }
        @Override
        public void handle(String commit, List<Refactoring> refactorings) {
            refactoringsReportedWithLocation = refactorings.stream().collect(groupingBy(x->x.getName()));
            refactoringsReported = getRefactoring(commit);
            LOGGER.info(refactoringsReported.stream().map(x -> x.getName() + "\t" + x.getOccurences()).collect(Collectors.joining("\n")));

        }

        @Override
        public void handleException(String commitId, Exception e) {
            LOGGER.warning(e.toString());
            exception = e.toString();
        }

        public List<CommitInfo.Refactoring> getRefactoringsReported() {
            return refactoringsReported;
        }

        public String getException() {
            return exception;
        }
    }

    private static Set<JarInfo> getDependenciesFromEffectivePom(String commit, Repository repo){

        Set<String> deps = generateEffectivePom(commit, repo)
                .map(x -> DownloadAllJarClient.getDependencies(x)).orElse(new HashSet<>());
        LOGGER.info("Generated Effective pom");

        return deps.stream().map(x->x.split(":"))
                .filter(x->x.length == 3)
                .map(dep->JarInfo.newBuilder()
                        .setArtifactID(dep[0]).setGroupID(dep[1])
                        .setVersion(dep[2]).build())
                .collect(toSet());
    }

    private static Optional<String> generateEffectivePom(String commitID, Repository repo) {

        Map<Path, String> poms = GitUtil.populateFileContents(repo, commitID, x -> x.endsWith("pom.xml"));
        Path p = outputFolder.resolve("tmp").resolve(commitID);

        FileUtils.materializeAtBase(p, poms);

        Path effectivePomPath = p.resolve("effectivePom.xml");

        if(!effectivePomPath.toFile().exists()) {
            InvocationRequest request = new DefaultInvocationRequest();
            request.setPomFile(new File(p.resolve("pom.xml").toAbsolutePath().toString()));
            request.setGoals(Arrays.asList("help:effective-pom", "-Doutput=" + effectivePomPath.toAbsolutePath().toString()));
            Invoker invoker = new DefaultInvoker();
            invoker.setMavenHome(new File(mavenHome));
            try {
                InvocationResult result = invoker.execute(request);
                if (result.getExitCode() != 0) {
                    System.out.println("Build Failed");
                    System.out.println("Could not generate effective pom");
                    return Optional.empty();
                }
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        String effectivePomPathContent = readFile(effectivePomPath);
        deleteDirectory(p);
        return Optional.of(effectivePomPathContent);
    }




    public static Optional<CommitInfo.DependencyUpdate> getDepUpd (Set<JarInfo> before, Set<JarInfo> after){
        Set<JarInfo> beforeMinusAfter = before.stream().filter(x -> after.stream().noneMatch(z->isSameDependency(z,x))).collect(toSet());
        Set<JarInfo> afterMinusBefore = after.stream().filter(x -> before.stream().noneMatch(z->isSameDependency(z,x))).collect(toSet());
        Set<DependencyPair> updates = versionUpdates(before, after);
        if(beforeMinusAfter.isEmpty() && afterMinusBefore.isEmpty())
            return Optional.empty();
        return Optional.of(DependencyUpdate.newBuilder()
                .addAllUpdate(updates)
                .addAllRemoved(beforeMinusAfter.stream()
                        .filter(x->updates.stream().map(DependencyPair::getBefore).noneMatch(d -> isSameDependency(x,d))).collect(toSet()))
                .addAllAdded(afterMinusBefore.stream()
                        .filter(x->updates.stream().map(DependencyPair::getAfter).noneMatch(d -> isSameDependency(x,d))).collect(toSet()))
                .build());
    }

    public static Set<DependencyPair> versionUpdates(Set<JarInfo> before, Set<JarInfo> after){
        return before.stream()
                .flatMap(x-> after.stream()
                        .filter(a -> a.getArtifactID().equals(x.getArtifactID()) && a.getGroupID().equals(x.getGroupID()) && !a.getVersion().equals(x.getVersion())).findFirst()
                        .map(d -> DependencyPair.newBuilder().setBefore(x).setAfter(d).build()).stream())
                .collect(toSet());
    }

    public static boolean isSameDependency(JarInfo b4, JarInfo after){
        return b4.getArtifactID().equals(after.getArtifactID()) && b4.getGroupID().equals(after.getGroupID())
                && b4.getVersion().equals(after.getVersion());
    }



}
