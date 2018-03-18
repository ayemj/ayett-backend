package com.aye.tt.utilities;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;

public class ConvertListObjToListDoc {
	public static List<Document> convert(List<Object> listObj){
		List<Document> listDoc = new ArrayList<>();
		for(Object o : listObj) {
			Map<String, Object> m = (LinkedHashMap<String, Object>)o;
			System.out.println(m.keySet().size());
			Document d = new Document();
			d.putAll(m);
			listDoc.add(d);
		}
		return listDoc;
	}
}
