package com.aye.tt.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aye.tt.utilities.DatabaseInfo;
import com.aye.tt.utilities.TimeConvertUtility;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

@CrossOrigin
@RestController
@RequestMapping("/adjustments")
public class AdjustmentsController {
	
	MongoClientURI mUri = new MongoClientURI(DatabaseInfo.uri);

    MongoClient mongoClient = new MongoClient(mUri);
	//Accessing the database 
	MongoDatabase database = mongoClient.getDatabase("TimeTable"); 
	MongoCollection<Document> teacherCollection = database.getCollection("TeacherTimeTable");
	MongoCollection<Document> adjustmentCollection = database.getCollection("Adjustment");
	
	@GetMapping("/discardAdjustments")
	public Document discardAdjustment() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        List<Document> adjList = adjustmentCollection.find(Filters.and(Filters.eq("username",username),Filters.eq("date",LocalDate.now().toString())))
        		.into(new ArrayList<Document>());
        Document d = new Document();
        if(adjList.size() > 0) {
			adjustmentCollection.deleteOne(adjList.get(0));
			d.append("status", "done");
            d.append("message", "");
            return d;
		}
        d.append("status", "notDone");
        d.append("message", "No Adjustment to delete");
        return d;  
	}
	
	@PostMapping("/getAdjustments")
	public Document adjustment(@RequestBody Map<String,List<Document>> data) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        List<Document> checkList = adjustmentCollection.find(Filters.and(Filters.eq("username",username),Filters.eq("date",LocalDate.now().toString()))).
        		projection(Projections.exclude("_id")).into(new ArrayList<Document>());
		if(checkList.size() > 0) {
			return new EditAdjustmentsController().fetchPreviousAdjustment();
		}
		
		List<Document> absentList = (List<Document>)data.get("absentList");
		Calendar calendar = Calendar.getInstance();
		LocalDateTime date = LocalDateTime.now();
		ZoneId indiaZoneId = ZoneId.of("Asia/Kolkata");
		ZonedDateTime indiaZonedDateTime = date.atZone(indiaZoneId);
		int dayOfWeek = indiaZonedDateTime.getDayOfWeek().getValue() - 1;
		if(dayOfWeek >5)
			dayOfWeek = 0;
		for(Document d:absentList) {
			Document teacherDoc = teacherCollection.find(Filters.eq("_id",new ObjectId((String)d.get("_id")))).into(new ArrayList<Document>()).get(0);
			List<List<Document>> timeTable = (List<List<Document>>)teacherDoc.get("timeTable");
			if(d.getString("reason").equals("Leave") || d.getString("reason").equals("Absent")) {
				if(d.getString("type").equals("1st Half")) {//change to "
					d.append("startTime", "08:10");
					d.append("endTime", "11:45");
				}else if(d.getString("type").equals("2nd Half")) {
					d.append("startTime", "12:20");
					d.append("endTime", "02:00");
				}
				else if(d.getString("type").equals("Full")) {
					d.append("startTime", ((Document)timeTable.get(dayOfWeek).get(0).get("timeSlot")).getString("startTime"));
					d.append("endTime", ((Document)timeTable.get(dayOfWeek).get(timeTable.get(dayOfWeek).size()-1).get("timeSlot")).getString("endTime"));
				}else {
					Document toBeSent = new Document();
					toBeSent.append("status", "notDone");
					toBeSent.append("message", "Invalid Type Valid Values --> 1st Half|2nd Half|Full");
			        return toBeSent;
				}
			}
			boolean flag = false;
			List<Document> lectures = new ArrayList<Document>();
			for(Document d1:timeTable.get(dayOfWeek)) {
				////System.out.println(((Document)d1.get("timeSlot")).getString("startTime"));
				////System.out.println(d.getString("startTime"));
				/*if(TimeConvertUtility.convertToMillis(((Document)d1.get("timeSlot")).getString("startTime")) >= TimeConvertUtility.convertToMillis(d.getString("startTime"))) {  // greater than
					flag = true;
				}
				if(TimeConvertUtility.convertToMillis(((Document)d1.get("timeSlot")).getString("endTime")) >= TimeConvertUtility.convertToMillis(d.getString("endTime"))) {  //greater than
					flag = false;
				}
				if(TimeConvertUtility.convertToMillis(((Document)d1.get("timeSlot")).getString("startTime")) >= TimeConvertUtility.convertToMillis(d.getString("endTime"))) {  //greater than
					flag = false;
				}*/
				if((TimeConvertUtility.convertToMillis(((Document)d1.get("timeSlot")).getString("startTime")) 
						<= TimeConvertUtility.convertToMillis(d.getString("endTime")) && 
								TimeConvertUtility.convertToMillis(((Document)d1.get("timeSlot")).getString("endTime")) 
								>= TimeConvertUtility.convertToMillis(d.getString("startTime")))
						&& !(d1.get("class").equals("Free"))
						&& !(d1.get("class").equals("Reserved"))
						) {
					lectures.add(d1);
				}
			}
			////System.out.println("Lecturessss :"  + lectures);
			d.append("lectures", lectures);
			d.append("teacherName", teacherDoc.getString("teacherName"));
		}


		// Exception List 

		List<Document> exceptionList = (List<Document>)data.get("exceptionList");
		for(Document d:exceptionList) {
			Document teacherDoc = teacherCollection.find(Filters.eq("_id",new ObjectId((String)d.get("_id")))).into(new ArrayList<Document>()).get(0);
			List<List<Document>> timeTable = (List<List<Document>>)teacherDoc.get("timeTable");
			boolean flag = false;
			List<Document> lectures = new ArrayList<Document>();
			for(Document d1:timeTable.get(dayOfWeek)) {
				/*if(TimeConvertUtility.convertToMillis(((Document)d1.get("timeSlot")).getString("startTime")) > TimeConvertUtility.convertToMillis(d.getString("startTime"))) {  // greater than
					flag = true;
				}
				if(TimeConvertUtility.convertToMillis(((Document)d1.get("timeSlot")).getString("endTime")) > TimeConvertUtility.convertToMillis(d.getString("endTime"))) {  //greater than
					flag = false;
				}
				if(TimeConvertUtility.convertToMillis(((Document)d1.get("timeSlot")).getString("startTime")) < TimeConvertUtility.convertToMillis(d.getString("endTime"))) {  //greater than
					flag = true;
				}
				if(flag) {
					lectures.add(d1);
				}*/
				if((TimeConvertUtility.convertToMillis(((Document)d1.get("timeSlot")).getString("startTime")) 
						<= TimeConvertUtility.convertToMillis(d.getString("endTime")) && 
								TimeConvertUtility.convertToMillis(((Document)d1.get("timeSlot")).getString("endTime")) 
								>= TimeConvertUtility.convertToMillis(d.getString("startTime")))
						&& !(d1.get("class").equals("Free"))) {
					lectures.add(d1);
				}
			}
			d.append("lectures", lectures);
			d.append("teacherName", teacherDoc.getString("teacherName"));
		}
		
		for(Document d:absentList) {
			d.append("startTime", TimeConvertUtility.formatTime(d.getString("startTime")));
			d.append("endTime", TimeConvertUtility.formatTime(d.getString("endTime")));
		}
		
		for(Document d:exceptionList) {
			d.append("startTime", TimeConvertUtility.formatTime(d.getString("startTime")));
			d.append("endTime", TimeConvertUtility.formatTime(d.getString("endTime")));
		}
		
		List<Document> adjustmentList = new ArrayList<Document>();
		List<Document> failedAdjustmentList = new ArrayList<Document>();
		for(Document d:absentList) {
			for(Document lecture : (List<Document>)d.get("lectures")) {
				int classLevel = -1;
				if(lecture.getString("class").startsWith("I ") || 
						lecture.getString("class").startsWith("II ") || 
						lecture.getString("class").startsWith("III ") || 
						lecture.getString("class").startsWith("IV ") || 
						lecture.getString("class").startsWith("V ")) {
					classLevel = 0;
				}else if(lecture.getString("class").startsWith("VI")) {
					classLevel = 1;
				}else {
					classLevel = 2;
				}
				Document adjustment = null;
				adjustment = findAdjustment(dayOfWeek,classLevel,lecture,absentList,adjustmentList,exceptionList,0,username);
				if(adjustment == null) {
					adjustment = findAdjustment(dayOfWeek,classLevel,lecture,absentList,adjustmentList,exceptionList,0.7,username);
					//////System.out.println("Not able to adjustment");
					if(adjustment == null) {
						adjustment = findAdjustment(dayOfWeek,classLevel,lecture,absentList,adjustmentList,exceptionList,0.5,username);
						//////System.out.println("Not able to adjustment");
						if(adjustment == null) {
							System.out.println("Not able to adjustment");
							Document failedAdjustment = new Document();
							failedAdjustment.append("_id", d.getString("_id"));
							failedAdjustment.append("teacherName", d.getString("teacherName"));
							failedAdjustment.append("class", lecture.getString("class"));
							Document timeSlot = (Document)lecture.get("timeSlot");
							failedAdjustment.append("startTime", timeSlot.getString("startTime"));
							failedAdjustment.append("endTime", timeSlot.getString("endTime"));
							failedAdjustmentList.add(failedAdjustment);
							
						}
						else {
							adjustment.append("previousId", d.getString("_id"));
							adjustment.append("previousTeacherName", d.getString("teacherName"));
							adjustmentList.add(adjustment);
						}
					}
					else {
						adjustment.append("previousId", d.getString("_id"));
						adjustment.append("previousTeacherName", d.getString("teacherName"));
						adjustmentList.add(adjustment);
					}
				}
				else {
					incrementTeacherAdjustmentCount(adjustment.getString("teacherId"));
					adjustment.append("previousId", d.getString("_id"));
					adjustment.append("previousTeacherName", d.getString("teacherName"));
					adjustmentList.add(adjustment);
				}
				////System.out.println(Arrays.toString(adjustmentList.toArray()));
				//if adjustment not in adjustmentList && teacher not absent List && teacher not in exception list
				//then add it to adjustment List
				//if adjustment == null adjustment not possible

			}
		}

		//    		Document teacherDoc = teacherCollection.find(Filters.eq("_id",new ObjectId((String)data.get("_id")))).into(new ArrayList<Document>()).get(0);
		//    		List<List<Document>> timeTable = (List<List<Document>>)teacherDoc.get("timetable");
		//    		////System.out.println(timeTable);
		//    		String toBeSent = ((Document)timeTable
		//    				.get(0)
		//    				.get(0)
		//    				.get("timeSlot"))
		//    				.getString("startTime");
		adjustmentList.forEach(o -> {
            o.remove("timeSlot");
        });
		HashMap<String, List<Document>> hashMap = new HashMap<String, List<Document>>();
		for(Document d:adjustmentList) {
			if (!hashMap.containsKey(d.getString("teacherName"))) {
			    List<Document> list = new ArrayList<Document>();
			    list.add(d);

			    hashMap.put(d.getString("teacherName"), list);
			} else {
			    hashMap.get(d.getString("teacherName")).add(d);
			}
		}
		
		HashMap<String, List<Document>> hashMap2 = new HashMap<String, List<Document>>();
		for(Document d:failedAdjustmentList) {
			if (!hashMap.containsKey(d.getString("teacherName"))) {
			    List<Document> list = new ArrayList<Document>();
			    list.add(d);

			    hashMap.put(d.getString("teacherName"), list);
			} else {
			    hashMap.get(d.getString("teacherName")).add(d);
			}
		}
		
		Document toBeSaved = new Document();
		toBeSaved.append("adjustmentList", adjustmentList);
		toBeSaved.append("failedAdjustmentList", failedAdjustmentList);
		toBeSaved.append("absentList", absentList);
		toBeSaved.append("exceptionList", exceptionList);
		toBeSaved.append("username", username);
		toBeSaved.append("date", indiaZonedDateTime.toLocalDate().toString());
		toBeSaved.append("dayOfWeek", dayOfWeek);
		adjustmentCollection.insertOne(toBeSaved);
		Document toBeSent = new Document();
		toBeSent.append("adjustmentList", hashMap);
		toBeSent.append("failedAdjustmentList", hashMap2);
		return toBeSent;
	}

	private boolean incrementTeacherAdjustmentCount(String teacherId) {
		Document teacher = teacherCollection.find(Filters.eq("_id", new ObjectId(teacherId))).into(new ArrayList<Document>()).get(0);
		if(teacher.getInteger("adjustmentCount") == null) {
			teacher.append("adjustmentCount", 1);
		}
		else {
			teacher.append("adjustmentCount", teacher.getInteger("adjustmentCount") + 1);
		}
		Bson filter = new Document("_id", teacher.get("_id"));
		teacher.remove("_id");
		Bson updateOperationDocument = new Document("$set", teacher);
		////System.out.println(teacherCollection.updateOne(filter, updateOperationDocument).getModifiedCount());
		
		return true;
		
	}

	private Document findAdjustment(int dayOfWeek, int classLevel, Document lecture, 
			List<Document> absentList, List<Document> adjustments, List<Document> exceptionList, double temp, String username) {
		//System.out.println("000000000000000000000000000000000000000000000000000000000000000000000000000000000");
		//System.out.println("To be adjusted time slot " + (Document)lecture.get("timeSlot"));
		
		// TODO Auto-generated method stub
		//		////System.out.println("DayOFWeek" + dayOfWeek);
		//		////System.out.println("lecture" + lecture);
		//		////System.out.println("absentList" + absentList.size());
		//		////System.out.println("adjustments" + adjustments.size());
		//		////System.out.println("exceptionList" + exceptionList.size());
		int[] zeroLevel = {0,1,2};
		int[] oneLevel = {1,2,0};
		int[] twoLevel = {2,1,0};
		int [] resultLevel = new int[3];
		if(classLevel == zeroLevel[0]) {
			resultLevel = zeroLevel;
		}
		else if(classLevel == oneLevel[0]) {
			resultLevel = oneLevel;
		}
		else {
			resultLevel = twoLevel;
		}
		classLevel = 0;
		Document timeSlot = (Document)lecture.get("timeSlot");
		for(int i=0;i<2;i++)
		{
			////System.out.println("TimeSlot" + timeSlot);
			List<Document> teacherDataList = teacherCollection.find(Filters.and(Filters.eq("username",username),Filters.eq("classLevel",resultLevel[i]))).sort(new BasicDBObject("adjustmentCount",1)).into(new ArrayList<Document>());
			//List<Document> teacherDataList = teacherCollection.find().sort(new BasicDBObject("adjustmentCount",1)).into(new ArrayList<Document>());
			////System.out.println("TeacherDataList" + teacherDataList.size());
			for(Document teacher : adjustments) {
				Optional<Document> t1 =  teacherDataList.stream().filter(o -> o.get("_id").toString().equals(teacher.getString("_id"))).findFirst();
				if(t1.isPresent()) {
					Document tempDoc = t1.get();
					teacherDataList.remove(teacherDataList.indexOf(tempDoc));
					teacherDataList.add(tempDoc);
				}
			}

			for(Document d : teacherDataList) {
				if(!absentList.stream().filter(o -> o.getString("_id").equals(d.get("_id").toString())).findFirst().isPresent()) {
					//////System.out.println("IFAbsentList");
					for(Document currentLecture:((List<List<Document>>)d.get("timeTable")).get(dayOfWeek)) {
						if(currentLecture.getString("class").equals("Free")) {
							Document currentTimeSlot = (Document)currentLecture.get("timeSlot");
							////System.out.println(currentTimeSlot);
							//////System.out.println("CurrentLecture" + currentLecture);
							if(!exceptionList.stream().filter(o -> (o.getString("_id").equals(d.get("_id").toString()) && 
									((TimeConvertUtility.convertToMillis(o.getString("startTime")) <= TimeConvertUtility.convertToMillis(currentTimeSlot.getString("endTime")) &&
											TimeConvertUtility.convertToMillis(currentTimeSlot.getString("startTime")) <= TimeConvertUtility.convertToMillis(o.getString("endTime"))) ||
											(TimeConvertUtility.convertToMillis(o.getString("startTime")) >= TimeConvertUtility.convertToMillis(currentTimeSlot.getString("startTime")) &&
													TimeConvertUtility.convertToMillis(o.getString("endTime")) <= TimeConvertUtility.convertToMillis(currentTimeSlot.getString("startTime")) &&
													TimeConvertUtility.convertToMillis(o.getString("startTime")) >= TimeConvertUtility.convertToMillis(currentTimeSlot.getString("endTime")) && 
													TimeConvertUtility.convertToMillis(o.getString("endTime")) <= TimeConvertUtility.convertToMillis(currentTimeSlot.getString("endTime"))))
									)
									).findFirst().isPresent()
									&& 
									!adjustments.stream().filter(o -> (o.getString("teacherId").equals(d.get("_id").toString()) && 
											((TimeConvertUtility.convertToMillis(o.getString("startTime")) <= TimeConvertUtility.convertToMillis(currentTimeSlot.getString("endTime")) &&
													TimeConvertUtility.convertToMillis(currentTimeSlot.getString("startTime")) <= TimeConvertUtility.convertToMillis(o.getString("endTime"))) ||
													(TimeConvertUtility.convertToMillis(((Document)o.get("timeSlot")).getString("startTime")) >= TimeConvertUtility.convertToMillis(currentTimeSlot.getString("startTime")) &&
															TimeConvertUtility.convertToMillis(((Document)o.get("timeSlot")).getString("endTime")) <= TimeConvertUtility.convertToMillis(currentTimeSlot.getString("startTime")) &&
															TimeConvertUtility.convertToMillis(((Document)o.get("timeSlot")).getString("startTime")) >= TimeConvertUtility.convertToMillis(currentTimeSlot.getString("endTime")) && 
															TimeConvertUtility.convertToMillis(((Document)o.get("timeSlot")).getString("endTime")) <= TimeConvertUtility.convertToMillis(currentTimeSlot.getString("endTime"))))
											)
											).findFirst().isPresent()) {   // 1. greater than   2. less than 3. greater 4. less 5.greater 6. less
								//////System.out.println("end");
								//////System.out.println("CurrentTimeSlot" + currentTimeSlot);
								//////System.out.println("TimeSlot" + timeSlot);
								//							////System.out.println(TimeConvertUtility.convertToMillis(currentTimeSlot.getString("startTime")) +"--"+ (TimeConvertUtility.convertToMillis(timeSlot.getString("startTime"))));
								//							////System.out.println(TimeConvertUtility.convertToMillis(currentTimeSlot.getString("endTime")) +"---"+ (TimeConvertUtility.convertToMillis(timeSlot.getString("endTime"))));
								//							////System.out.println(currentLecture.getString("class").equals("Free"));
								//							////System.out.println("CurrentLEcture" + currentLecture);
	
								// if temp = 0
								if(temp == 0) {
									if(TimeConvertUtility.convertToMillis(currentTimeSlot.getString("startTime")).longValue() 		== 
											(TimeConvertUtility.convertToMillis(timeSlot.getString("startTime")).longValue()) 
											&& 
											TimeConvertUtility.convertToMillis(currentTimeSlot.getString("endTime")).longValue() == 
											(TimeConvertUtility.convertToMillis(timeSlot.getString("endTime")).longValue())) {
										////System.out.println("Matched");
										lecture.append("teacherId", d.get("_id").toString());
										lecture.append("teacherName", d.getString("teacherName"));
										lecture.append("startTime", timeSlot.getString("startTime"));
										lecture.append("endTime", timeSlot.getString("endTime"));
										lecture.append("percentage", 100);
										////System.out.println("Current Lecture" + lecture);
										//System.out.println("Adjusted lecture with no empty space "+lecture);
										return lecture;
									}
								}
								// temp = 1
								else {
									long currentTimeStart =TimeConvertUtility.convertToMillis(currentTimeSlot.getString("startTime")).longValue();
									long currentTimeEnd = TimeConvertUtility.convertToMillis(currentTimeSlot.getString("endTime")).longValue();
									long timeStart =TimeConvertUtility.convertToMillis(timeSlot.getString("startTime")).longValue();
									long timeEnd = TimeConvertUtility.convertToMillis(timeSlot.getString("endTime")).longValue();
									//System.out.println("##########################3");
									//System.out.println("currentTimeSlot " + currentTimeSlot );
									//System.out.println("timeSlot " + timeSlot );
									//System.out.println("currentTimeStart " + currentTimeStart );
									//System.out.println("currentTimeEnd " + currentTimeEnd );
									//System.out.println("timeStart " + timeStart );
									//System.out.println("timeEnd " + timeEnd );
									//System.out.println("##########################3");
									if(currentTimeStart <= timeStart && currentTimeEnd >= timeEnd) {
										//////System.out.println("Matched");
										lecture.append("teacherId", d.get("_id").toString());
										lecture.append("teacherName", d.getString("teacherName"));
										lecture.append("startTime", timeSlot.getString("startTime"));
										lecture.append("endTime", timeSlot.getString("endTime"));
										lecture.append("percentage", 100);
										//////System.out.println("<  && >");
										//System.out.println("1Adjusted lecture with no empty space temp!=0 "+lecture);
										return lecture;
									}
									else if(currentTimeStart >= timeStart && currentTimeEnd <= timeEnd) {
										double diff = (currentTimeEnd - currentTimeStart)/(timeEnd - timeStart);
										if(diff >= temp) {
											lecture.append("teacherId", d.get("_id").toString());
											lecture.append("teacherName", d.getString("teacherName"));
											lecture.append("startTime", currentTimeSlot.getString("startTime"));
											lecture.append("endTime", currentTimeSlot.getString("endTime"));
											lecture.append("percentage", temp*100);
											//////System.out.println("Current Lecture" + lecture);
											////System.out.println(">  && <");
											//System.out.println("2Adjusted lecture with "+(100-temp*100)+" empty space temp!=0 "+lecture);
											return lecture;
										}	
									}
									else if(currentTimeStart <= timeStart && currentTimeEnd <= timeEnd && currentTimeEnd > timeStart) {
										double diff = (timeStart - currentTimeEnd)/(timeEnd - timeStart);
										if(diff >= temp) {
											lecture.append("teacherId", d.get("_id").toString());
											lecture.append("teacherName", d.getString("teacherName"));
											lecture.append("startTime", timeSlot.getString("startTime"));
											lecture.append("endTime", currentTimeSlot.getString("endTime"));
											lecture.append("percentage", temp*100);
											//////System.out.println("Current Lecture" + lecture);
											////System.out.println("<  && <");
											//System.out.println("3Adjusted lecture with "+(100-temp*100)+" empty space temp!=0 "+lecture);
											return lecture;
										}	
									}
									else if(currentTimeStart >= timeStart && currentTimeEnd >= timeEnd && currentTimeStart < timeEnd) {
										double diff = (currentTimeStart - timeEnd)/(timeEnd - timeStart);
										if(diff >= temp) {
											lecture.append("teacherId", d.get("_id").toString());
											lecture.append("teacherName", d.getString("teacherName"));
											lecture.append("startTime", currentTimeSlot.getString("startTime"));
											lecture.append("endTime", timeSlot.getString("endTime"));
											lecture.append("percentage", temp*100);
											//////System.out.println("Current Lecture" + lecture);
											////System.out.println(">  && >");
											//System.out.println("4Adjusted lecture with "+(100-temp*100)+" empty space temp!=0 "+lecture);
											return lecture;
										}	
									}
								}
							}
						}
					}
				}
			}
		}

		//		Document d = new Document();
		//		d.append("teacherId", "");
		//		d.append("class","");
		//		d.append("timeSlot", new Document());
		return null;
	}

}
