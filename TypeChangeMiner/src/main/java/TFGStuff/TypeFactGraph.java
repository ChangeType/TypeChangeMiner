package TFGStuff;


import com.google.common.graph.*;
import com.t2r.common.models.ast.IdentificationOuterClass.Identification;
import com.t2r.common.models.ast.TFGOuterClass.TFG;
import io.vavr.Tuple;
import io.vavr.Tuple2;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class TypeFactGraph<U>{


    private ImmutableValueGraph<U,String> tfg;

    private TypeFactGraph(MutableValueGraph<U,String> g){
        tfg = ImmutableValueGraph.copyOf(g);
    }


    public ImmutableValueGraph<U, String> get() {
        return tfg;
    }

    public static <U> TypeFactGraph<U> of(MutableValueGraph<U,String> g){
        return new TypeFactGraph<>(g);
    }

    /**
     *
     * @param f : function which transforms the underlying graph as guava's Mutable Value Graphs
     * @return : returns a TFG where this the transformation function 'f' has been applied on TFG
     */
    public TypeFactGraph<U> map(Function<MutableValueGraph<U,String>, MutableValueGraph<U,String>> f){
        return of(f.apply(Graphs.copyOf(tfg)));
    }

    public TypeFactGraph<U> flatMap(
            Function<MutableValueGraph<U,String>, TypeFactGraph<U>> f){
        return  f.apply(Graphs.copyOf(tfg));
    }

    public boolean existsBiDirectionalEdge(U u, U v){
        return tfg.edgeValue(u,v).isPresent() && tfg.edgeValue(v,u).isPresent();
    }

    /**
     *
     * @param fs : list of functions to be applied on tfg
     * @return returns List of
     */
    public TypeFactGraph<U> mergeMap(List<Function<MutableValueGraph<U,String>, MutableValueGraph<U, String>>> fs){
        return map(fs.stream().reduce(Function.identity(),Function::andThen));

    }

    public boolean isEmpty(){
        return tfg.nodes().size() == 0;
    }

    // Basic graph operations

    /**
     *
     * @param u : node to be added
     * @param <U> : Type of the nodes in TFG
     * @return : Returns a function which adds node 'u' to a graph.
     */
    public static <U> Function<MutableValueGraph<U,String>, MutableValueGraph<U,String>> addNode(U u){
        return  g -> { g.addNode(u);return g; };
    }

    /**
     *
     * @param a From node
     * @param b To Node
     * @param aTob Edge Label
     * @return : Returns a function which establishes an directed labelled edge between two nodes of a graph
     */
    public static <U> Function<MutableValueGraph<U,String>, MutableValueGraph<U,String>> u_v(U a, U b, String aTob) {
        return g -> {
            g.putEdgeValue(a, b, aTob);
            return g;
        };
    }

    /**
     *
     * @param a Node
     * @param b Node
     * @param aBbA Pair(Edge label from 'a' to 'b', Edge label from 'b' to 'a')
     * @return Returns a function which establishes directional labelled edges from 'a' to 'b' and from 'b' to 'a'
     */
    public static <U> Function<MutableValueGraph<U,String>, MutableValueGraph<U,String>> uV_vU(U a, U b, Tuple2<String, String> aBbA) {
        if(aBbA._1().equals(NO_EDGE) || aBbA._2().equals(NO_EDGE))
            return Function.identity();
        return u_v(a,b,aBbA._1()).andThen(u_v(b,a,aBbA._2()));
    }

    /**
     *
     * @param <U> Type of nodes
     * @return Returns a directed empty graph TFG
     */
    public static <U> TypeFactGraph<U> emptyTFG() {
        MutableValueGraph<U, String> graph = ValueGraphBuilder.directed().allowsSelfLoops(true).build();
        return new TypeFactGraph<>(graph);
    }

    /**
     *
     * @param a input graph for merge operation
     * @param b input graph for merge operation
     * @param <T> Type of Nodes in the graph
     * @return It merges the graph while preserving the edge labels.
     */
    public static <T> TypeFactGraph<T> mergeGraphs(TypeFactGraph<T> a, TypeFactGraph<T> b) {
        final BinaryOperator<TypeFactGraph<T>> merge = (gl, gr) -> {
            ImmutableValueGraph<T, String> g1 = gl.get();
            ImmutableValueGraph<T, String> g2 = gr.get();
            MutableValueGraph<T, String> graph = ValueGraphBuilder.directed().allowsSelfLoops(true).build();
            g1.edges().forEach(e -> graph.putEdgeValue(e.nodeU(), e.nodeV(), g1.edgeValue(e.nodeU(), e.nodeV()).get()));
            g2.edges().forEach(e -> graph.putEdgeValue(e.nodeU(), e.nodeV(), g2.edgeValue(e.nodeU(), e.nodeV()).get()));
            g1.nodes().forEach(graph::addNode);
            g2.nodes().forEach(graph::addNode);
            return TypeFactGraph.of(graph);
        };
        return nullHandleReduce(a,b,merge, emptyTFG());
    }

    /**
     *
     * @param tfg Input graph
     * @return Returns a com.google.common.graph.MutableValueGraph<T> of the tfg.
     */
    private static <T> MutableValueGraph<T,String> getMutableOf(TypeFactGraph<T> tfg){
        MutableValueGraph<T,String> gr = ValueGraphBuilder.directed().allowsSelfLoops(true).build();
        for(EndpointPair<T> uv : tfg.get().edges())
            gr.putEdgeValue(uv.nodeU(),uv.nodeV(), tfg.get().edgeValue(uv.nodeU(), uv.nodeV()).get());
        for(T t: tfg.get().nodes())
            gr.addNode(t);
        return gr;
    }

    /**
     *
     * @param tfg input graph for node replacement operation
     * @param replaceWith input pairs (replace * with)
     * @return This operation replaces the first element of the input pairs with their respective second
     * element. This operation preserves the edges, and then eliminates the 'replaced' element from the graph.
     */
    public static <T> TypeFactGraph<T> replace(TypeFactGraph<T> tfg, final List<Tuple2<T,T>> replaceWith) {
        MutableValueGraph<T,String> gr = getMutableOf(tfg);
        for(Tuple2<T,T> rw: replaceWith){
            final T replace = rw._1();
            final T with  = rw._2();
            final Set<T> succ = tfg.get().successors(replace);
            for(T s: succ){
                String edgeValue = tfg.get().edgeValue(replace,s).get();
                gr.putEdgeValue(with,s,edgeValue);
            }
            final Set<T> pred = tfg.get().successors(replace);
            for(T p: pred){
                String edgeValue = tfg.get().edgeValue(p,replace).get();
                gr.putEdgeValue(p,with,edgeValue);
            }
        }

        for(Tuple2<T,T> rw: replaceWith){
            gr.removeNode(rw._1());
        }
        return of(gr);
    }

    /**
     *
     * @param tfg input graph for edge remove operation
     * @param removeEdgeBetween : input pairs of nodes
     * @return returns a tfg with removed edges between the input pairs of nodes
     */
    public static <T> TypeFactGraph<T> removeBiDirectionalEdge(TypeFactGraph<T> tfg, List<Tuple2<T,T>> removeEdgeBetween) {
        MutableValueGraph<T, String> gr = getMutableOf(tfg);
        for(Tuple2<T,T> rw: removeEdgeBetween) {
            gr.removeEdge(rw._1(), rw._2());
            gr.removeEdge(rw._2(), rw._1());
        }
        return of(gr);
    }

    public static <T> TypeFactGraph<T> removeEdge(TypeFactGraph<T> tfg, Tuple2<T,T> rw) {
        MutableValueGraph<T, String> gr = getMutableOf(tfg);
        gr.removeEdge(rw._1(), rw._2());
        return of(gr);
    }

    /**
     *
     * @param tfg input graph
     * @param elements elements to be removed
     * @param <T>
     * @return returns a graph with the elements removed
     */
    public static <T> TypeFactGraph<T> removeNodes(TypeFactGraph<T> tfg, Iterable<T> elements){
        MutableValueGraph<T,String> gr = getMutableOf(tfg);
        for(T r: elements){
            gr.removeNode(r);
        }
        return of(gr);
    }


    public Stream<U> nodes(){
        return tfg.nodes().stream();
    }

    /**
     *
     * @return all the nodes of in the TFG
     */
    public Stream<U> nodes_p(){
        return tfg.nodes().parallelStream();
    }

    /**
     *
     * @param g input TFG proto object
     * @return TypeFactGraph of the TFG
     */
    public static TypeFactGraph<Identification> of(TFG g){
        List<Function<MutableValueGraph<Identification,String>, MutableValueGraph<Identification,String>>> addNodesEdges =
                Stream.concat(g.getNodesList().stream().map(TypeFactGraph::addNode)
                , g.getEdgesList().stream().map(e -> u_v(g.getNodes(e.getFst()),
                        g.getNodes(e.getSnd()), e.getEdgeValue()))).collect(toList());
        return TypeFactGraph.<Identification>emptyTFG().mergeMap(addNodesEdges);
    }

    /**
     *
     * @return returns a serializable form of TypeFactGraph using the protocol-buffer
     */
    public TFG asTFG(){
        final List<Identification> nodes = new ArrayList(tfg.nodes());
        List<TFG.Edge> edges = tfg.edges().stream().map(e -> Tuple.of(e, tfg.edgeValue(e.nodeU(), e.nodeV()).get()))
                .map(p -> TFG.Edge.newBuilder().setFst(nodes.indexOf(p._1().nodeU()))
                        .setSnd(nodes.indexOf(p._1().nodeV())).setEdgeValue(p._2())
                        .build())
                .collect(toList());
        return TFG.newBuilder().addAllNodes(nodes).addAllEdges(edges).build();
    }

    /* public TFGRefactorable asTFGRef(){
        final List<Refactorable> nodes = new ArrayList(tfg.nodes());
        List<REdge> edges = tfg.edges().stream().map(e -> P(e, tfg.edgeValue(e.nodeU(), e.nodeV()).get()))
                .map(p -> REdge.newBuilder().setFst(nodes.indexOf(p.fst().nodeU()))
                        .setSnd(nodes.indexOf(p.fst().nodeV())).setEdgeValue(p.snd())
                        .build())
                .collect(toList());
        return TFGRefactorable.newBuilder().addAllNodes(nodes).addAllEdges(edges).build();
    }*/

    public static<U> Optional<U> getSuccessorWithEdge(TypeFactGraph<U> t, U m, String s) {
        return t.get().successors(m).stream().filter(x -> t.get().edgeValue(m, x).get().contains(s)).findFirst();
    }

    public static <U> Set<U> getSuccessorsWithEdge(TypeFactGraph<U> t, U m, String s) {
        return t.get().successors(m).stream().filter(x -> t.get().edgeValue(m, x).get().contains(s)).collect(toSet());
    }

    public static <U> Set<U> getSuccessorsWithEdges(TypeFactGraph<U> t, U m, List<String> s) {
        return t.get().successors(m).stream()
                .filter(succ -> s.stream().anyMatch(ed -> t.get().edgeValue(m,succ).get().contains(ed)))
                .collect(toSet());
    }

    public static <U> Set<U> getSuccessorsOf(TypeFactGraph<U> t, U m){
        return t.get().successors(m);
    }

    public static <U> Set<U> getSuccessorsOfWithBiDirectionalEdge(TypeFactGraph<U> t, U m){
        return t.get().successors(m).stream().filter(s -> t.existsBiDirectionalEdge(m,s)).collect(toSet());
    }


    public static final String NO_EDGE = "NO_EDGE";
    public Tuple2<String,String> getEdgeValuesBetween(U u, U v){
        return Tuple.of(tfg.edgeValueOrDefault(u,v,NO_EDGE),tfg.edgeValueOrDefault(v,u,NO_EDGE));
    }

    public static TypeFactGraph<Identification> induceGraph(TypeFactGraph<Identification> tfg, Set<Identification> ns){
        MutableValueGraph<Identification,String> gr = ValueGraphBuilder.directed().allowsSelfLoops(true).build();
        for(Identification t: ns) {
            for(Identification q : ns){
                tfg.get().edgeValue(t,q).ifPresent(x -> gr.putEdgeValue(t,q,x));
            }
        }
        return of(gr);
    }

    public static <U> U nullHandleReduce(U u1, U u2, BinaryOperator<U> binOp, U empty){

        if((null == u1 && null == u2)){
            return empty;
        }
        else if(u1 == null || u1.equals(empty)){
            return u2;
        }
        else if(u2 == null || u2.equals(empty)){
            return u1;
        }
        else if((u1.equals(empty) || u2.equals(empty))){
            return empty;
        }
        else
            return binOp.apply(u1,u2);
    }

//
//    public static void visualizeGraph(String fileName, List<TFG> tfg) {
//
//        for(int i = 0; i < tfg.size(); i++) {
//            final List<Node> ns = generateEdge(tfg.get(i));
//            Node[] t = ns.toArray(new Node[ns.size()]);
//            Graph g = graph().directed().with(t);
//            try {
//                Graphviz.fromGraph(g).height(200).width(100).render(Format.SVG).toFile(new File("D:\\MyProjects\\" + fileName + i +".svg"));
//            } catch (IOException e) {
//                System.out.println(e.toString());
//                e.printStackTrace();
//            }
//        }
//    }
//
//
//    private static List<Node> generateEdge(TFG tfg){
//        final List<Identification> ns = tfg.getNodesList();
//        final Map<Integer, List<TFG.Edge>> g = tfg.getEdgesList().stream().collect(Collectors.groupingBy(x -> x.getFst()));
//        return g.entrySet().stream()
//                .map(e -> Tuple.of(getNode(ns.get(e.getKey())),
//                        e.getValue().stream().map(x -> Link.to(getNode(ns.get(x.getSnd()))).with(Label.of(x.getEdgeValue()))).collect(toList())))
//                .map(p -> p._1().link(p._2().toArray(new Link[p._2().size()]))).collect(toList());
//    }
//
//    private static Node getNode(Identification id){
//        if(!id.hasOwner())
//            return  node(id.getName() + "\n" + id.getKind());
//        return node(id.getName() + "\n" + id.getKind() + "\n" + "" +  prettyType(id.getType()) +  id.getOwner().hashCode());
//        //return node(id.getName() + "\n" + id.getKind() + "\n" + prettyType(id.getType()) + "\n" + qualifiedName(id)+ "\n"+  id.getOwner().hashCode());
//    }
}
