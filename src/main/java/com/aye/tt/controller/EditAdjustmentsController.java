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

import com.aye.tt.utilities.ConvertListObjToListDoc;
import com.aye.tt.utilities.DatabaseInfo;
import com.aye.tt.utilities.TimeConvertUtility;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

@CrossOrigin
@RestController
@RequestMapping("/edit-adjustments")
public class EditAdjustmentsController {
	
	MongoClientURI mUri = new MongoClientURI(DatabaseInfo.uri);

    MongoClient mongoClient = new MongoClient(mUri);
	//Accessing the database 
	MongoDatabase database = mongoClient.getDatabase("TimeTable"); 
	MongoCollection<Document> teacherCollection = database.getCollection("TeacherTimeTable");
	MongoCollection<Document> adjustmentCollection = database.getCollection("Adjustment");
	@GetMapping("/fetchPreviousAdjustment")
	public Document fetchPreviousAdjustment() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        LocalDateTime date = LocalDateTime.now();
		ZoneId indiaZoneId = ZoneId.of("Asia/Kolkata");
		ZonedDateTime indiaZonedDateTime = date.atZone(indiaZoneId);
        List<Document> adjustmentList = adjustmentCollection.find(Filters.and(Filters.eq("username",username),Filters.eq("date",indiaZonedDateTime.toLocalDate().toString()))).
        		projection(Projections.exclude("_id")).into(new ArrayList<Document>());
        if(adjustmentList.size()>0) {
        	Document d1 =  adjustmentList.get(0);
        	List<Document> adjList = (List<Document>)d1.get("adjustmentList");
        	HashMap<String, List<Document>> hashMap = new HashMap<String, List<Document>>();
    		for(Document d:adjList) {
    			if (!hashMap.containsKey(d.getString("teacherName"))) {
    			    List<Document> list = new ArrayList<Document>();
    			    list.add(d);

    			    hashMap.put(d.getString("teacherName"), list);
    			} else {
    			    hashMap.get(d.getString("teacherName")).add(d);
    			}
    		}
    		d1.append("adjustmentList", hashMap);
        	return d1;
        }
        else {
        	Document toBeSent = new Document();
        	toBeSent.append("status", "NA");
        	return toBeSent;
        }
	}

	@PostMapping("/changeAdjustments")
	public Document adjustment(@RequestBody Map<String,Object> data) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        
		List<Object> addAbsentListObj = (List<Object>)data.get("addAbsentList");
		List<Object> editAbsentListObj = (List<Object>)data.get("editAbsentList");
		List<Object> addExceptionListObj = (List<Object>)data.get("addExceptionList");
		List<Object> editExceptionListObj = (List<Object>)data.get("editExceptionList");
		List<Document> addAbsentList = ConvertListObjToListDoc.convert(addAbsentListObj);
		List<Document> editAbsentList = ConvertListObjToListDoc.convert(editAbsentListObj);
		List<Document> addExceptionList = ConvertListObjToListDoc.convert(addExceptionListObj);
		List<Document> editExceptionList = ConvertListObjToListDoc.convert(editExceptionListObj);
		LocalDateTime date = LocalDateTime.now();
		ZoneId indiaZoneId = ZoneId.of("Asia/Kolkata");
		ZonedDateTime indiaZonedDateTime = date.atZone(indiaZoneId);
		int dayOfWeek = indiaZonedDateTime.getDayOfWeek().getValue() - 1;
		if(dayOfWeek >5)
			dayOfWeek = 0;
		
		List<Document> adjustmentListDoc = adjustmentCollection.find(Filters.and(Filters.eq("username",username),Filters.eq("date",indiaZonedDateTime.toLocalDate().toString()))).
        		into(new ArrayList<Document>());
        if(adjustmentListDoc.size() == 0) {
        	Document toBeSent = new Document();
        	toBeSent.append("status", "NA");
        	return toBeSent;
        }
        Document prevAdjustment = adjustmentListDoc.get(0);
        List<Document> previousAbsentList = (List<Document>)prevAdjustment.get("absentList");
		List<Document> previousAdjustmentList = (List<Document>)prevAdjustment.get("adjustmentList");
		List<Document> previousExceptionList = (List<Document>)prevAdjustment.get("exceptionList");
		List<Document> previousFailedAdjustmentList = (List<Document>)prevAdjustment.get("failedAdjustmentList");
        
		for(Document d:editExceptionList) {
			System.out.println("removing from exception list");
			
			System.out.println(previousExceptionList.removeIf((o -> o.getString("_id").equals(d.getString("_id")))));
			if(d.getString("status").equalsIgnoreCase("Partial")) {
				Document teacherDoc = teacherCollection.find(Filters.eq("_id", new ObjectId(d.getString("_id")))).projection(Projections.exclude("timeTable")).into(new ArrayList<Document>()).get(0);
				d.append("teacherName", teacherDoc.getString("teacherName"));
				previousExceptionList.add(d);
				System.out.println(d.getString("_id"));
			}
		}
		
		for(Document d:addExceptionList) {
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
			System.out.println("adding to exception list");
			previousExceptionList.add(d);
		}
		List<Document> temp = new ArrayList<Document>(previousAdjustmentList);
		for(Document d:editAbsentList) {
			System.out.println("removing from previous adjustments");
			if(d.getString("status").equalsIgnoreCase("Complete")) {
				System.out.println("Complete");
				System.out.println(previousAdjustmentList.removeIf(o -> o.getString("previousId").equals(d.getString("_id"))));
			}else if(d.getString("status").equalsIgnoreCase("Partial")) {
				for(Document d1 : temp) {
					if(d1.getString("previousId").equals(d.getString("_id"))) {
						if(!(TimeConvertUtility.convertToMillis(d.getString("startTime")).longValue() 
								<= TimeConvertUtility.convertToMillis(d1.getString("endTime")).longValue()
								&& TimeConvertUtility.convertToMillis(d1.getString("startTime")).longValue() 
								<= TimeConvertUtility.convertToMillis(d.getString("endTime")).longValue())) {
							System.out.println("Partial");
							System.out.println(previousAdjustmentList.removeIf(k -> ((k.getString("previousId").equals(d.getString("_id"))
										&& 
										!(TimeConvertUtility.convertToMillis(d.getString("startTime")).longValue() 
												<= TimeConvertUtility.convertToMillis(k.getString("endTime")).longValue()
												&& TimeConvertUtility.convertToMillis(k.getString("startTime")).longValue() 
												<= TimeConvertUtility.convertToMillis(d.getString("endTime")).longValue())
									))));
						}
					}
				}
/*				temp.stream().
				filter(o -> )
				.iterator().forEachRemaining(o->{
					if(!(TimeConvertUtility.convertToMillis(d.getString("startTime")).longValue() 
							<= TimeConvertUtility.convertToMillis(o.getString("endTime")).longValue()
							&& TimeConvertUtility.convertToMillis(o.getString("startTime")).longValue() 
							<= TimeConvertUtility.convertToMillis(d.getString("endTime")).longValue())) {
						previousAdjustmentList.removeIf(k -> k.getString("_id").equals(d.getString("_id")));
					}
				});				
*/			}
			
		}
		temp = new ArrayList<Document>(previousAbsentList);
		for(Document d:editAbsentList) {
			System.out.println("Editing absent list");
			System.out.println(previousAbsentList.removeIf(o -> o.getString("_id").equals(d.getString("_id"))));
			if(d.getString("status").equalsIgnoreCase("Partial")) {
				Document teacherDoc = teacherCollection.find(Filters.eq("_id", new ObjectId(d.getString("_id")))).projection(Projections.exclude("timeTable")).into(new ArrayList<Document>()).get(0);
				d.append("teacherName", teacherDoc.getString("teacherName"));
				previousAbsentList.add(d);
				System.out.println(d.getString("_id"));
			}
		}
		
		for(Document d:addAbsentList) {
			System.out.println("adding lectures to absent list");
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
				System.out.println(((Document)d1.get("timeSlot")).getString("startTime"));
				System.out.println(d.getString("startTime"));
				/*if(TimeConvertUtility.convertToMillis(((Document)d1.get("timeSlot")).getString("startTime")) >= TimeConvertUtility.convertToMillis(d.getString("startTime"))) {  // greater than
					flag = true;
				}
				if(TimeConvertUtility.convertToMillis(((Document)d1.get("timeSlot")).getString("endTime")) > TimeConvertUtility.convertToMillis(d.getString("endTime"))) {  //greater than
					flag = false;
				}
				if(TimeConvertUtility.convertToMillis(((Document)d1.get("timeSlot")).getString("startTime")) <= TimeConvertUtility.convertToMillis(d.getString("endTime"))) {  //greater than
					flag = true;
				}*/
				if((TimeConvertUtility.convertToMillis(((Document)d1.get("timeSlot")).getString("startTime")) 
						<= TimeConvertUtility.convertToMillis(d.getString("endTime")) && 
								TimeConvertUtility.convertToMillis(((Document)d1.get("timeSlot")).getString("endTime")) 
								>= TimeConvertUtility.convertToMillis(d.getString("startTime")))
						&& !(d1.get("class").equals("Free"))) {
					lectures.add(d1);
				}
			}
			System.out.println("Lecturessss :"  + lectures);
			d.append("lectures", lectures);
			d.append("teacherName", teacherDoc.getString("teacherName"));
			System.out.println(teacherDoc.getString("teacherName"));
		}
		
		for(Document d:addAbsentList) {
			d.append("startTime", TimeConvertUtility.formatTime(d.getString("startTime")));
			d.append("endTime", TimeConvertUtility.formatTime(d.getString("endTime")));
		}
		
		for(Document d:previousExceptionList) {
			d.append("startTime", TimeConvertUtility.formatTime(d.getString("startTime")));
			d.append("endTime", TimeConvertUtility.formatTime(d.getString("endTime")));
		}
		
		for(Document d:addAbsentList) {
			System.out.println("adding absent list");
			previousAbsentList.add(d);
		}
		
		List<Document> adjustmentList = new ArrayList<Document>(previousAdjustmentList);
		List<Document> failedAdjustmentList = new ArrayList<Document>(previousFailedAdjustmentList);
		for(Document d:addAbsentList) {
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
				adjustment = findAdjustment(dayOfWeek,classLevel,lecture,previousAbsentList,adjustmentList,previousExceptionList,0);
				if(adjustment == null) {
					adjustment = findAdjustment(dayOfWeek,classLevel,lecture,previousAbsentList,adjustmentList,previousExceptionList,0.7);
					//System.out.println("Not able to adjustment");
					if(adjustment == null) {
						adjustment = findAdjustment(dayOfWeek,classLevel,lecture,previousAbsentList,adjustmentList,previousExceptionList,0.5);
						//System.out.println("Not able to adjustment");
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
					adjustment.append("previousId", d.getString("_id"));
					adjustment.append("previousTeacherName", d.getString("teacherName"));
					adjustmentList.add(adjustment);
				}
				System.out.println(Arrays.toString(adjustmentList.toArray()));
				//if adjustment not in adjustmentList && teacher not absent List && teacher not in exception list
				//then add it to adjustment List
				//if adjustment == null adjustment not possible

			}
		}
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
		Document toBeSaved = prevAdjustment;
		toBeSaved.append("adjustmentList", adjustmentList);
		toBeSaved.append("failedAdjustmentList", failedAdjustmentList);
		toBeSaved.append("absentList", previousAbsentList);
		toBeSaved.append("exceptionList", previousExceptionList);
		toBeSaved.append("username", username);
		toBeSaved.append("date", indiaZonedDateTime.toLocalDate().toString());
		toBeSaved.append("dayOfWeek", dayOfWeek);
		//adjustmentCollection.insertOne(toBeSaved); //update this instead of insert
		Bson filter = new Document("_id", toBeSaved.get("_id"));
		toBeSaved.remove("_id");
		Bson updateOperationDocument = new Document("$set", toBeSaved);
		System.out.println(adjustmentCollection.updateOne(filter, updateOperationDocument).getModifiedCount());
		
		Document toBeSent = new Document();
		toBeSent.append("adjustmentList", hashMap);
		toBeSent.append("failedAdjustmentList", failedAdjustmentList);
		return toBeSent;		
	}

	
	private Document findAdjustment(int dayOfWeek, int classLevel, Document lecture, 
			List<Document> absentList, List<Document> adjustments, List<Document> exceptionList, double temp) {
		// TODO Auto-generated method stub
		//		System.out.println("DayOFWeek" + dayOfWeek);
		//		System.out.println("lecture" + lecture);
		//		System.out.println("absentList" + absentList.size());
		//		System.out.println("adjustments" + adjustments.size());
		//		System.out.println("exceptionList" + exceptionList.size());
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
			System.out.println("TimeSlot" + timeSlot);
			//List<Document> teacherDataList = teacherCollection.find(Filters.eq("classLevel",resultLevel[i])).into(new ArrayList<Document>());
			List<Document> teacherDataList = teacherCollection.find().into(new ArrayList<Document>());
			System.out.println("TeacherDataList" + teacherDataList.size());
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
					//System.out.println("IFAbsentList");
					for(Document currentLecture:((List<List<Document>>)d.get("timeTable")).get(dayOfWeek)) {
						Document currentTimeSlot = (Document)currentLecture.get("timeSlot");
						//System.out.println("CurrentLecture" + currentLecture);
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
										((TimeConvertUtility.convertToMillis(o.getString("startTime")) 
												<= TimeConvertUtility.convertToMillis(currentTimeSlot.getString("endTime")) &&
												TimeConvertUtility.convertToMillis(currentTimeSlot.getString("startTime")) 
												<= TimeConvertUtility.convertToMillis(o.getString("endTime"))) )
										)
										).findFirst().isPresent()) {   // 1. greater than   2. less than 3. greater 4. less 5.greater 6. less
							//System.out.println("end");
							//System.out.println("CurrentTimeSlot" + currentTimeSlot);
							//System.out.println("TimeSlot" + timeSlot);
							//							System.out.println(TimeConvertUtility.convertToMillis(currentTimeSlot.getString("startTime")) +"--"+ (TimeConvertUtility.convertToMillis(timeSlot.getString("startTime"))));
							//							System.out.println(TimeConvertUtility.convertToMillis(currentTimeSlot.getString("endTime")) +"---"+ (TimeConvertUtility.convertToMillis(timeSlot.getString("endTime"))));
							//							System.out.println(currentLecture.getString("class").equals("Free"));
							//							System.out.println("CurrentLEcture" + currentLecture);

							// if temp = 0
							if(temp == 0) {
							if(TimeConvertUtility.convertToMillis(currentTimeSlot.getString("startTime")).longValue() 		== 
									(TimeConvertUtility.convertToMillis(timeSlot.getString("startTime")).longValue()) 
									&& 
									TimeConvertUtility.convertToMillis(currentTimeSlot.getString("endTime")).longValue() == 
									(TimeConvertUtility.convertToMillis(timeSlot.getString("endTime")).longValue()) && 
									currentLecture.getString("class").equals("Free")) {
								System.out.println("Matched");
								lecture.append("teacherId", d.get("_id").toString());
								lecture.append("teacherName", d.getString("teacherName"));
								lecture.append("startTime", timeSlot.getString("startTime"));
								lecture.append("endTime", timeSlot.getString("endTime"));
								lecture.append("percentage", 100);
								System.out.println("Current Lecture" + lecture);
								return lecture;
							}
							}
							// temp = 1
							else {
								long currentTimeStart =TimeConvertUtility.convertToMillis(currentTimeSlot.getString("startTime")).longValue();
								long currentTimeEnd = TimeConvertUtility.convertToMillis(currentTimeSlot.getString("endTime")).longValue();
								long timeStart =TimeConvertUtility.convertToMillis(timeSlot.getString("startTime")).longValue();
								long timeEnd = TimeConvertUtility.convertToMillis(timeSlot.getString("endTime")).longValue();
								if(currentTimeStart < timeStart && currentTimeEnd > timeEnd) {
									//System.out.println("Matched");
									lecture.append("teacherId", d.get("_id").toString());
									lecture.append("teacherName", d.getString("teacherName"));
									lecture.append("startTime", timeSlot.getString("startTime"));
									lecture.append("endTime", timeSlot.getString("endTime"));
									lecture.append("percentage", 100);
									System.out.println("<  && >");
									return lecture;
								}
								else if(currentTimeStart > timeStart && currentTimeEnd < timeEnd) {
									double diff = (currentTimeEnd - currentTimeStart)/(timeEnd - timeStart);
									if(diff >= temp) {
										lecture.append("teacherId", d.get("_id").toString());
										lecture.append("teacherName", d.getString("teacherName"));
										lecture.append("startTime", timeSlot.getString("startTime"));
										lecture.append("endTime", timeSlot.getString("endTime"));
										lecture.append("percentage", temp*100);
										//System.out.println("Current Lecture" + lecture);
										System.out.println(">  && <");
										return lecture;
									}	
								}
								else if(currentTimeStart < timeStart && currentTimeEnd < timeEnd) {
									double diff = (timeStart - currentTimeEnd)/(timeEnd - timeStart);
									if(diff >= temp) {
										lecture.append("teacherId", d.get("_id").toString());
										lecture.append("teacherName", d.getString("teacherName"));
										lecture.append("startTime", timeSlot.getString("startTime"));
										lecture.append("endTime", timeSlot.getString("endTime"));
										lecture.append("percentage", temp*100);
										//System.out.println("Current Lecture" + lecture);
										System.out.println("<  && <");
										return lecture;
									}	
								}
								else if(currentTimeStart > timeStart && currentTimeEnd > timeEnd) {
									double diff = (currentTimeStart - timeEnd)/(timeEnd - timeStart);
									if(diff >= temp) {
										lecture.append("teacherId", d.get("_id").toString());
										lecture.append("teacherName", d.getString("teacherName"));
										lecture.append("startTime", timeSlot.getString("startTime"));
										lecture.append("endTime", timeSlot.getString("endTime"));
										lecture.append("percentage", temp*100);
										//System.out.println("Current Lecture" + lecture);
										System.out.println(">  && >");
										return lecture;
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
