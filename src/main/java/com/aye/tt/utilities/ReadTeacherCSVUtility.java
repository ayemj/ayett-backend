package com.aye.tt.utilities;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;

public class ReadTeacherCSVUtility {
	
	public static void readTeacherCSV(String username, MongoCollection<Document> teacherCollection) {
		
		List<String> contentTeacher=ReadTeacherCSVUtility.readDataFromCsv("upload/"+username+"/teacherTimeTable.csv");
		System.out.println("Content: " +contentTeacher.get(0));
		List<LinkedHashMap<String,Object>> teacherNewArray=new ArrayList<LinkedHashMap<String,Object>>();

		for (int i=0;i<contentTeacher.size();i=i+10){
			LinkedHashMap<String,Object> teacherJson=new  LinkedHashMap<String,Object>();
			String[] teacherArray = contentTeacher.get(i).split(",");
			System.out.println(teacherArray[0]);
			String teacherName=teacherArray[0].trim().split("-")[1];
			teacherJson.put("teacherName",teacherName.trim());
			Integer[] classLevel= {0,0};
			String[] timeArray = contentTeacher.get(i+1).split(",");
			timeArray=Arrays.copyOfRange(timeArray,1,timeArray.length);
			System.out.println("Time Array" + Arrays.toString(timeArray));
			List<List<Map<String,Object>>> timeTable=new ArrayList<List<Map<String,Object>>> ();
			for(int j=i+3;j<i+9;j++){
		
				List<Map<String,Object>> daywise = new LinkedList<Map<String,Object>>();
				String[] dayArray = contentTeacher.get(j).split(",");
				System.out.println("Content Teacher" + contentTeacher.get(j));
				System.out.println("Day Array" + Arrays.toString(dayArray));
				dayArray=  Arrays.copyOfRange(dayArray, 1, dayArray.length);
				System.out.println("Day Array" + Arrays.toString(dayArray));
				for(int l=0;l<timeArray.length;l++){
					if(dayArray[l].trim().toLowerCase().contains("break")) {
						continue;
					}else if(dayArray[l].trim().toLowerCase().contains("free")){
						dayArray[l] = "Free";
					}else if(dayArray[l].trim().toLowerCase().contains("reserved")){
						dayArray[l] = "Reserved";
					}
					
					
					Map<String,String> timeJson = new LinkedHashMap<String,String>();
					Map<String,Object> lecture =new LinkedHashMap<String,Object>();
					if(!timeArray[l].trim().equals("")){
						//Map<String,String> timeJson=new LinkedHashMap<String,String>();
						lecture.put("class",dayArray[l].trim() + " ");
						if(lecture.get("class").toString().startsWith("I ") || lecture.get("class").toString().startsWith("II ") || lecture.get("class").toString().startsWith("III ") || lecture.get("class").toString().startsWith("IV ") || lecture.get("class").toString().startsWith("V ")){
							classLevel[0] += 1;
						}else if(lecture.get("class").toString().startsWith("VI")){
							classLevel[1] += 1;
						}
						lecture.put("class",lecture.get("class").toString().trim());
						if (lecture.get("class").toString().equals("")){
							lecture.put("class","Free");
						}
						String[] timearr = timeArray[l].trim().split("-");
						System.out.println("TimeArray" + timearr[0]);
						timearr[0] = TimeConvertUtility.formatTime(timearr[0]);
						timearr[1] = TimeConvertUtility.formatTime(timearr[1]);
						timeJson.put("startTime", timearr[0]);
						timeJson.put("endTime", timearr[1]);
						lecture.put("timeSlot", timeJson);
						daywise.add(lecture);
					}


				}
				String temp =dayArray[0].trim() + " ";
				if(temp.startsWith("I ") || temp.startsWith("II ") || temp.startsWith("III ") || temp.startsWith("IV ") || temp.startsWith("V ")){
					classLevel[0] += 48;
				}else if(temp.startsWith("VI")){
					classLevel[1] += 48;
				}
				timeTable.add(daywise);

			}

			if((Arrays.stream(timeArray).filter( o -> !(o.equals("") || o.equals("\r")) ).collect( Collectors.toList() )).size()==8){
				teacherJson.put("classLevel",2);

			}
			else{
				teacherJson.put("classLevel",classLevel[0]>classLevel[1]?0:1);
			}


			teacherJson.put("timeTable",timeTable);
			teacherJson.put("username", username);
			teacherNewArray.add(teacherJson);
			
			teacherCollection.insertOne(new Document(teacherJson));
		}
		
		ObjectMapper mapper = new ObjectMapper();
		try {
			System.out.println( mapper.writeValueAsString(teacherNewArray));
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Success");
		
	}
	
	private static List<String> readDataFromCsv(String fileName){
		BufferedReader br;
		List<String> contentTeacher=new ArrayList<String>();
		try {
			br = new BufferedReader(new FileReader(fileName));
			String line = null;
			//System.out.println(line);
			while((line=br.readLine())!=null){
				contentTeacher.add(line+ " ");
			}
			br.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return contentTeacher;
	}

}
