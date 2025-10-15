// OllamaRequest.java
package com.vg.restrictacesscontrol.net;

import java.util.Arrays;
import java.util.List;

public class OllamaRequest {
    public String model = "qwen2.5vl:latest";
    public String prompt;
    public String format = "json";
    public Options options = new Options();
    public List<String> images;
    public boolean stream = false;

    public static class Options { public int temperature = 0; }

    public static OllamaRequest build(String base64, String prompt) {
        OllamaRequest r = new OllamaRequest();
        r.prompt = prompt;
        r.images = Arrays.asList(base64);
        return r;
    }
}
