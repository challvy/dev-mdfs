package com.challvy.zdfs.zdfsdatanode;

import com.challvy.zdfs.zdfsdatanode.storage.StorageProperties;
import com.challvy.zdfs.zdfsdatanode.storage.StorageService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;

@EnableConfigurationProperties(StorageProperties.class)
@EnableEurekaClient
@SpringBootApplication
public class ZdfsDatanodeApplication {

	public static void main(String[] args) {
		SpringApplication.run(ZdfsDatanodeApplication.class, args);
	}

    @Bean
    CommandLineRunner init(StorageService storageService) {
        return (args) -> {
            storageService.deleteAll();
            storageService.init();
        };
    }

}
