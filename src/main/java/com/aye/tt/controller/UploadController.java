package com.aye.tt.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.bson.Document;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.aye.tt.utilities.DatabaseInfo;
import com.aye.tt.utilities.ReadTeacherCSVUtility;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

@CrossOrigin
@RestController
@RequestMapping("/UploadFiles")
public class UploadController {

    //Save the uploaded file to this folder
    private static String UPLOADED_FOLDER = "upload";
    
    

    MongoClient mongoClient = new MongoClient(DatabaseInfo.host,DatabaseInfo.port);

	//Accessing the database 

	MongoDatabase database = mongoClient.getDatabase("TimeTable"); 
	MongoCollection<Document> teacherCollection = database.getCollection("TeacherTimeTable");
    
    @PostMapping("/uploadTimeTable") 
    public Document uploadTimeTable(@RequestParam("studentTimeTable") MultipartFile studentTimeTable,
    								@RequestParam("teacherTimeTable") MultipartFile teacherTimeTable,
                                   RedirectAttributes redirectAttributes) {
    	Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        if(teacherCollection.find(Filters.eq("username", name)).into(new ArrayList<Document>()).size()>0) {
        	Document d = new Document();
            d.append("status", "done");
            d.append("message", "");
            return d;
        }
        if (studentTimeTable.isEmpty() || teacherTimeTable.isEmpty() ) {
            Document d = new Document();
            d.append("status", "done");
            d.append("message", "Please select a file to upload");
            return d;
        }

        try {

            // Get the file and save it somewhere
            byte[] bytes = studentTimeTable.getBytes();
            Path path = Paths.get(UPLOADED_FOLDER + "/" + name + "/studentTimeTable.csv");
            try {
				Files.createDirectories(path.getParent());
				Files.createFile(path);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			}
            Files.write(path, bytes);
            bytes = teacherTimeTable.getBytes();
            path = Paths.get(UPLOADED_FOLDER + "/" + name + "/" + "/teacherTimeTable.csv");
            try {
				Files.createDirectories(path.getParent());
				Files.createFile(path);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			}
            Files.write(path, bytes);
            ReadTeacherCSVUtility.readTeacherCSV(name,teacherCollection);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Document d = new Document();
        d.append("status", "done");
        d.append("message", "");
        return d;
    }
}
