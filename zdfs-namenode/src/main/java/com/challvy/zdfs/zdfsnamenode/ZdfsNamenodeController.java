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

    private final int BLOCK_SIZE = 32768;
    private final String blockDir = "zdfs/temp/block/";
    private static final String downloadDir = "zdfs/temp/download/";

    // 所有可用的DataNode组的url
    private ArrayList<String> urlList = new ArrayList<>();

    // 存储文件名以及对应的分块数量
    private Map<String, Integer> mapFilenameBlocknum = new HashMap<>();

    // 存储各个文件分块数量、位置等信息
    private Map<String, List<String>> fileBlock_dataNodeURL = new HashMap<>();

    public void addFileBlock(String dataNodeURL, String fileName, long blockIndex) {
        String fileBlock = fileName + "#" + blockIndex;
        List<String> URLS = fileBlock_dataNodeURL.get(fileBlock);
        if (URLS == null) {
            URLS = new LinkedList<>();
            URLS.add(dataNodeURL);
            fileBlock_dataNodeURL.put(fileBlock, URLS);
        } else {
            URLS.add(dataNodeURL);
        }
    }

    public List<String> getDataNodeURL(String fileName, long blockIndex) {
        return fileBlock_dataNodeURL.get(fileName + "#" + blockIndex);
    }

    public void removeFileBlock(String fileName, long blockIndex) {
        String fileBlock = fileName + "#" + blockIndex;
        fileBlock_dataNodeURL.remove(fileBlock);
    }

    ZdfsNamenodeController(){
        File block = new File(blockDir);
        File download = new File(downloadDir);
        block.mkdirs();
        download.mkdirs();
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
        urlList.add(datanode);
        System.err.println("Registered:\t" + datanode);
    }

    @EventListener
    public void listen(EurekaInstanceRenewedEvent event) {
        System.err.println("Renewed:\t" + event.getServerId() + "\t" + event.getAppName());
    }

    @EventListener
    public void listen(EurekaInstanceCanceledEvent event) {
        String datanode = "http://" + event.getServerId() + "/";
        // 直接从可用节点组中删去，没有考虑节点失效问题
        urlList.remove(datanode);
        System.err.println("Canceled:\t" + event.getServerId() + "\t" + event.getAppName());
    }

    @GetMapping("/all")
    public Map<String, Integer> get() {
        // TODO: 增加接口，增加文件信息详情
        return mapFilenameBlocknum;
    }

    // 以下三个函数分别对应功能GET, POST, DELETE
    @ResponseBody
    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> get(@PathVariable String filename) throws IOException {
        // TODO: 这里只实现对文件的处理，需对文件夹进行处理
        File tempFile = new File(downloadDir + filename);
        tempFile.createNewFile();

        // 由于原文件被分块，可以借助FileChannel机制进行复原
        FileChannel downloadFileChannel = new FileOutputStream(tempFile).getChannel();
        for (int idx=0; idx<mapFilenameBlocknum.get(filename); idx++) {
            List<String> dataNodeURLS = getDataNodeURL(filename, idx);
            String blockedFileName = filename + "-" + idx;

            // 任取一个可用节点获取文件即可
            String dataNodeURL = dataNodeURLS.get(0);
            if(dataNodeURL!=null) {
                String resourceURL = dataNodeURL + blockedFileName;
                UrlResource urlResource = new UrlResource(new URL(resourceURL));
                InputStream inputStream = urlResource.getInputStream();

                byte[] bytes = new byte[BLOCK_SIZE];
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int n, blockSize = 0;
                while ( (n=inputStream.read(bytes)) != -1) {
                    out.write(bytes,0,n);
                    blockSize += n;
                }
                bytes = out.toByteArray();

                File slicedFile = new File(blockDir + blockedFileName);
                FileOutputStream fos = new FileOutputStream(slicedFile);
                fos.write(bytes, 0, blockSize);
                fos.close();

                FileChannel inputChannel = new FileInputStream(slicedFile).getChannel();
                inputChannel.transferTo(0, slicedFile.length(), downloadFileChannel);
                inputChannel.close();
            }
        }
        downloadFileChannel.close();
        System.err.println("Got:\t" + filename);
        Resource urlRes = new UrlResource(tempFile.toURI());
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,urlRes.getFilename()).body(urlRes);
    }

    @PostMapping("/")
    public String post(@RequestParam("file") MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();

        // 计算原上传文件所需的分块数，向上取整
        int numBlock = (int)(Math.ceil(file.getSize()/BLOCK_SIZE));

        BufferedInputStream bufferedInputStream = new BufferedInputStream(file.getInputStream());
        // 循环处理每一个分块
        for(int idx=0; idx<numBlock; idx++) {
            byte byteBlock[] = new byte[BLOCK_SIZE];
            int blockSize = bufferedInputStream.read(byteBlock);
            String blockName = blockDir + fileName + "-" + idx;
            File itemFile = new File(blockName);
            FileOutputStream fileOutputStream = new FileOutputStream(itemFile);
            fileOutputStream.write(byteBlock, 0, blockSize);
            fileOutputStream.close();
            // TODO: NameNode没有暂时实现负载均衡，也没有设置数据备份数量参数，这里直接将上传的数据存入所有可用DataNode
            for (String URL : urlList) {
                FileSystemResource resource = new FileSystemResource(itemFile);
                MultiValueMap<String, Object> parameters = new LinkedMultiValueMap<>();
                parameters.add("file", resource);
                RestTemplate rest = new RestTemplate();
                String response = rest.postForObject(URL, parameters, String.class);
                addFileBlock(URL, fileName, idx);
                System.err.println(response + ":\t" + fileName + "\tBlock" + idx + "\t" + URL);
            }
            itemFile.delete();
        }
        mapFilenameBlocknum.put(fileName, numBlock);
        return "Posted.";
    }

    @ResponseBody
    @DeleteMapping("/{filename:.+}")
    public String delete(@PathVariable String filename) {
        int numBlocks = mapFilenameBlocknum.get(filename);
        for (int blockIndex = 0; blockIndex < numBlocks; blockIndex ++) {
            List<String> dataNodeURLS = getDataNodeURL(filename, blockIndex);
            for (String dataNodeURL : dataNodeURLS) {
                String URL = dataNodeURL + filename + "-" + blockIndex;
                RestTemplate rest = new RestTemplate();
                rest.delete(URL);
                System.err.println("Deleted:\t" + URL);
            }
            removeFileBlock(filename, blockIndex);
        }
        mapFilenameBlocknum.remove(filename);
        return "Deleted.";
    }

}
