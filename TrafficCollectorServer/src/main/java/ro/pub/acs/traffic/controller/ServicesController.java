package ro.pub.acs.traffic.controller;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.social.facebook.api.Facebook;
import org.springframework.social.facebook.api.impl.FacebookTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import ro.pub.acs.traffic.dao.LocationDAO;
import ro.pub.acs.traffic.dao.UserContactDAO;
import ro.pub.acs.traffic.dao.UserDAO;
import ro.pub.acs.traffic.model.Location;
import ro.pub.acs.traffic.model.Place;
import ro.pub.acs.traffic.model.User;
import ro.pub.acs.traffic.model.UserContact;

@RestController
@RequestMapping("/services")
public class ServicesController {

	@Autowired
	private UserDAO userDAO;

	@Autowired
	private LocationDAO locationDAO;

	@Autowired
	private UserContactDAO userContactDAO;

	@RequestMapping(value = "user/getUser/{userId}", method = RequestMethod.GET)
	public @ResponseBody User getUser(@PathVariable int userId) {
		User user = userDAO.get(userId);

		return user;
	}

	@RequestMapping(value = "/signup/userpass", method = RequestMethod.POST)
	public @ResponseBody User userPassCreateAccount(@RequestBody User user) {
		User userExists = userDAO.get(user.getUsername());
		if (userExists != null) {
			return null;
		}

		String uuid = UUID.randomUUID().toString();
		DateTime dateTime = new DateTime();
		DateTime threeMontsLater = dateTime.plusMonths(3);
		long seconds = Seconds.secondsBetween(dateTime, threeMontsLater)
				.getSeconds();
		user.setAuth_token(uuid);
		user.setUuid(uuid);
		user.setAuthExpiresIn((int)seconds);
		user.setFacebookExpiresIn(0);

		/* Save and return the new user */
		int userId = userDAO.add(user);
		User newUser = userDAO.get(userId);

		return newUser;
	}

	@RequestMapping(value = "/authenticate/facebook", method = RequestMethod.POST)
	public @ResponseBody User loginWithFacebook(@RequestBody User user) {
		/* Get Facebook profile */
		Facebook facebook = new FacebookTemplate(user.getFacebook_token());
		org.springframework.social.facebook.api.User profile = facebook
				.userOperations().getUserProfile();
		if (profile == null || profile.getEmail() == null
				|| !user.getUsername().equals(profile.getEmail())) {
			return null;
		}

		String uuid = UUID.randomUUID().toString();
		DateTime dateTime = new DateTime(0);
		DateTime threeMonthsLater = new DateTime().plusMonths(3);
		long seconds = Seconds.secondsBetween(dateTime, threeMonthsLater)
				.getSeconds();
		user.setAuth_token(uuid);
		user.setAuthExpiresIn((int)seconds);
		user.setUuid(uuid);

		/* Save and return the new user */
		int userId;
		
		/* Update user into database */
		User oldUser = userDAO.get(user.getUsername());
		if (oldUser != null) {
			user.setId(oldUser.getId());
			userId = userDAO.update(user);
		} else {
			userId = userDAO.add(user);
		}
		
		User newUser = userDAO.get(userId);

		/* Get friend from Facebook and insert into database */
		List<String> friendIds = facebook.friendOperations().getFriendIds();

		for (String friendId : friendIds) {
			User userFried = userDAO.get(friendId);
			UserContact userContact1 = new UserContact();
			UserContact userContact2 = new UserContact();
			userContact1.setIdUser(newUser);
			userContact1.setIdFriendUser(userFried);
			userContact2.setId(userFried.getId());
			userContact2.setIdFriendUser(newUser);

			userContactDAO.addFriend(userContact1);
			userContactDAO.addFriend(userContact2);
		}

		return newUser;
	}

	@RequestMapping(value = "/authenticate/userpass", method = RequestMethod.POST)
	public @ResponseBody User loginWithUserAndPass(@RequestBody User user) {
		User oldUser = userDAO.get(user.getUsername(), user.getPassword());
		if (oldUser == null) {
			return null;
		}

		String uuid = UUID.randomUUID().toString();
		DateTime dateTime = new DateTime(0);
		DateTime threeMonthsLater = new DateTime().plusMonths(3);
		long seconds = Seconds.secondsBetween(dateTime, threeMonthsLater)
				.getSeconds();
		oldUser.setAuth_token(uuid);
		oldUser.setAuthExpiresIn((int)seconds);

		/* Save and return the new user */
		userDAO.update(oldUser);

		return oldUser;
	}

	@RequestMapping(value = "/social/getFriendsNames", method = RequestMethod.GET)
	public @ResponseBody List<User> getFriendsNames(
			@RequestHeader("X-Auth-Token") String authToken) {
		User user = userDAO.get(authToken, 0);
		List<User> friends = new ArrayList<User>();

		if (user != null) {
			friends = userContactDAO.getFriends(user);
		}

		return friends;
	}

	@RequestMapping(value = "/social/getFriendsLocations", method = RequestMethod.GET)
	public @ResponseBody List<Location> getFriendsLocations(
			@RequestHeader("X-Auth-Token") String authToken) {
		User user = userDAO.get(authToken, 0);
		List<User> friends = new ArrayList<User>();
		List<Location> locations = new ArrayList<Location>();

		if (user != null) {
			friends = userContactDAO.getFriends(user);
			for (User friend : friends) {
				Location location = locationDAO
						.getLocation(friend);
				if (location != null) {
					location.setTimestamp(null);
					locations.add(location);
				}
			}
		}

		return locations;
	}

	@RequestMapping(value = "/social/getUsersWithPhone", method = RequestMethod.GET)
	public @ResponseBody List<User> getUsersWithPhone(
			@RequestHeader("X-Auth-Token") String authToken) {
		User user = userDAO.get(authToken, 0);
		List<User> users = new ArrayList<User>();

		if (user != null) {
			users = userDAO.getUsersWithPhone();
		}

		return users;
	}

	@RequestMapping(value = "/social/addFriends", method = RequestMethod.PUT)
	public @ResponseBody boolean addFriends(
			@RequestHeader("X-Auth-Token") String authToken,
			@RequestBody List<User> friends) {
		User user = userDAO.get(authToken, 0);
		List<String> oldFriends = new ArrayList<String>();
		
		if (user == null) {
			return false;
		} else {
			for (User friend : friends) {
				oldFriends = userContactDAO.getFriendsEmails(user);
				if (user.getId() != friend.getId() && !oldFriends.contains(friend.getUsername())) {
					UserContact userContact1 = new UserContact();
					UserContact userContact2 = new UserContact();
					userContact1.setIdUser(user);
					userContact1.setIdFriendUser(friend);
					userContact2.setIdUser(friend);
					userContact2.setIdFriendUser(user);

					userContactDAO.addFriend(userContact1);
					userContactDAO.addFriend(userContact2);
				}
			}
		}

		return true;
	}

	@RequestMapping(value = "/social/getNearbyLocations", method = RequestMethod.PUT)
	public @ResponseBody List<Place> getNearbyLocations(
			@RequestHeader("X-Auth-Token") String authToken,
			@RequestBody List<String> types) {

		return new ArrayList<Place>();
	}

	@SuppressWarnings({ "deprecation", "resource" })
	@RequestMapping(value = "/social/getRoute", method = RequestMethod.PUT)
	public @ResponseBody List<Location> getRoute(
			@RequestHeader("X-Auth-Token") String authToken,
			@RequestBody ArrayList<Location> locations) {
		ArrayList<Location> routePoints = new ArrayList<Location>();
		
		try {
			HttpClient httpClient = new DefaultHttpClient();
			StringBuilder url = new StringBuilder();
			url.append("http://192.168.122.136:5000/viaroute?loc=");
			url.append(locations.get(0).getLatitude()+","+locations.get(0).getLongitude()+"&loc=");
			url.append(locations.get(1).getLatitude()+","+locations.get(1).getLongitude()+"&instructions=true");
			HttpGet httpGet = new HttpGet(url.toString());
		    HttpResponse httpGetResponse = httpClient.execute(httpGet);
		    HttpEntity httpGetEntity = httpGetResponse.getEntity();
		    
		    if (httpGetEntity != null) {  
		    	String response = EntityUtils.toString(httpGetEntity);
		    	JSONObject route = new JSONObject(response);
		    	JSONArray viaPoints = route.getJSONArray("via_points");
		    	
		    	if(viaPoints != null){
		    		for(int i = 0; i < viaPoints.length(); i++){
			    		JSONArray point = viaPoints.getJSONArray(i);
			    		Location location = new Location();
			    		location.setIdUser(0);
			    		location.setLatitude(point.getInt(0));
			    		location.setLongitude(point.getInt(1));
			    		location.setSpeed(0);
			    		
			    		routePoints.add(location);
			    	}
		    	}
		    	
		    }            
		} catch (Exception exception) {
		    exception.printStackTrace();
		}
		
		return routePoints;
	}

	@RequestMapping(value = "/location/update", method = RequestMethod.PUT)
	public @ResponseBody boolean updateLocation(@RequestBody Location location,
			@RequestHeader("X-Auth-Token") String authToken) {
		User user = userDAO.get(authToken, location.getIdUser());

		if (user != null) {
			Location locationUser = locationDAO.getLocation(user);
			if (locationUser != null) {
				locationUser.setLatitude(location.getLatitude());
				locationUser.setLongitude(location.getLongitude());
				locationUser.setSpeed(location.getSpeed());
				locationUser.setTimestamp(new Date());
				locationDAO.updateLocation(locationUser);
			} else {
				location.setIdUser(user.getId());
				location.setTimestamp(new Date());
				locationDAO.addLocation(location);
			}

			return true;
		}

		return false;
	}

}