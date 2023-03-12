package com.batrakov;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {
    private final int requestLimit;
    private final String TOKEN = "${TOKEN}";
    private final TimeUnit timeUnit;
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private long lastRequestTime;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        lastRequestTime = System.currentTimeMillis();
    }

    public synchronized void createDocument(Object document, String signature) throws InterruptedException {
        checkMaxRequests();
        makeApiRequest(document, signature);
        requestCount.incrementAndGet();
    }

    private void makeApiRequest(Object document, String signature) {
        HttpClient httpClient = HttpClient.newHttpClient();

        ObjectMapper objectMapper = new ObjectMapper();
        String documentJson;
        String signatureJson;
        String formatJson;
        String typeJson;
        try {
            documentJson = objectMapper.writeValueAsString(document);
            signatureJson = objectMapper.writeValueAsString(signature);
            formatJson = objectMapper.writeValueAsString("MANUAL");
            typeJson = objectMapper.writeValueAsString("LP_INTRODUCE_GOODS");
        } catch (JsonProcessingException e) {
            // Обработка ошибок сериализации (как временное решение указал печать в консоль)
            System.out.println(e.getMessage());
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru//api/v3/lk/documents/create"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer" + TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString(String.format(
                        "{\"document_format\":%s, \"product_document\":%s, \"signature\":%s, \"type\":%s}",
                        formatJson, documentJson, signatureJson, typeJson)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                // Обработка успешного ответа от API (как временное решение указал печать в консоль)
                System.out.println("Document sent");
            } else {
                throw new CrptApiException("Wrong request");
            }
        } catch (IOException | InterruptedException e) {
            throw new CrptApiException(e.getMessage());
        }
    }

    private void checkMaxRequests() throws InterruptedException {
        long now = System.currentTimeMillis();
        long timeElapsed = now - lastRequestTime;
        if (timeElapsed >= timeUnit.toMillis(1)) {
            requestCount.set(0);
            lastRequestTime = now;
        } else if (requestCount.get() >= requestLimit) {
            long waitTime = timeUnit.toMillis(1) - timeElapsed;
            wait(waitTime);
        }
    }

    public static class CrptApiException extends RuntimeException {
        public CrptApiException(String message) {
            super(message);
        }
    }
}
