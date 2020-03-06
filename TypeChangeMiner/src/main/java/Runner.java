import com.t2r.common.models.refactorings.CommitInfoOuterClass.CommitInfo;
import com.t2r.common.models.refactorings.ProjectOuterClass.Project;
import com.t2r.common.models.refactorings.TypeChangeCommitOuterClass.TypeChangeCommit;
import com.t2r.common.utilities.ProtoUtil.ReadWriteAt;
import io.vavr.Tuple;
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static com.t2r.common.utilities.GitUtil.findCommit;
import static com.t2r.common.utilities.GitUtil.tryToClone;
import static java.util.stream.Collectors.toList;
import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;

public class Runner {
    private static final Logger LOGGER = Logger.getLogger("Runner");
    static FileHandler fh;
    public static Properties prop;
    public static Path pathToCorpus;
    public static Path projectList;
    public static Function<String,Path> projectPath;
    public static Date epochStart;
    public static Path outputFolder;
    public static ReadWriteAt readWriteInputProtos;
    public static Path pathToInput;
    public static Path pathToDependencies;
    public static ReadWriteAt readWriteOutputProtos;
    public static ReadWriteAt readWriteCodeMappingProtos;
    public static ReadWriteAt readWriteVerificationProtos;
    public static ReadWriteAt readWriteMigrationProtos;
    public static GraphTraversalSource gr;

    static{
        try {
            gr = traversal().withRemote(DriverRemoteConnection.using("localhost", 8182, "g"));
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
            pathToInput = Paths.get(".").toAbsolutePath().resolve(prop.getProperty("PathToInput"));
            readWriteInputProtos = new ReadWriteAt(pathToInput.resolve("ProtosOut"));
            readWriteOutputProtos = new ReadWriteAt(outputFolder);
            readWriteCodeMappingProtos = new ReadWriteAt(outputFolder.resolve("CodeMapping"));
            readWriteMigrationProtos = new ReadWriteAt(outputFolder.resolve("Migration"));
            readWriteVerificationProtos = new ReadWriteAt(outputFolder.resolve("Verification"));
            pathToDependencies = pathToInput.resolve("dependencies");
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args)  {
       readWriteInputProtos.<Project>readAll("Projects", "Project").stream()
//                .filter( p -> p.getName().contains("guava"))
            .map(prc -> Tuple.of(prc, readWriteInputProtos.<CommitInfo>readAll("commits_" + prc.getName(), "CommitInfo")))
            .map(x -> Tuple.of(x,tryToClone(x._1().getUrl(), projectPath.apply(x._1().getName()).toAbsolutePath())))
            .forEach(p -> {
                List<String> analyzedCommits = readWriteOutputProtos.<TypeChangeCommit>readAll("TypeChangeCommit_" + p._1()._1().getName(), "TypeChangeCommit")
                        .stream().map(x -> x.getSha()).collect(toList());
                if (p._2().isSuccess()){
                    System.out.println("Analysing " + p._1()._1());
                    p._1()._2().stream().filter(x->x.getRefactoringsCount() > 0 && x.getIsTypeChangeReported())
//                            .filter(x -> !analyzedCommits.contains(x.getSha()))
                            .map(x -> Tuple.of(x,findCommit(x.getSha(), p._2.get().getRepository())))
                            .filter(x->x._2().isPresent())
                       //     .filter(x -> x._2().map(r -> r.getId().getName().equals("6151fec89a02ef41e499c10fd3732862a06e8be0")).orElse(false))
                            .forEach(c -> {
                                GitHistoryRefactoringMinerImpl gitHistoryRefactoringMiner = new GitHistoryRefactoringMinerImpl(gr);
                                final ChangeTypeMiner ctm = new ChangeTypeMiner(p._1()._1());
                                gitHistoryRefactoringMiner
                                        .mineTypeChange(p._2.get(), c._1(), ctm, pathToDependencies, p._1()._1().getUrl());
                            });
                }
            });


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
