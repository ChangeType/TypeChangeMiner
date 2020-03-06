package com.t2r.common.utilities;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Counter<T> {
    public Map<T, Integer> getCounts() {
        return counts;
    }

    public boolean contains(T t){
        return counts.containsKey(t);
    }

    final Map<T, Integer> counts = new HashMap<>();

    public void add(T t) {
        counts.merge(t, 1, Integer::sum);
    }

    public void add(T t, int count) {
        counts.merge(t, count, Integer::sum);
    }

    public void addAll(List<T> ts) {
        for (var t : ts)
            counts.merge(t, 1, Integer::sum);
    }

    public void merge(Counter<T> ts) {
        for (var t : ts.items())
            counts.merge(t.getKey(), t.getValue(), Integer::sum);
    }

    public void unMerge(Counter<T> ts) {
        for (var t : items())
            if(ts.contains(t.getKey()))
                counts.put(t.getKey(), t.getValue()-ts.count(t.getKey()));
    }

    public int count(T t) {
        return counts.getOrDefault(t, 0);
    }

    public static <T,R> Counter<R> updateKey(Function<T,R> fn, Counter<T> counter){
        Counter<R> newCounter = new Counter<>();
        for (var t : counter.items()){
            newCounter.add(fn.apply(t.getKey()), t.getValue());
        }
        return newCounter;
    }

    public Set<Map.Entry<T,Integer>> items(){
        return counts.entrySet();
    }



    @Override
    public String toString(){
        return items().stream().map(t -> t.getKey().toString() + "     "  + t.getValue())
                .collect(Collectors.joining(","));
    }

}
