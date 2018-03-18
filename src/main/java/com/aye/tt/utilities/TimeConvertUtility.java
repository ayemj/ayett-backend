package com.aye.tt.utilities;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeConvertUtility {
	
	public static Long convertToMillis(String time)  {
		int a=Integer.parseInt(time.split(":")[0]);
		if(a<6) {
			a=a+12;
			time=a+":"+time.split(":")[1];

		}
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm"); //24 hr format
		Date date;
		//System.out.println("TIme:" + time);
		try {
			date = sdf.parse(time);
			return date.toInstant().toEpochMilli();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public static String formatTime(String time) {
		if(time.trim().length() < 5) {
			String hour = time.trim().split(":")[0];
			String min = time.trim().split(":")[1];
			if(hour.length() < 2) {
				hour = "0" + hour;
			}
			if(min.length() < 2) {
				min = "0" + min;
			}
			time = hour+":"+min;
		}
		return time;
	}

}
