/**
 * Copyright 2014 University of Chicago
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * Author: Daniel Yu <danielyu@uchicago.edu>
 */
package edu.uchicago.duo.web;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import edu.uchicago.duo.domain.DuoAllIntegrationKeys;
import edu.uchicago.duo.domain.DuoPersonObj;
import edu.uchicago.duo.service.DuoAdminFunc;
import edu.uchicago.duo.service.DuoObjInterface;
import edu.uchicago.duo.validator.DeviceExistDuoValidator;
import java.io.UnsupportedEncodingException;
import java.sql.Date;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.Locale;
import java.util.TreeMap;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;

@Controller
@RequestMapping("/enrollment")
@SessionAttributes("DuoPerson")
public class DuoEnrollController {

	//get log4j handler
	private static final Logger logger = Logger.getLogger(DuoEnrollController.class);
	///
	@Autowired
	private DuoAdminFunc duoadminfunc;
	///
	@Autowired(required = true)
	private DuoAllIntegrationKeys duoallikeys;
	///
	@Autowired
	private DuoObjInterface duoPhoneService;
	///
	@Autowired
	private DuoObjInterface duoUsrService;
	///
	@Autowired
	private DuoObjInterface duoTokenService;
	///
	@Autowired
	private DeviceExistDuoValidator deviceExistDuoValidator;
	///
	private JSONObject jresult = null;
	private JSONArray userresult = null;

	@InitBinder
	public void initBinder(WebDataBinder binder) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

		binder.registerCustomEditor(Date.class, new CustomDateEditor(dateFormat, true));

	}

	@RequestMapping(method = RequestMethod.GET)
	public String initForm(ModelMap model, @ModelAttribute DuoPersonObj duoperson, HttpSession session, SessionStatus status) {

//		DuoPersonObj duopersonobj = new DuoPersonObj();
//		duopersonobj.setPhonenumber("7731234567");
//		model.addAttribute("duopersonobj", duopersonobj);

//		duoperson.setUsername(session.getAttribute("username").toString());
		duoperson.setChoosenDevice("mobile");
		duoperson.setDeviceOS("apple ios");
		duoperson.setCountryDialCode("+1");
		model.addAttribute("DuoPerson", duoperson);

		//return form view
		return "DuoEnrollStep1";
	}

	@RequestMapping(method = RequestMethod.POST, params = "reset")
	public String resetform(ModelMap model, @ModelAttribute("DuoPerson") DuoPersonObj duoperson, HttpSession session, SessionStatus status) {

		status.setComplete();
		return "duo";
	}

	@RequestMapping(method = RequestMethod.POST, params = "enrollsteps")
	public String processPage(@RequestParam("_page") final int nextPage,
			@Valid @ModelAttribute("DuoPerson") DuoPersonObj duoperson,
			BindingResult result, HttpSession session, SessionStatus status, ModelMap model) throws UnsupportedEncodingException, JSONException, Exception {

		//Redirect for Enroll Another Device
		if (nextPage == 0) {
			//model.addAttribute("DuoPerson", new DuoPersonObj());		//Thinking about emptying all info of Bean or just some?
			return "DuoEnrollStep1";
		}

		if (nextPage == 2) {
			//Session Attribute "Username" - UPDATED
			session.setAttribute("username", duoperson.getUsername());

			String userId = duoUsrService.getObjByParam(duoperson.getUsername(), null, "userId");
			duoperson.setUser_id(userId);

			if (userId != null) {
				//Session Attribute "Duo User ID" - UPDATED (Depends)
				session.setAttribute("duoUserId", userId);
				model.put("existingUser", true);
			}
		}

		//Redirect Depends on the type of Device that is being enroll
		if (nextPage == 3) {
			switch (duoperson.getChoosenDevice()) {
				case "tablet":
					duoperson.setDeviceOS(null);
					return "DuoEnrollTablet";
				case "token":
					return "DuoEnrollToken";
			}

		}

		//Validation on Submission of Phone Nmber, to make sure Phone Number has not been registered or belong to someone else
		if (nextPage == 4) {
			logger.info("Combined Phone Number=" + duoperson.getCompletePhonenumber());

			deviceExistDuoValidator.validate(duoperson, result);
			if (result.hasErrors()) {
				return "DuoEnrollStep3";
			}

			if (duoperson.getChoosenDevice().equals("landline")) {
				return "DuoPhoneVerify";
			}
		}

		//Check on Activation Status on DUO Mobile App, don't let user move on until confirm the device has been activated
		if (nextPage == 5) {
			String activeStatus;
			activeStatus = duoPhoneService.getObjStatusById(duoperson.getPhone_id());

			if (activeStatus.equals("false")) {
				if (duoperson.getQRcode() == null) {
					String qrCode = duoPhoneService.objActionById(duoperson.getPhone_id(), "qrCode");
					duoperson.setQRcode(qrCode);
				}
				logger.info("DeviceID:" + duoperson.getPhone_id() + "|| Not Activated");
				model.put("deviceNotActive", true);
				return "DuoActivationQR";
			}

			userresult = duoadminfunc.duosearchuser(this.duoallikeys, "RetrUsers", duoperson.getUsername());
			model.addAttribute("userinfo", userresult.getJSONObject(0));
			return "DuoEnrollSuccess";
		}

		//Traverse Multipage Form, DuoEnrollStep*.jsp
		return "DuoEnrollStep" + nextPage;
	}

	@RequestMapping(value = "/phoneverify.json/{action}", method = RequestMethod.GET)
	@ResponseBody
	public String callToVerify(@ModelAttribute("DuoPerson") DuoPersonObj duoperson, HttpSession session, @PathVariable String action) {
		String callInfo = null;
		String callState = null;
		Map<String, Object> verifyInfo = new HashMap<>();

		switch (action) {
			case "call":
				verifyInfo = duoPhoneService.verifyObj(duoperson.getCompletePhonenumber(), action);
				duoperson.setPhoneVerifyPin(verifyInfo.get("pin").toString());
				duoperson.setPhoneVerifyTxid(verifyInfo.get("txid").toString());
				return "CALLING";
			case "status":
				verifyInfo = duoPhoneService.verifyObj(duoperson.getPhoneVerifyTxid(), action);
				callInfo = verifyInfo.get("info").toString();
				callState = verifyInfo.get("state").toString();
				break;
		}

		return callInfo;
	}

	@RequestMapping(value = "/phoneverify.json/verify/{inputpin}", method = RequestMethod.GET)
	@ResponseBody
	public String verifyPin(@ModelAttribute("DuoPerson") DuoPersonObj duoperson, HttpSession session, @PathVariable String inputpin) {
		String correctPin = null;

		correctPin = duoperson.getPhoneVerifyPin();

		if (inputpin.equals(correctPin)) {
			duoperson.setPhoneOwnerVerified(true);
			return "VERIFIED";
		} else {
			duoperson.setPhoneOwnerVerified(false);
			return "INCORRECT";
		}

	}

	@RequestMapping("/activationstatus.json")
	@ResponseBody
	public String reportStatus(@ModelAttribute("DuoPerson") DuoPersonObj duoperson, HttpSession session) {

		return duoPhoneService.getObjStatusById(duoperson.getPhone_id());

	}

	@RequestMapping(method = RequestMethod.POST, params = "sendsms")
	public String duoSendSMS(
			@ModelAttribute("DuoPerson") DuoPersonObj duoperson,
			BindingResult result, SessionStatus status, ModelMap model) throws UnsupportedEncodingException, Exception {

		duoPhoneService.objActionById(duoperson.getPhone_id(), "activationSMS");

		duoperson.setQRcode(null);

		logger.info("Landed on DuoSend SMS link!");
		return "DuoActivationQR";

	}

	@RequestMapping(method = RequestMethod.POST, params = "genQRcode")
	public String genQRCode(
			@ModelAttribute("DuoPerson") DuoPersonObj duoperson,
			BindingResult result, SessionStatus status, ModelMap model) throws UnsupportedEncodingException, Exception {

		String qrCode = duoPhoneService.objActionById(duoperson.getPhone_id(), "qrCode");
		duoperson.setQRcode(qrCode);

		logger.info("Landed on Duo Gen QR link!");
		return "DuoActivationQR";

	}

	@RequestMapping(method = RequestMethod.POST, params = "enrollUserNPhone")
	public String processEnroll(
			@ModelAttribute("DuoPerson") DuoPersonObj duoperson,
			BindingResult result, HttpSession session, SessionStatus status, ModelMap model) throws UnsupportedEncodingException, Exception {

		String phoneId;
		String tokenId;
		String userId;
		String qrCode;

		//Register First-Time User into DUO Database
		if (duoperson.getUser_id() == null) {
			userId = duoUsrService.createObjByParam(duoperson.getUsername(), duoperson.getFullName(), duoperson.getEmail(), null, null);
			duoperson.setUser_id(userId);

			//Session Attribute "Duo User ID" - ADDED
			session.setAttribute("duoUserId", userId);

			logger.info("Duo User Account created: " + duoperson.getUsername());
			logger.info("Duo userID: " + duoperson.getUser_id());
		}


		if (StringUtils.hasLength(duoperson.getPhone_id())) {
			String activeStatus = duoPhoneService.getObjStatusById(duoperson.getPhone_id());
			if (activeStatus.equals("false")) {
				qrCode = duoPhoneService.objActionById(duoperson.getPhone_id(), "qrCode");
				duoperson.setQRcode(qrCode);
				model.put("deviceNotActive", true);
				return "DuoActivationQR";
			} else {
				return "DuoEnrollSuccess";
			}
		}

		/**
		 * Enrollment Procedure for Type == Mobile | Tablet
		 *
		 * 1st) Create the Phone/Tablet Device first in DUO DB
		 *
		 * 2nd) Link the newly create Phone/Tablet to the user
		 *
		 * 3rd) Generate and Display the Activation QR code for DUO Mobile App
		 * Registration
		 */
		if (duoperson.getChoosenDevice().matches("mobile|tablet")) {
			logger.info(duoperson.getChoosenDevice() + ' ' + duoperson.getDeviceOS() + ' ' + duoperson.getTabletName());
			phoneId = duoPhoneService.createObjByParam(duoperson.getCompletePhonenumber(), duoperson.getChoosenDevice(), duoperson.getDeviceOS(), duoperson.getTabletName(), null);
			duoperson.setPhone_id(phoneId);
//			logger.info("Duo Phone Device created: " + duoperson.getPhonenumber());
//			logger.info("Duo deviceID: " + duoperson.getPhone_id());

			duoPhoneService.associateObjs(duoperson.getUser_id(), phoneId);

			qrCode = duoPhoneService.objActionById(duoperson.getPhone_id(), "qrCode");
			duoperson.setQRcode(qrCode);

			return "DuoActivationQR";
		}

		if (duoperson.getChoosenDevice().matches("landline")) {
			logger.info(duoperson.getChoosenDevice() + ' ' + duoperson.getDeviceOS() + ' ' + duoperson.getTabletName());
			phoneId = duoPhoneService.createObjByParam(duoperson.getCompletePhonenumber(), duoperson.getChoosenDevice(), null, null, duoperson.getLandLineExtension());
			duoperson.setPhone_id(phoneId);

			duoPhoneService.associateObjs(duoperson.getUser_id(), phoneId);
		}


		/**
		 * Enrollment Procedure for Type == Token 1st) Validate against the
		 * database to see whether Token has been register by somebody else ||
		 * Token Existence in DB
		 */
		if (duoperson.getChoosenDevice().matches("token")) {
			logger.info("Duo Token Type:" + duoperson.getTokenType());
			logger.info("Duo Token Serial Number:" + duoperson.getTokenSerial());

			deviceExistDuoValidator.validate(duoperson, result);
			if (result.hasErrors()) {
				return "DuoEnrollToken";
			}
			tokenId = duoTokenService.getObjByParam(duoperson.getTokenSerial(), duoperson.getTokenType(), "tokenId");
			duoperson.setTokenId(tokenId);
			duoTokenService.associateObjs(duoperson.getUser_id(), tokenId);
		}


		userresult = duoadminfunc.duosearchuser(this.duoallikeys, "RetrUsers", duoperson.getUsername());
		for (int i = 0; i < userresult.length(); i++) {
			model.addAttribute("userinfo", userresult.getJSONObject(i));
		}
		return "DuoEnrollSuccess";

	}

	@ModelAttribute("tokenTypeList")
	public Map<String, String> populateTokenTypeList() {

		Map<String, String> tokenType = new LinkedHashMap<>();
		tokenType.put("d1", "Duo-D100 hardware token");
		tokenType.put("yk", "YubiKey AES hardware token");
		tokenType.put("h6", "HOTP-6 hardware token");
		tokenType.put("h8", "HOTP-8 hardware token");
		tokenType.put("t6", "TOTP-6 hardware token");
		tokenType.put("t8", "TOTP-8 hardware token");

		return tokenType;
	}

	@ModelAttribute("tabletOSList")
	public Map<String, String> populateTabletOSList() {

		Map<String, String> tabletOS = new LinkedHashMap<>();
		tabletOS.put("apple ios", "Apple IOS");
		tabletOS.put("google android", "Google Android");
		tabletOS.put("windows phone", "Microsoft Windows for Surface");

		return tabletOS;
	}

	/////////////////////////////////////////////////////////////////////////////////////////
	@ModelAttribute("countryDialList")
	public Map<String, String> populatecountryDialList() {

		List<Country> countries = new ArrayList<>();
		Map<String, String> dialCodes = new LinkedHashMap<>();
		Map<String, String> sortedDialCodes = new LinkedHashMap<>();

		PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

		//
		// Get ISO countries, create Country object and
		// store in the collection.
		//
		String[] isoCountries = Locale.getISOCountries();
		for (String country : isoCountries) {
			Locale locale = new Locale("en", country);
			String code = locale.getCountry();
			String name = locale.getDisplayCountry();

			if (!"".equals(code) && !"".equals(name)) {
				try {
					int dialCode = phoneUtil.parse("1112223333", code).getCountryCode();
					countries.add(new Country(code, name, dialCode));
				} catch (Exception e) {
				}
			}
		}

		Collections.sort(countries, new CountryComparator());

		for (Country country : countries) {
			dialCodes.put("+" + String.valueOf(country.dialCode), country.name);
			//dialCodes.put("+"+String.valueOf(country.code), country.name);
		}

		sortedDialCodes = sortByValue(dialCodes);

		return sortedDialCodes;
	}

	private static class Country {

		private String code;
		private String name;
		private int dialCode;

		Country(String code, String name, int dialCode) {
			this.code = code;
			this.name = name;
			this.dialCode = dialCode;
		}
	}

	static class CountryComparator implements Comparator {

		private Comparator comparator;

		CountryComparator() {
			comparator = Collator.getInstance();
		}

		@SuppressWarnings("unchecked")
		@Override
		public int compare(Object o1, Object o2) {
			Country c1 = (Country) o1;
			Country c2 = (Country) o2;

			return comparator.compare(c1.name, c2.name);
		}
	}

	public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
		List<Map.Entry<K, V>> list =
				new LinkedList<>(map.entrySet());
		Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
			@Override
			public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
				return (o1.getValue()).compareTo(o2.getValue());
			}
		});

		Map<K, V> result = new LinkedHashMap<>();
		for (Map.Entry<K, V> entry : list) {
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}
//	@RequestMapping(method = RequestMethod.POST, params = "AddUser")
//	public String processSubmit(
//			@ModelAttribute("DuoPerson") DuoPersonObj duoperson,
//			BindingResult result, SessionStatus status, ModelMap model) throws UnsupportedEncodingException, Exception {
//
//		if (duoperson.getChoosenDevice() != null && !duoperson.getChoosenDevice().isEmpty() && !duoperson.getChoosenDevice().matches("landline|token")) {
//			jresult = duoadminfunc.DuoEnrollUser(this.duoallikeys, "EnrollUser", duoperson.getUsername());
//			model.addAttribute("barcode", jresult.getString("activation_barcode"));
//		}
//
//		userresult = duoadminfunc.duosearchuser(this.duoallikeys, "RetrUsers", duoperson.getUsername());
//
//		for (int i = 0; i < userresult.length(); i++) {
//			model.addAttribute("userinfo", userresult.getJSONObject(i));
//		}
//		return "DuoEnrollSuccess";
//	}
}