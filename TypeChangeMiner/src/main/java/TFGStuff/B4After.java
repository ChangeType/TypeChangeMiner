package TFGStuff;

import io.vavr.Tuple2;

import java.util.function.BiFunction;
import java.util.function.Function;

public class B4After<T> {

    private final T b4;
    private final T aftr;

    public B4After(T b4, T aftr){
        this.b4 = b4;
        this.aftr = aftr;
    }

    public B4After(Tuple2<T,T> t){
        this.b4 = t._1();
        this.aftr = t._2();
    }

    public T getAftr() {
        return aftr;
    }

    public T getB4() {
        return b4;
    }

    public T get(boolean b){
        return b? getB4() : getAftr();
    }

    public static String b4AftrToStr(boolean b){
        return b ? "Before" : "After";
    }

    public static <T,U> B4After<U> mapB4After(Function<T,U> fn, B4After<T> val){
        return new B4After<U>(fn.apply(val.getB4()), fn.apply(val.getAftr()));
    }

    public static <T,U> B4After<U> mapB4After(Function<T,U> fn1, Function<T,U> fn2, B4After<T> val){
        return new B4After<U>(fn1.apply(val.getB4()), fn2.apply(val.getAftr()));
    }

    public static <T,U,V> B4After<V> zip(B4After<T> t, B4After<U> u, BiFunction<T,U,V> fn){
        return new B4After<>(fn.apply(t.getB4(),u.getB4()), fn.apply(t.getAftr(), u.getAftr()));
    }

    public static <T,U,V> B4After<V> zip(Tuple2<T,T> t, Tuple2<U, U> u, BiFunction<T,U,V> fn){
        return new B4After<>(fn.apply(t._1(),u._1()), fn.apply(t._2(), u._2()));
    }
}

