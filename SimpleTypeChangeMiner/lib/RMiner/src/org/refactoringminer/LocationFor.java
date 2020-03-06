package org.refactoringminer;

import java.security.MessageDigest;

import gr.uom.java.xmi.LocationInfo;
import io.vavr.Tuple2;

public interface LocationFor {


    Tuple2<String, String> getUrlsToElement (Tuple2<String, String> commitUtrl);


    default String generateUrl(LocationInfo locationInfo, String url, String lOrR)  {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(locationInfo.getFilePath().getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            String val = sb.toString();
            return url + val + lOrR + locationInfo.getStartLine();
        }catch (Exception e){
            e.printStackTrace();
        }
        return "";

    }

}
