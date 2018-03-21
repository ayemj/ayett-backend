package com.aye.tt.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aye.tt.utilities.DatabaseInfo;
import com.aye.tt.utilities.ReasonsUtility;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

@CrossOrigin
@RestController
@RequestMapping("/fetchData")
public class FetchDataController {

	MongoClient mongoClient = new MongoClient(DatabaseInfo.host,DatabaseInfo.port);
	MongoDatabase database = mongoClient.getDatabase("TimeTable"); 
	MongoCollection<Document> teacherCollection = database.getCollection("TeacherTimeTable");
	MongoCollection<Document> metaDataCollection = database.getCollection("MetaData");
	MongoCollection<Document> adjustmentCollection = database.getCollection("Adjustment");
	@GetMapping("/getTeacherList")
    public List<Document> getTeacherList() {
    	Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        List<Document> teacherList = teacherCollection.find(Filters.eq("username", username)).projection(Projections.include("_id","teacherName")).into(new ArrayList<Document>());
        for(Document teacher : teacherList) {
        	teacher.append("_id", teacher.get("_id").toString());
        }
        return teacherList;        
    }
	
	@GetMapping("/getReasons")
    public String getReasons() {
    	Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        
        return ReasonsUtility.reasons.toString();        
    }
	
	@GetMapping("/getUserData")
    public Document getPreviousAdjustmentData() {
    	Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        List<Document> adjustmentList = adjustmentCollection.find(Filters.and(Filters.eq("username",username),Filters.eq("date",LocalDate.now().toString()))).
		projection(Projections.include("_id")).into(new ArrayList<Document>());
        Document d = new Document();
        if(adjustmentList.size() == 0) {
        	d.append("adjustment", false);
        }else {
        	d.append("adjustment", true);
        }
        return d;       
    }
}
