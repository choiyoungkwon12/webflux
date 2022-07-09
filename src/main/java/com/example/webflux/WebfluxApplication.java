package com.example.webflux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
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

    @GetMapping("/event/{id}")
    Mono<Event> event(@PathVariable long id) {
        return Mono.just(new Event(id, "event " + id));
    }

    @GetMapping("/event2/{id}")
    Mono<List<Event>> event2(@PathVariable long id) {
        return Mono.just(List.of(new Event(1L, "event1"), new Event(2L, "event2")));
    }

    // produces는 1차적으로는 client가 요청을 보내준거에서 accept 헤더를 보고 mapping을 해주기 위한 것
    // 2차적으로는 해당 컨트롤러가 어떤 미디어 타입으로 리턴해주는지도 사용할 수 있음.
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<Event> events() throws ExecutionException, InterruptedException {
        // 초기 상태와 그것을 이용해서 데이터를 계속 만들어내는 Flux
        Flux<String> es = Flux
            .generate(sink -> sink.next("value"));

        // 일정 간격으로 숫자를 만들어 내는 Flux
        Flux<Long> interval = Flux.interval(Duration.ofSeconds(1));

        return Flux.zip(es, interval).map(tu ->
            new Event(tu.getT2(), tu.getT1() + tu.getT2())
        ).take(10);
    }

    @Data
    @AllArgsConstructor
    public static class Event {

        long id;
        String value;
    }

    @Service
    public static class MyService {

        @Async
        public CompletableFuture<String> work(String req) {
            return CompletableFuture.completedFuture(req + "/asyncwork");
        }
    }
}
