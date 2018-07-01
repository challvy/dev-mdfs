package com.challvy.zdfs.zdfsdatanode;

import com.challvy.zdfs.zdfsdatanode.storage.StorageService;

import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ZdfsDatanodeController {

    // 来自spring guides上官方给的读写工具包
    private StorageService storageService;

    // 依赖注入
    @Autowired
    public ZdfsDatanodeController(StorageService storageService) {
        this.storageService = storageService;
    }

    @ResponseBody
    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> get(@PathVariable String filename) {
        // 从配置好的文件夹处读取资源文件
        Resource file = storageService.loadAsResource(filename);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION).body(file);
    }

    @PostMapping("/")
    public String post(@RequestParam("file") MultipartFile file) {
        // 存储文件块至配置好的文件夹位置
        storageService.store(file);
        return "Posted";
    }

    @ResponseBody
    @DeleteMapping("/{filename:.+}")
    public String delete(@PathVariable String filename) {
        // 通过传入块文件名从配置好的文件夹处删除块文件
        storageService.delete(filename);
        return "Deleted";
    }

}
