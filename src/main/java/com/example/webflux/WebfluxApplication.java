package com.example.webflux;

import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@EnableAsync
@RestController
@SpringBootApplication
public class WebfluxApplication {

    static final String URL1 = "http://localhost:8081/service?req={req}";
    static final String URL2 = "http://localhost:8081/service2?req={req}";
    WebClient client = WebClient.create();
    @Autowired
    MyService myService;

    public static void main(String[] args) {
        System.setProperty("reactor.ipc.netty.workerCount", "1");
        System.setProperty("reactor.ipc.netty.pool.maxConnections", "2000");
        SpringApplication.run(WebfluxApplication.class, args);
    }

    @GetMapping("/")
    Mono<String> hello() throws InterruptedException {
        log.info("pos1");
        Mono<String> m = Mono.fromSupplier(this::generateHello).doOnNext(log::info).log();
        log.info("pos2");
        return m; // publisher -> publisher -> publisher -> subscriber
    }

    private String generateHello() {
        log.info("method generateHello()");
        return "Hello";
    }

    @GetMapping("/rest")
    public Mono<String> rest(int idx) {
        Mono<String> body =
            client.get().uri(URL1, idx).exchangeToMono(clientResponse -> clientResponse.bodyToMono(String.class))
                .doOnNext(s -> {
                    log.info("test {}", s);
                })
                .flatMap(s -> client.get().uri(URL2, s).exchangeToMono(clientResponse -> clientResponse.bodyToMono(String.class)))
                .doOnNext(s -> {
                    log.info("test2 {}", s);
                })
                .flatMap(s -> Mono.fromCompletionStage(myService.work(s)));
        /*Mono<ClientResponse> responseMono = client.get().uri(URL1, idx).exchange();
        Mono<String> body = responseMono.flatMap(clientResponse -> clientResponse.bodyToMono(String.class));*/

        return body;
    }

    @Service
    public static class MyService {

        @Async
        public CompletableFuture<String> work(String req) {
            return CompletableFuture.completedFuture(req + "/asyncwork");
        }
    }
}
