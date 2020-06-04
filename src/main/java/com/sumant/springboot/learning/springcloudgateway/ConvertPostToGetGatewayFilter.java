package com.sumant.springboot.learning.springcloudgateway;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import com.sumant.springboot.learning.springcloudgateway.ConvertPostToGetGatewayFilter.Config;
import org.bouncycastle.util.Strings;
import reactor.core.publisher.Flux;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;


@Component
public class ConvertPostToGetGatewayFilter extends AbstractGatewayFilterFactory<Config> {

	private static final String REQUEST_BODY_OBJECT = "requestBodyObject";

	public ConvertPostToGetGatewayFilter(){
		super(Config.class);
	}

	@Override
	public GatewayFilter apply(Config config) {

		return (exchange, chain) -> {

			if (exchange.getRequest().getHeaders().getContentType() == null) {
				return chain.filter(exchange);
			} else {
				return DataBufferUtils.join(exchange.getRequest().getBody())
						.flatMap(dataBuffer -> {

							DataBufferUtils.retain(dataBuffer);
							Flux<DataBuffer> cachedFlux = Flux.defer(() -> Flux.just(dataBuffer.slice(0, dataBuffer.readableByteCount())));

							ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {


								@Override
								public String getMethodValue(){
									return HttpMethod.GET.name();
								}


								@Override
								public URI getURI(){
									return UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
											.queryParams(RequestBodyHelper.convertJsonToQueryParamMap(toRaw(cachedFlux))).build().toUri();
								}


								@Override
								public Flux<DataBuffer> getBody() {
									return Flux.empty();
								}

							};

							return chain.filter(exchange.mutate().request(mutatedRequest).build());
						});
			}

		};
	}


	private static String toRaw(Flux<DataBuffer> body) {
		AtomicReference<String> rawRef = new AtomicReference<>();
		body.subscribe(buffer -> {
			byte[] bytes = new byte[buffer.readableByteCount()];
			buffer.read(bytes);
			DataBufferUtils.release(buffer);
			rawRef.set(Strings.fromUTF8ByteArray(bytes));
		});
		return rawRef.get();
	}

	public static class Config {

	}
}
