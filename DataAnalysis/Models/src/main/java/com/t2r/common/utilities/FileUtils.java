package com.t2r.common.utilities;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.vavr.CheckedConsumer;
import io.vavr.control.Try;

public class FileUtils {


    public static Path createIfAbsent(Path p){
        return Try.of(() -> p.toFile().exists() ? p : Files.createFile(p))
                .getOrElseThrow(() -> new RuntimeException("Could Not create file " + p.toString()));
    }

    public static String readFile(Path p){
        return Try.of(() -> new String(Files.readAllBytes(p))).getOrElse("");
    }

    public static Path createFolderIfAbsent(Path p){
        if(!p.toFile().exists())
            Try.of(() -> Files.createDirectories(p)).onFailure(e -> e.printStackTrace());
        return p;
    }

    public static Path writeToFile(Path p, String content){
        return Try.of(() -> Files.write(p.toAbsolutePath(), content.getBytes(), StandardOpenOption.CREATE))
                .onFailure(e -> e.printStackTrace())
                .getOrElse(p);
    }

    public static Path materializeFile(Path p, String content){
        createFolderIfAbsent(p.getParent().toAbsolutePath());
        return Try.of(() -> Files.write(p.toAbsolutePath(), content.getBytes(), StandardOpenOption.CREATE))
                .onFailure(e -> e.printStackTrace())
                .getOrElse(p);
    }

    public static void appendToFile(Path p, CheckedConsumer<FileOutputStream> content){
        Try.of(() -> {
            FileOutputStream output = new FileOutputStream(p.toString(), true);
            content.accept(output);
            output.close();
            return true;
        }).getOrElseThrow(() -> new RuntimeException("Could not append to file"));

    }



    public static void writeToFile(Path p, CheckedConsumer<FileOutputStream> content){
        Try.of(() -> {
            FileOutputStream output = new FileOutputStream(p.toString(), false);
            content.accept(output);
            output.close();
            return true;
        }).getOrElseThrow(() -> new RuntimeException("Could not append to file"));

    }


    public static <T> List<T> parseCsv(Path path, Function<String[], T> parser){
        try {
            return Files.readAllLines(path).stream()
                    .map(e -> e.split(","))
                    .map(parser)
                    .collect(toList());
        }catch (Exception e){
            System.out.println("Could not the csv file " + path );
            throw new RuntimeException("Could not read projects");
        }
    }

    public static void materializeAtBase(Path basePath, Map<Path,String> fileContent) {
        createFolderIfAbsent(basePath);
        fileContent.forEach((k,v) -> materializeFile(basePath.resolve(k), v));
    }

    public static void deleteDirectory(Path p){
        try{
            Files.walk(p)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }catch (Exception e){
            e.printStackTrace();
        }

    }



}
