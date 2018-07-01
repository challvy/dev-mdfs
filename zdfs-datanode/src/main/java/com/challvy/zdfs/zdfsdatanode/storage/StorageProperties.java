package com.challvy.zdfs.zdfsdatanode.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("storage")
public class StorageProperties {

    // 配置文件存储目录
    private String folderLocation = "zdfs/database/";

    public String getLocation() {
        return folderLocation;
    }

    public void setLocation(String location) {
        this.folderLocation = location;
    }

}
