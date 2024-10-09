package com.fdu.msacs.dfs.server;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    @GetMapping("/pingsvr")
    public String pingServer() {
        return "Server is running!";
    }
}
