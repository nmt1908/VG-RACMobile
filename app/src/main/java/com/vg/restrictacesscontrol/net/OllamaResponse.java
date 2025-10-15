// OllamaResponse.java
package com.vg.restrictacesscontrol.net;

public class OllamaResponse {
    // Ollama trả về field "response" là JSON (string) kiểu {"plate_number": "...", "confidence": ...}
    public String model;
    public String response;  // sẽ parse tiếp ở Activity
    public boolean done;
}
