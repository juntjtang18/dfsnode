package com.infolink.dfs;

import java.io.File;

public class EnvironmentUtils {
    public static boolean isInDocker() {
        return new File("/.dockerenv").exists() || System.getenv("DOCKER_CONTAINER") != null;
    }
}