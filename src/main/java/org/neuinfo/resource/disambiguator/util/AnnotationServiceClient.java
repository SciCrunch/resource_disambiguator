package org.neuinfo.resource.disambiguator.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

/**
 * 
 * @author bozyurt
 * 
 */
public class AnnotationServiceClient {
	private String serviceURL = "http://nif-services.neuinfo.org/servicesv1/v1/annotate";
	Pattern pattern = Pattern
			.compile("data-nif=\"(\\w+),\\S+,Resource\">\\w+</span>");
	
	Pattern pattern2 = Pattern
			.compile("data-nif=\"(\\w+),\\S+,(\\w+)\">\\w+</span>");
	

	public AnnotationServiceClient() {
	}

	public String annotate(String content, Map<String, String> paramsMap)
			throws Exception {
		HttpClient client = new DefaultHttpClient();
		URIBuilder builder = new URIBuilder(serviceURL);
		builder.setParameter("content", content);
		if (paramsMap != null) {
			for (String paramName : paramsMap.keySet()) {
				builder.setParameter(paramName, paramsMap.get(paramName));
			}
		}
		URI uri = builder.build();
		HttpGet httpGet = new HttpGet(uri);
		try {
			HttpResponse resp = client.execute(httpGet);
			HttpEntity entity = resp.getEntity();
			if (entity != null) {
				String s = EntityUtils.toString(entity);
				return s;
			}
		} finally {
			if (httpGet != null) {
				httpGet.releaseConnection();
			}
		}
		return null;
	}

	public String annotatePost(String content, Map<String, String> paramsMap)
			throws Exception {
		HttpClient client = new DefaultHttpClient();
		URIBuilder builder = new URIBuilder(serviceURL);
		URI uri = builder.build();
		HttpPost httpPost = new HttpPost(uri);

		List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(
				paramsMap.size() + 1);
		nameValuePairs.add(new BasicNameValuePair("content", content));
		for (String key : paramsMap.keySet()) {
			String value = paramsMap.get(key);
			if (key.equals("includeCat")) {
				String[] toks = value.split(",");
				for (String tok : toks) {
					nameValuePairs.add(new BasicNameValuePair(key, tok));
				}
			} else {
				nameValuePairs.add(new BasicNameValuePair(key, value));
			}
		}

		httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
		try {
			HttpResponse resp = client.execute(httpPost);
			HttpEntity entity = resp.getEntity();
			if (entity != null) {
				String s = EntityUtils.toString(entity);
				return s;
			}
		} finally {
			if (httpPost != null) {
				httpPost.releaseConnection();
			}
		}
		return null;
	}

	public Map<String, Integer> getTypes(String annotatedContent) {
		Matcher matcher = pattern.matcher(annotatedContent);
		Map<String, Integer> map = new HashMap<String, Integer>(7);

		while (matcher.find()) {
			String type = matcher.group(1);
			Integer count = map.get(type);
			if (count == null) {
				map.put(type, new Integer(1));
			} else {
				map.put(type, new Integer(count.intValue() + 1));
			}

		}
		return map;
	}
	
	public Map<String, Integer> getCategories(String annotatedContent) {
		Matcher matcher = pattern2.matcher(annotatedContent);
		Map<String, Integer> map = new HashMap<String, Integer>(7);

		while (matcher.find()) {
			String type = matcher.group(2);
			Integer count = map.get(type);
			if (count == null) {
				map.put(type, new Integer(1));
			} else {
				map.put(type, new Integer(count.intValue() + 1));
			}

		}
		return map;
	}

}
