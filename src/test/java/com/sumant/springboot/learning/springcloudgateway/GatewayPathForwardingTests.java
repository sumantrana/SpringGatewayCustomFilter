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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith({SpringExtension.class})
@SpringBootTest( webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GatewayPathForwardingTests {

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
	public void pathForwardToDefaultBook_ReturnsDefaultBook(){

		String returnValue = "{\n"
				+ "  "
				+ "\"id\": 0,\n"
				+ "  \"name\":\"DefaultBook\",\n"
				+ "  \"value\":25,\n"
				+ "  \"authorList\":null,\n"
				+ "}";

		wireMockServer.stubFor( get("/defaultBook?vin=abcde12345")
				.withHeader("NadId", equalTo("test"))
				.withHeader("Vin", equalTo("test"))
				.willReturn( status(200).withBody(returnValue)));


		String gatewayUrl = "http://localhost:" + port + "/myDefaultBook";

		MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
		headers.add("NadId", "test");
		headers.add("Vin", "test");

		//language=JSON
		String json = "{\n"
				+ "  \"vin\": \"abcde12345\"\n"
				+
				"}";


		ResponseEntity<String> outputEntity = testRestTemplate.exchange(gatewayUrl, HttpMethod.POST,new HttpEntity<String>(json, headers),String.class);


		assertThat( outputEntity.getStatusCode() ).isEqualTo( HttpStatus.OK );
		assertThat( outputEntity.getBody() ).contains( "DefaultBook" );

	}



}
