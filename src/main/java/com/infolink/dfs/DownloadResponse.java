package com.infolink.dfs;

import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public class DownloadResponse {
    private StreamingResponseBody body;
    private String filename;

    public DownloadResponse(StreamingResponseBody body, String filename) {
        this.body = body;
        this.filename = filename;
    }

    public StreamingResponseBody getBody() {
        return body;
    }

    public String getFilename() {
        return filename;
    }
}
