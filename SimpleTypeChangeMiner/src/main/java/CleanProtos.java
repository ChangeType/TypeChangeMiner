import com.t2r.common.models.refactorings.CommitInfoOuterClass;
import com.t2r.common.models.refactorings.CommitInfoOuterClass.CommitInfo;
import com.t2r.common.models.refactorings.ProjectOuterClass;
import com.t2r.common.utilities.GitUtil;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.ArrayList;
import java.util.List;

import static com.t2r.common.utilities.FileUtils.parseCsv;
import static com.t2r.common.utilities.GitUtil.*;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.revwalk.RevSort.COMMIT_TIME_DESC;

public class CleanProtos {

    /**
     * Remove merged commits
     * Remove duplicate commits
     * @param a
     */
    public static void main(String a[]){
        List<Tuple2<String, String>> projects = parseCsv(Runner.projectList, x -> Tuple.of(x[0], x[1]));

        List<Tuple2<Tuple2<String, String>, Git>> projectsGit =
                projects.stream()
                        .map(x -> Tuple.of(x, tryToClone(x._2(), Runner.projectPath.apply(x._1()))))
                        .filter(x -> x._2().isSuccess())
                        .map(x->x.map2(Try::get)).collect(toList());

        projectsGit.forEach(prc -> {
            List<String> noMergeCommits = getCommits(prc._2(), COMMIT_TIME_DESC, Runner.epochStart, new ArrayList<>(), new ArrayList<>())
                    .stream().map(x -> x.getId().getName()).collect(toList());
            List<String> onlyMerges = new ArrayList<>();
//                    getMergedCommits(prc._2(), COMMIT_TIME_DESC, Runner.epochStart, new ArrayList<>(), new ArrayList<>())
//                    .stream().map(x -> x.getId().getName()).collect(toList());

            noMergeCommits = noMergeCommits.stream().filter(x -> !onlyMerges.contains(x)).collect(toList());

            System.out.println(noMergeCommits.size());
            List<CommitInfo> protoCommits = Runner.readWriteProtos.readAll("commits_" + prc._1()._1(), "CommitInfo");
            System.out.println(protoCommits.size());
            protoCommits = io.vavr.collection.List.ofAll(protoCommits)
                    .distinctBy(CommitInfo::getSha)
                    .toJavaList();
            protoCommits = protoCommits.stream().filter(x -> !onlyMerges.contains(x.getSha()))
                    .collect(toList());
            System.out.println(protoCommits.size());
            ProjectOuterClass.Project project = Runner.getProject(prc._1(), noMergeCommits.size());
            Runner.readWriteCleanProtos.write(project, "Projects", true);
            protoCommits.stream()
                    .forEach(c -> Runner.readWriteCleanProtos.write(c,"commits_" + project.getName(), true));
        });



    }

}
