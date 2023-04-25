package com.scapeak.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author sooyaaa
 * @createTime 2023/4/25
 */
public class MyTest {
    public static void main(String[] args) throws Exception {
        List<String> result = new ArrayList<>();
        result.add("out_freq");
        result.add("motor_speed");
        DataClient dataClient = new DataClient("opc.tcp://192.168.3.118:4840", "Project.141A016M01.Data", result);
        dataClient.readValue().forEach((k, v) -> System.out.println(k + " : " + v));
    }
}
