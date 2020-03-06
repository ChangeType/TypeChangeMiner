import com.t2r.common.models.refactorings.ProcessedCodeMappingsOuterClass.ProcessedCodeMappings;
import com.t2r.common.models.refactorings.ProcessedCodeMappingsOuterClass.ProcessedCodeMappings.RelevantStmtMapping;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

public class AnalyseProcessedCodeMappings {



    public static void main (String a[]){

        List<ProcessedCodeMappings> processedCodeMappings = Runner.readWriteCodeMappingProtos.readAll("ProcessedCodeMapping", "CodeMapping");
        long count = processedCodeMappings.size();
//        List<Tuple2<Tuple2<TypeGraphOuterClass.TypeGraph, TypeGraphOuterClass.TypeGraph>, Map<String, Long>>> tciMappings =
        List<RelevantStmtMapping> tciMappings = processedCodeMappings.stream()
                .flatMap(pc -> pc.getRelevantStmtsList().stream())
                .collect(toList());
        System.out.println(tciMappings.size());

        tciMappings = tciMappings.stream().filter(x -> x.getMappingCount() > 0).collect(toList());
        System.out.println(tciMappings.size());

        List<Set<String>> tciReplacements = tciMappings.stream()
                .map(l -> Stream.concat(l.getMappingList().stream().map(x -> x.getReplacement()),
                        (l.getB4().equals(l.getAfter()) ? Stream.empty() : Stream.of("\\percentVarRename"))).collect(toSet()))
                .collect(toList());

      long c = tciReplacements.stream().filter(x->x.isEmpty()).count();

        Map<String, Long> zz = tciReplacements.stream().flatMap(x -> x.stream()).collect(groupingBy(x -> x, counting()));

        System.out.println(zz);

//                .map(x -> Tuple.of(Tuple.of(x.getB4(), x.getAftr())
//                        , x.getRelevantStmtsList().stream()
//                                .peek(d -> {
//                                    if(d.getMappingList().isEmpty()){
//                                        if(!d.getB4().equals(d.getAfter()))
//                                            System.out.println();
//                                    }
//                                })
//                                .flatMap(r -> r.getMappingList().stream()).collect(toMap(m -> m.getReplacement(), m -> 1L, (t1 ,t2) -> 1L))))
//                .collect(toList());

//        var temp = tciMappings.stream()

//        long count = tciMappings.stream()
//                .filter(x -> !x._2().isEmpty()).count();
//        System.out.println("Total Type Changes with edit patterns = " + count);
//
//        var editPatGroupedByPattern = tciMappings.stream().flatMap(x -> x._2().entrySet().stream().map(Tuple::fromEntry))
//                .collect(groupingBy(x -> x._1(), summingLong(x -> x._2())));
//
//        var groupedByTypeChange = tciMappings.stream().flatMap(x -> x._2().keySet().stream().map(g -> Tuple.of(x._1(), g)))
//                .collect(groupingBy(g -> g._2(), counting()));
//
//
//        editPatGroupedByPattern.entrySet().forEach(x -> System.out.println(x.getKey() + ", " + x.getValue()));
//        System.out.println("##################################################################################################");
         zz.entrySet().forEach(x -> System.out.println("\\newcommand{" + x.getKey() + "TCI}{" + (double) x.getValue() / count + "\\%\\xspace"));
//
//        System.out.println(editPatGroupedByPattern);
    }


}
