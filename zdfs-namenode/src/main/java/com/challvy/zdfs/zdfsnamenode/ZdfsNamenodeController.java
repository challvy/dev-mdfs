package com.challvy.zdfs.zdfsnamenode;

import com.netflix.appinfo.InstanceInfo;
import org.springframework.cloud.netflix.eureka.server.event.*;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.*;

@RestController
public class ZdfsNamenodeController {

    // 分块大小
    private final int SIZE_BLOCK = 32768;
    private final String blockDir = "zdfs/temp/block/";
    private static final String downloadDir = "zdfs/temp/download/";

    // NameNode文件管理模型，<文件名:对应的分块数量>
    private Map<String, Integer> namenodeFileSystem;

    // DataNode文件管理模型，<文件分块名称:对应的DataNode URL集>
    private Map<String, ArrayList<String>> datanodeFileSystem;

    // 所有可用DataNode集[URL]
    private ArrayList<String> datanodeUrlList;

    ZdfsNamenodeController(){
        // 初始化目录
        File block = new File(blockDir);
        File download = new File(downloadDir);
        block.mkdirs();
        download.mkdirs();

        // 初始化数据
        namenodeFileSystem = new HashMap<>();
        datanodeUrlList = new ArrayList<>();
        datanodeFileSystem = new HashMap<>();
    }

    // 以下四个函数是事件监听
    @EventListener
    public void listen(EurekaServerStartedEvent event) {
        System.err.println("Started:\tEureka Server");
    }

    @EventListener
    public void listen(EurekaInstanceRegisteredEvent event) {
        InstanceInfo instanceInfo = event.getInstanceInfo();
        String datanode = instanceInfo.getHomePageUrl();
        datanodeUrlList.add(datanode);
        System.err.println("Registered:\t" + datanode);
    }

    @EventListener
    public void listen(EurekaInstanceRenewedEvent event) {
        System.err.println("Renewed:\t" + event.getServerId() + "\t" + event.getAppName());
    }

    @EventListener
    public void listen(EurekaInstanceCanceledEvent event) {
        String datanode = "http://" + event.getServerId() + "/";
        // TODO: 数据迁移。此处直接从可用节点组中删去，没有考虑节点失效问题
        datanodeUrlList.remove(datanode);
        System.err.println("Canceled:\t" + event.getServerId());
    }

    @GetMapping(value = "/all")
    public Map<String, Integer> get() {
        return namenodeFileSystem;
    }

    // 以下三个函数分别对应功能GET,POST,DELETE
    @ResponseBody
    @GetMapping(value = "/{fileName:.+}")
    public ResponseEntity<Resource> get(@PathVariable String fileName) throws IOException {
        // TODO: 这里只实现对文件的处理，没有文件夹处理
        File tempFile = new File(downloadDir + fileName);
        tempFile.createNewFile();

        // 由于原文件被分块，可以借助FileChannel机制进行组装复原
        FileChannel fileChan = new FileOutputStream(tempFile).getChannel();
        for (int idx=0; idx< namenodeFileSystem.get(fileName); idx++) {
            String blockName = fileName + "-" + idx;
            ArrayList<String> datanodeUrl = datanodeFileSystem.get(blockName);

            // 任取一个可用节点位置来获取文件分块位置即可，这里直接默认取第一个，显然可以优化
            String datanodeUrlItem = datanodeUrl.get(0);
            if(datanodeUrlItem!=null) {
                String resUrl = datanodeUrlItem + blockName;
                UrlResource urlRes = new UrlResource(new URL(resUrl));
                InputStream inputStrm = urlRes.getInputStream();
                byte[] byteBlock = new byte[SIZE_BLOCK];
                ByteArrayOutputStream byteArrayOutputStrm = new ByteArrayOutputStream();
                int tmpSize;
                int size=0;
                while (-1 != (tmpSize=inputStrm.read(byteBlock))) {
                    byteArrayOutputStrm.write(byteBlock,0, tmpSize);
                    size += tmpSize;
                }
                byteBlock = byteArrayOutputStrm.toByteArray();
                File blockFile = new File(blockDir + blockName);
                FileOutputStream fileOutputStream = new FileOutputStream(blockFile);
                fileOutputStream.write(byteBlock, 0, size);
                fileOutputStream.close();
                FileChannel tmpChan = new FileInputStream(blockFile).getChannel();
                tmpChan.transferTo(0, blockFile.length(), fileChan);
                tmpChan.close();
            }
        }
        fileChan.close();
        System.err.println("Got:\t" + fileName);
        Resource urlRes = new UrlResource(tempFile.toURI());
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,urlRes.getFilename()).body(urlRes);
    }

    @PostMapping("/")
    public String post(@RequestParam("zdfs") MultipartFile file) throws IOException {
        // 先对文件进行分块，然后将分块文件交给DataNode存储，同时datanodeFileSystem要记录分块文件的分块文件名以及存储位置信息

        // 计算原上传文件所需的分块数，向上取整
        int blockNum = (int)(Math.ceil(file.getSize()/ SIZE_BLOCK));
        String fileName = file.getOriginalFilename();
        BufferedInputStream bufferedInputStream = new BufferedInputStream(file.getInputStream());

        // 循环处理每一个分块文件
        for(int idx=0; idx<blockNum; idx++) {
            byte byteBlock[] = new byte[SIZE_BLOCK];
            int blockSize = bufferedInputStream.read(byteBlock);
            String blockName = fileName + "-" + idx;
            File blockFile = new File(blockDir + blockName);
            FileOutputStream fileOutputStream = new FileOutputStream(blockFile);
            fileOutputStream.write(byteBlock, 0, blockSize);
            fileOutputStream.close();

            // TODO: NameNode没有暂时实现负载均衡，也没有设置数据备份数量参数，这里直接将上传的数据存入所有可用DataNode
            for(String datanodeUrlItem : datanodeUrlList) {
                // 设置与DataNode交互的参数，让DataNode存储分块文件
                FileSystemResource res = new FileSystemResource(blockFile);
                MultiValueMap<String, Object> parameters = new LinkedMultiValueMap<>();
                parameters.add("zdfs", res);
                RestTemplate rest = new RestTemplate();
                String response = rest.postForObject(datanodeUrlItem, parameters, String.class);

                // 记录分块数据位置信息
                ArrayList<String> datanodeUrl = datanodeFileSystem.get(blockName);
                if(null!=datanodeUrl) {
                    datanodeUrl.add(datanodeUrlItem);
                } else {
                    datanodeUrl = new ArrayList<>();
                    datanodeUrl.add(datanodeUrlItem);
                    datanodeFileSystem.put(blockName, datanodeUrl);
                }
                System.err.println(response+":\t"+ fileName+"-"+idx +"\t"+datanodeUrlItem);
            }
            // 清空，循环利用
            blockFile.delete();
        }
        namenodeFileSystem.put(fileName, blockNum);
        return "Posted.";
    }

    @ResponseBody
    @DeleteMapping(value = "/{fileName:.+}")
    public String delete(@PathVariable String fileName) {
        // 删除文件，即删除文件对应的所有分块
        for (int idx = 0; idx<namenodeFileSystem.get(fileName); idx++) {
            String blockName = fileName + "-" + idx;
            ArrayList<String> datanodeUrl = datanodeFileSystem.get(blockName);
            // 删除文件，包括所有备份的文件（分块）
            for(String datanodeUrlItem:datanodeUrl) {
                String blockUrl = datanodeUrlItem + blockName;
                RestTemplate rest = new RestTemplate();
                rest.delete(blockUrl);
                System.err.println("Deleted:\t" + blockUrl);
            }
            // 从DataNode文件管理模型中删除本文件分块项
            datanodeFileSystem.remove(blockName);
        }
        // 从NameNode文件管理模型中删除本文件项
        namenodeFileSystem.remove(fileName);
        return "Deleted.";
    }

}
