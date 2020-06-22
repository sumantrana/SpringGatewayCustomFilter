package com.sumant.springboot.learning.springcloudgateway;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.sumant.springboot.learning.springcloudgateway.EncryptDecryptFilter.Config;
import org.bouncycastle.util.Strings;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.filter.factory.rewrite.MessageBodyDecoder;
import org.springframework.cloud.gateway.filter.factory.rewrite.MessageBodyEncoder;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.server.ServerWebExchange;

import static java.util.function.Function.identity;

@Component
public class EncryptDecryptFilter extends AbstractGatewayFilterFactory<Config> {

	private final Map<String, MessageBodyDecoder> messageBodyDecoders;

	private final Map<String, MessageBodyEncoder> messageBodyEncoders;



	public EncryptDecryptFilter(Set<MessageBodyDecoder> messageBodyDecoders,
			Set<MessageBodyEncoder> messageBodyEncoders){
		super(Config.class);
		this.messageBodyDecoders = messageBodyDecoders.stream()
				.collect(Collectors.toMap(MessageBodyDecoder::encodingType, identity()));
		this.messageBodyEncoders = messageBodyEncoders.stream()
				.collect(Collectors.toMap(MessageBodyEncoder::encodingType, identity()));
	}

	@Override
	public GatewayFilter apply(Config config) {

		return new OrderedGatewayFilter( (exchange, chain) -> {

				System.out.println("Applying encrypt-decrypt filter");

				return DataBufferUtils.join(exchange.getRequest().getBody()).flatMap(dataBuffer -> {

					ServerHttpRequest mutatedHttpRequest = getServerHttpRequest(exchange, dataBuffer);

					ServerHttpResponse mutatedHttpResponse = getServerHttpResponse(exchange);

					return chain.filter(exchange.mutate().request(mutatedHttpRequest).response(mutatedHttpResponse).build());

				});

		}, -2);


	}

	private ServerHttpRequest getServerHttpRequest(ServerWebExchange exchange, DataBuffer dataBuffer) {

		DataBufferUtils.retain(dataBuffer);
		Flux<DataBuffer> cachedFlux = Flux.defer(() -> Flux.just(dataBuffer.slice(0, dataBuffer.readableByteCount())));

		String body = toRaw(cachedFlux);
		String decryptedBody = EncryptDecryptHelper.decrypt(body);
		byte[] decryptedBodyBytes = decryptedBody.getBytes(StandardCharsets.UTF_8);

		return new ServerHttpRequestDecorator(exchange.getRequest()) {

			@Override
			public HttpHeaders getHeaders(){
				HttpHeaders httpHeaders = new HttpHeaders();
				httpHeaders.putAll(exchange.getRequest().getHeaders());
				if (decryptedBodyBytes.length > 0) {
					httpHeaders.setContentLength(decryptedBodyBytes.length);
				}
				httpHeaders.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
				return httpHeaders;
			}


			@Override
			public Flux<DataBuffer> getBody() {

				return Flux.just(body).
						map(s -> {
							return new DefaultDataBufferFactory().wrap(decryptedBodyBytes);
						});

			}

		};


	}

	private ServerHttpResponse getServerHttpResponse(ServerWebExchange exchange) {
		ServerHttpResponse originalResponse = exchange.getResponse();

		return new ServerHttpResponseDecorator(originalResponse) {

			@Override
			public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {

				HttpHeaders httpHeaders = new HttpHeaders();
				httpHeaders.set(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE);
				httpHeaders.set(HttpHeaders.CONTENT_ENCODING, "application/octet-stream");

				ClientResponse clientResponse = prepareClientResponse(body, httpHeaders);

				Mono<String> modifiedBody = extractBody(exchange, clientResponse)
						.flatMap( originalBody -> Mono.just(Objects.requireNonNull(EncryptDecryptHelper.encrypt(originalBody))))
						.switchIfEmpty(Mono.empty());

				BodyInserter<Mono<String>, ReactiveHttpOutputMessage> bodyInserter = BodyInserters.fromPublisher(modifiedBody, String.class);

				CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange,
						exchange.getResponse().getHeaders());

				return bodyInserter.insert(outputMessage, new BodyInserterContext())
						.then(Mono.defer(() -> {
							Mono<DataBuffer> messageBody = updateBody(getDelegate(), outputMessage);
							HttpHeaders headers = getDelegate().getHeaders();
							headers.setContentType(MediaType.TEXT_PLAIN);
							if (headers.containsKey(HttpHeaders.CONTENT_LENGTH)) {
								messageBody = messageBody.doOnNext(data -> {
									headers.setContentLength(data.readableByteCount());
								});
							}

							return getDelegate().writeWith(messageBody);
						}));

			}

			private Mono<String> extractBody(ServerWebExchange exchange1, ClientResponse clientResponse) {

				List<String> encodingHeaders = exchange.getResponse().getHeaders()
						.getOrEmpty(HttpHeaders.CONTENT_ENCODING);
				for (String encoding : encodingHeaders) {
					MessageBodyDecoder decoder = messageBodyDecoders.get(encoding);
					if (decoder != null) {
						return clientResponse.bodyToMono(byte[].class)
								.publishOn(Schedulers.parallel()).map(decoder::decode)
								.map(bytes -> exchange.getResponse().bufferFactory()
										.wrap(bytes))
								.map(buffer -> prepareClientResponse(Mono.just(buffer),
										exchange.getResponse().getHeaders()))
								.flatMap(response -> response.bodyToMono(String.class));
					}
				}


				return clientResponse.bodyToMono(String.class);

			}

			private Mono<DataBuffer> updateBody(ServerHttpResponse httpResponse,
					CachedBodyOutputMessage message) {

				Mono<DataBuffer> response = DataBufferUtils.join(message.getBody());

				List<String> encodingHeaders = httpResponse.getHeaders()
						.getOrEmpty(HttpHeaders.CONTENT_ENCODING);
				for (String encoding : encodingHeaders) {
					MessageBodyEncoder encoder = messageBodyEncoders.get(encoding);
					if (encoder != null) {
						DataBufferFactory dataBufferFactory = httpResponse.bufferFactory();
						response = response.publishOn(Schedulers.parallel())
								.map(encoder::encode).map(dataBufferFactory::wrap);
						break;
					}
				}

				return response;

			}



			private ClientResponse prepareClientResponse(Publisher<? extends DataBuffer> body, HttpHeaders httpHeaders) {
				ClientResponse.Builder builder = ClientResponse.create(exchange.getResponse().getStatusCode(), HandlerStrategies.withDefaults().messageReaders());
				return builder.headers(headers -> headers.putAll(httpHeaders)).body(Flux.from(body)).build();
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

		public Config() {
		}

	}

}
