package gr.uom.java.xmi.TypeFactMiner;

import com.t2r.common.models.ast.TypeGraphOuterClass.TypeGraph;
import com.t2r.common.utilities.PrettyPrinter;
import org.eclipse.jdt.core.dom.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class Visitors {

    public static class EnumVisitor extends ASTVisitor {

        private Set<String> enums = new HashSet<>();
        private final String parentName;
        Map<String, Set<String>> usedTypes = new HashMap<>();

        public EnumVisitor(String parentName) {
            this.parentName = parentName;
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            String name = parentName + "." + node.getName().toString();
            enums.add(name);
            UsedTypes ut = new UsedTypes();
            node.accept(ut);
            usedTypes.put(name,ut.typesUsed);




            return false;
        }

        public Set<String> getEnums() {
            return enums;
        }
    }

    public static class UsedTypes extends ASTVisitor {

        public Set<String> typesUsed = new HashSet<>();

        @Override
        public boolean visit(SimpleType st) {
            typesUsed.add(st.getName().getFullyQualifiedName());
            return true;
        }

        @Override
        public boolean visit(QualifiedType qt) {
            typesUsed.add(qt.getName().getFullyQualifiedName());
            return true;
        }

        @Override
        public boolean visit(NameQualifiedType nqt) {
            typesUsed.add(nqt.getName().getFullyQualifiedName());
            return true;
        }
    }



    public static class UsedTypesCounter extends ASTVisitor {

        private final Predicate<TypeGraph> pred;
        public int counter = 0;

        public UsedTypesCounter(TypeGraph tg){
            this.pred = t -> PrettyPrinter.looselyEqual(tg, t);
        }


        @Override
        public boolean visit(PrimitiveType st) {
            if(pred.test(TypeGraphUtil.getTypeGraph(st)))
                counter += 1;
            return true;
        }

        @Override
        public boolean visit(SimpleType st) {
            if(pred.test(TypeGraphUtil.getTypeGraph(st)))
                counter += 1;
            return true;
        }

        @Override
        public boolean visit(ArrayType st) {
            if(pred.test(TypeGraphUtil.getTypeGraph(st)))
                counter += 1;
            return true;
        }

        @Override
        public boolean visit(ParameterizedType st) {
            if(pred.test(TypeGraphUtil.getTypeGraph(st)))
                counter += 1;
            return true;
        }

        @Override
        public boolean visit(QualifiedType qt) {
            if(pred.test(TypeGraphUtil.getTypeGraph(qt)))
                counter+= 1;
            return true;
        }

        @Override
        public boolean visit(NameQualifiedType nqt) {
            if(pred.test(TypeGraphUtil.getTypeGraph(nqt)))
                counter += 1;
            return true;
        }

    }


}
