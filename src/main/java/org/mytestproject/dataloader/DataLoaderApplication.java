package org.mytestproject.dataloader;

import org.mytestproject.dataloader.services.DataLoaderService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DataLoaderApplication implements CommandLineRunner {

    private final DataLoaderService service;

    public DataLoaderApplication(DataLoaderService service){
        this.service = service;
    }

    public static void main(String[] args) {
        SpringApplication.run(DataLoaderApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {

        System.out.println(service.loadLocalDataFile());

    }

}
