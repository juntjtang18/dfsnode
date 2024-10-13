package com.fdu.msacs.dfs.bfs;

public interface Encryptor {
    byte[] encrypt(byte[] data);
    byte[] decrypt(byte[] data);
}
