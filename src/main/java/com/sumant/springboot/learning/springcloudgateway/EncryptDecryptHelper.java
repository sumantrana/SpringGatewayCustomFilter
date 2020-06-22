package com.sumant.springboot.learning.springcloudgateway;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class EncryptDecryptHelper {

	private static final String SECRET_KEY = "Thisisatestkeyfortesting";


	private static SecretKeySpec secretKey;
	private static byte[] key;

	public static String encrypt(String strToEncrypt){
		return encrypt(strToEncrypt, SECRET_KEY);
	}

	public static String decrypt(String strToDecrypt){
		return decrypt(strToDecrypt, SECRET_KEY);
	}

	public static void setKey(String myKey)
	{
		MessageDigest sha = null;
		try {
			key = myKey.getBytes("UTF-8");
			sha = MessageDigest.getInstance("SHA-1");
			key = sha.digest(key);
			key = Arrays.copyOf(key, 16);
			secretKey = new SecretKeySpec(key, "AES");
		}
		catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}


	public static String encrypt(String strToEncrypt, String secret)
	{
		try
		{
			setKey(secret);
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, secretKey);
			return Base64.getEncoder().encodeToString(cipher.doFinal(strToEncrypt.getBytes("UTF-8")));
		}
		catch (Exception e)
		{
			System.out.println("Error while encrypting: " + e.toString());
		}
		return null;
	}



	public static String decrypt(String strToDecrypt, String secret)
	{
		try
		{
			setKey(secret);
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
			cipher.init(Cipher.DECRYPT_MODE, secretKey);
			return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)));
		}
		catch (Exception e)
		{
			System.out.println("Error while decrypting: " + e.toString());
		}
		return null;
	}

	public static void main(String[] args) {

		String key = "Thisisatestkeyfortesting";

		//language=JSON
		String data = "{\n"
				+ "  \"title\": \"TestTitle1\",\n"
				+ "  \"author\": \"TestAuthor1\"\n"
				+ "}";

		System.out.println("Original String: " + data);

		String encryptedString = EncryptDecryptHelper.encrypt(data, key);

		System.out.println("Encrypted String: " + encryptedString);

		String decryptedString = EncryptDecryptHelper.decrypt(encryptedString, key);

		System.out.println("Decrypted String: " + decryptedString);

	}
}
