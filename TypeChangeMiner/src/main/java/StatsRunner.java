import com.t2r.common.models.refactorings.CommitInfoOuterClass.CommitInfo;
import com.t2r.common.models.refactorings.JarInfoOuterClass;
import com.t2r.common.models.refactorings.ProjectOuterClass.Project;
import com.t2r.common.models.refactorings.TheWorldOuterClass.TheWorld;
import com.t2r.common.models.refactorings.TypeChangeCommitOuterClass;
import io.vavr.Tuple;
import org.eclipse.jgit.api.Git;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.refactoringminer.util.GitServiceImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.t2r.common.utilities.GitUtil.tryToClone;
import static java.util.stream.Collectors.toSet;

public class StatsRunner {
//    private static final Logger LOGGER = Logger.getLogger("Runner");
//    static FileHandler fh;
//    public static Properties prop;
//    public static Path pathToCorpus;
//    public static Path projectList;
//    public static Function<String,Path> projectPath;
//    public static Date epochStart;
//    public static Path outputFolder;
//    public static ReadWriteAt readWriteInputProtos;
//    public static Path pathToInput;
//    public static Path pathToDependencies;
//    public static ReadWriteAt readWriteOutputProtos;
////    public static GraphTraversalSource gr;
//
//    static{
//        try {
////            gr = traversal().withRemote(DriverRemoteConnection.using("localhost", 8182, "g"));
//            fh = new FileHandler("./Runner.log");
//            fh.setFormatter(new SimpleFormatter());
//            LOGGER.addHandler(fh);
//            prop = new Properties();
//            InputStream input = new FileInputStream("paths.properties");
//            prop.load(input);
//            pathToCorpus = Paths.get(prop.getProperty("PathToCorpus"));
//            projectList = pathToCorpus.resolve(prop.getProperty("InputProjects"));
//            projectPath = p -> pathToCorpus.resolve("Project_"+p).resolve(p);
//            epochStart = new SimpleDateFormat("YYYY-mm-dd").parse(prop.getProperty("epoch"));
//            outputFolder = Paths.get(".").toAbsolutePath().resolve(prop.getProperty("output"));
//            pathToInput = Paths.get(".").toAbsolutePath().resolve(prop.getProperty("PathToInput"));
//            readWriteInputProtos = new ReadWriteAt(pathToInput.resolve("ProtosOut"));
//            readWriteOutputProtos = new ReadWriteAt(outputFolder);
//            pathToDependencies = pathToInput.resolve("dependencies");
//        } catch (IOException | ParseException e) {
//            e.printStackTrace();
//        }
//    }

    public static void main(String[] args) {
        var ppp = new ArrayList<>(Runner.readWriteInputProtos.<Project>readAll("Projects", "Project"));
        ppp.parallelStream().forEach(z -> {
            var xz = Tuple.of(z, Runner.readWriteOutputProtos.<TypeChangeCommitOuterClass.TypeChangeCommit>readAll("TypeChangeCommit_" + z.getName(), "TypeChangeCommit"));
            var p = Tuple.of(xz, tryToClone(xz._1().getUrl(), Runner.projectPath.apply(xz._1().getName()).toAbsolutePath()));
            if (p._2().isSuccess()) {
                System.out.println("Analysing " + p._1()._1());
                var ccc = p._1()._2().stream()
//                        .filter(x -> x.getSha().equals("ed4ad2a431cd7c40c6f63d1107af6a350a10e462"))
                        .filter(x -> x.getTypeChangesCount() > 0).collect(Collectors.toList());
                Set<String> alreadyAnalyzed = Runner.readWriteOutputProtos.<TheWorld>readAll("TheWorld_" + z.getName(), "TheWorld")
                        .stream().map(x -> x.getSha()).collect(toSet());
                for (var c : ccc) {
                    if (!alreadyAnalyzed.contains(c.getSha())) {
                        Set<JarInfoOuterClass.JarInfo> allJars = Stream.concat(c.getDependencyUpdate().getUpdateList().stream().map(x -> x.getBefore()),
                                Stream.concat(c.getDependencyUpdate().getRemovedList().stream(), getDependencyList(c.getSha(), z.getName())))
                                .collect(toSet());
                        try {
                            System.out.println(c.getSha());
                            Set<String> classesAffected = c.getTypeChangesList().stream()
                                    .flatMap(x -> x.getTypeChangeInstancesList().stream()).map(x -> x.getCompilationUnit()).collect(toSet());
                            GitHistoryRefactoringMinerImpl ghi = new GitHistoryRefactoringMinerImpl();
                            Git g = p._2.get();
                            TheWorld stats = ghi.detectCodeStats(new GitServiceImpl(), g, allJars, c.getSha(), Runner.pathToDependencies, Runner.gr, classesAffected);
                            Runner.readWriteOutputProtos.write(stats, "TheWorld_" + p._1()._1().getName(), true);
                            System.out.println("---------------------=-=-------------------");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });


    }


    public static Stream<JarInfoOuterClass.JarInfo> getDependencyList(String sha, String project) {
        return Runner.readWriteInputProtos.<CommitInfo>readAll("commit_" + project, "CommitInfo")
                .stream().filter(x -> x.getSha().equals(sha))
                .map(CommitInfo::getDependenciesList).flatMap(Collection::stream);

    }

    /**
     *
     * Project x
     *    Pi
     *      Ci1 Ci2 Ci3
     *    Pj
     *      Cj1 Cj2 Cj3
     *    Pk
     *      Ck1 Ck2 Ck3
     *
     * Foo -> Bar
     * Ck1, Ck2 (Class level type migration ) in commit1 (66%)
     * Ck3 (Class level type migration ) in commit2 ... Package Level Migration
     *
     * Pj migrated in commit 3
     *  Project Level Migration
     *
     *
     * For every migration (Foo -> Bar)
     *  First commit when Foo -> Bar type change was performed
     *  Migration completes when last commit Foo exists or last of commit of project
     *  We analyse the subset of commits that contains at least one Foo -> Bar type change
     *      In all these commits we know all the Foos that exist after the change
     *      We can compute exact % of migration
     *            total number of Foo -> Bar / total number of Foo in LHS
     *
     *  Commit1
     *  Ck1 has 20 Foos, ck2 has 8 Foos, ck3 has 6 Foos, Cj1 has 40 Foos
     *  28/74
     *
     *  Map<Files, Foo>
     *
     *  Commit 1.5
     *
     *  Ck5 add 20 Foos
     *
     *  Commit2
     *
     *  6/66 Foo -> Bar
     *
     */


}
