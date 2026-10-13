package com.reviewai.backend.ai;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AiServiceProperties.class)
class AiServiceConfig {

	/**
	 * Cliente HTTP dedicado al ai-service, con timeouts explícitos: sin ellos, un
	 * servicio colgado bloquearía también al backend.
	 */
	@Bean
	RestClient aiServiceRestClient(AiServiceProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
				// Por defecto el cliente de Java intenta subir a HTTP/2 (Upgrade: h2c) incluso
				// sin TLS, y uvicorn solo habla HTTP/1.1: descarta el cuerpo y responde 422.
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(properties.connectTimeout())
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());

		return RestClient.builder()
				.baseUrl(properties.url())
				.requestFactory(requestFactory)
				.build();
	}
}
