package com.sumant.springboot.learning.springcloudgateway;


import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith({SpringExtension.class})
@SpringBootTest( webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GatewayEncryptDecryptTests {

	@Autowired
	private TestRestTemplate testRestTemplate;

	@LocalServerPort
	private int port;

	private WireMockServer wireMockServer;


	@BeforeAll
	public void init(){
		wireMockServer = new WireMockServer(options().port(8080));
		wireMockServer.start();

	}

	@AfterAll
	public void destroy(){
		wireMockServer.stop();
	}


	@Test
	public void encryptDecryptFilter_decryptsDefaultBook_and_EncryptsDefaultResponse(){

		//language=JSON
		String data = "{\n"
				+ "  \"title\": \"TestTitle1\",\n"
				+ "  \"author\": \"TestAuthor1\"\n"
				+ "}";

		MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
		headers.add(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE);

		wireMockServer.stubFor( post("/api/book").withRequestBody(equalToJson(data))
				.willReturn( status(201).withBody("Created")));


		String gatewayUrl = "http://localhost:" + port + "/createBook";

		String encryptedRequest = EncryptDecryptHelper.encrypt(data);
		String expectedDecryptedResponse = "Created";


		ResponseEntity<String> outputEntity = testRestTemplate.exchange(gatewayUrl, HttpMethod.POST,new HttpEntity<String>(encryptedRequest, headers),String.class);


		assertThat( outputEntity.getStatusCode() ).isEqualTo( HttpStatus.CREATED );
		assertThat( EncryptDecryptHelper.decrypt( outputEntity.getBody() ) ).isEqualTo(expectedDecryptedResponse);

	}



}
