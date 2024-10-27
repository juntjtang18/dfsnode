package com.infolink.dfs;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    @GetMapping("/pingsvr")
    public String pingServer() {
        return "Server is running!";
    }
}
