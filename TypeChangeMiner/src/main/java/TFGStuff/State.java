package TFGStuff;


import com.google.common.collect.Streams;
import com.t2r.common.models.ast.IdentificationOuterClass.Identification;
import io.vavr.Tuple2;
import org.eclipse.jdt.core.dom.ASTNode;

import java.util.List;

/**
 * Created by ameya on 12/17/18.
 */
public class State {
    private final ASTNode root;
    private final List<Tuple2<State,String>> typeDependents;
    private final Identification rootID;


    public State(ASTNode root, Identification rootID, List<Tuple2<State,String>> typeDependents){
        this.root = root;
        this.typeDependents = typeDependents;
        this.rootID = rootID;
    }

    public ASTNode getRoot() {
        return this.root;
    }

    public List<Tuple2<State,String>> getTypeDependents() {
        return this.typeDependents;
    }

    public Identification getRootID() {
        return this.rootID;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof State) {
            State s = (State) obj;
            return s.getRootID().equals(rootID) && ((s.getTypeDependents().isEmpty()
                    && (typeDependents.isEmpty()))
                     || (s.getTypeDependents().size() == typeDependents.size()
                    && Streams.zip(s.getTypeDependents().stream(),typeDependents.stream(), (x, y) -> x._2().equals(y._2()) && x._1().equals(y._1())).allMatch(x-> x)));
        }
        return false;
    }
}

