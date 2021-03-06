package com.paysera.sdk.wallet;

import com.paysera.sdk.wallet.helpers.OkHTTPQueryStringConverter;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import okhttp3.HttpUrl;
import org.apache.commons.codec.binary.Base64;

public class RequestSigner {
    private NonceGeneratorInterface nonceGenerator;
    private OkHTTPQueryStringConverter okHTTPQueryStringConverter;

    public RequestSigner(NonceGeneratorInterface nonceGenerator, OkHTTPQueryStringConverter okHTTPQueryStringConverter) {
        this.nonceGenerator = nonceGenerator;
        this.okHTTPQueryStringConverter = okHTTPQueryStringConverter;
    }

    public String generateSignature(
        String macId,
        String macSecret,
        String host,
        Integer port,
        String method,
        String path,
        String query,
        byte[] body,
        String timestamp,
        Map<String, String> parameters
    ) throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        String nonce = this.nonceGenerator.generate();
        String ext = generateExt(body, parameters);

        if (query != null && !query.isEmpty()) {
            HttpUrl url = (new HttpUrl.Builder())
                .scheme("https")
                .host(host)
                .query(query)
                .build();

            path += "?" + url.encodedQuery();
        }

        // mac
        String mac = calculateMAC(
            timestamp,
            nonce,
            macSecret,
            method,
            path,
            host,
            port,
            ext
        );

        String authorizationHeader = String.format(
            "MAC id=\"%s\", ts=\"%s\", nonce=\"%s\", mac=\"%s\"",
            macId,
            timestamp,
            nonce,
            mac
        );

        if (ext != null) {
            authorizationHeader += ", ext=\"" + ext + "\"";
        }

        return authorizationHeader;
    }

    private String generateExt(byte[] content, Map<String, String> parameters) throws NoSuchAlgorithmException {
        Map<String, String> extParameters = new HashMap<>();

        if (content != null && content.length > 0) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] contentHash = digest.digest(content);

            extParameters.put("body_hash", new String(Base64.encodeBase64(contentHash)));
        }

        extParameters.putAll(parameters);

        return okHTTPQueryStringConverter.convertToEncodedQueryString(extParameters);
    }

    private String calculateMAC(String timestamp, String nonce, String secret, String httpMethod, String path, String host, Integer port, String ext) throws NoSuchAlgorithmException, InvalidKeyException {
        final StringBuilder macStringBuilder = new StringBuilder()
            .append(timestamp)
            .append("\n")
            .append(nonce)
            .append("\n")
            .append(httpMethod)
            .append("\n")
            .append(path)
            .append("\n")
            .append(host)
            .append("\n")
            .append(port)
            .append("\n")
            .append(ext)
            .append("\n");

        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");

        mac.init(secretKey);

        byte[] HMACdigest = mac.doFinal(macStringBuilder.toString().getBytes());

        return new String(Base64.encodeBase64(HMACdigest));
    }
}
