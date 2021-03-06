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

import edu.uchicago.duo.domain.DuoPersonObj;
import edu.uchicago.duo.service.DuoObjInterface;
import java.io.UnsupportedEncodingException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.validation.SmartValidator;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("/devicemgmt")
@SessionAttributes("DuoPerson")
public class DuoDeviceMgmtController {

	protected final Log logger = LogFactory.getLog(getClass());
	//
	@Autowired
	private DuoObjInterface duoUsrService;
	//
	@Autowired
	private DuoObjInterface duoPhoneService;
	//
	@Autowired
	private DuoObjInterface duoTabletService;
	//
	@Autowired
	private DuoObjInterface duoTokenService;
	//
	@Autowired
	SmartValidator validator;
	//

	@InitBinder
	public void initBinder(WebDataBinder binder) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

		binder.registerCustomEditor(Date.class, new CustomDateEditor(dateFormat, true));

	}

	//Temporary Test Controller for Device Controller Button Submission
	@RequestMapping(method = RequestMethod.POST)
	public String processPage(@ModelAttribute("DuoPerson") DuoPersonObj duoperson,
			BindingResult result, HttpSession session, SessionStatus status, ModelMap model) throws UnsupportedEncodingException, JSONException, Exception {

		logger.info("Value We Got!: " + duoperson.getPhone_id());
		logger.info("Value We Got!: " + duoperson.getChoosenDevice());
		logger.info("Value We Got!: " + duoperson.getTokenId());
		logger.info("Value We Got!: " + duoperson.getTokenSerial());
		duoperson = new DuoPersonObj();
		initForm(model, duoperson, session, status);
		//return form view
		return "DuoDeviceMgmt";
	}

	@RequestMapping(method = RequestMethod.GET)
	public String initForm(ModelMap model, @ModelAttribute DuoPersonObj duoperson, HttpSession session, SessionStatus status) {
		String userId = null;
		String username = null;

		username = session.getAttribute("username").toString();

		if (session.getAttribute("duoUserId") == null) {
			userId = duoUsrService.getObjByParam(username, null, "userId");
			if (userId == null) {
				logger.info("This User:" + username + " has not yet register with DUO!");
				status.setComplete();
				return "duo";
			}
			logger.info("Assigned UserID via DUO API Query");
		} else {
			userId = session.getAttribute("duoUserId").toString();
			logger.info("Assigned UserID via Session Variable");
		}


		duoperson.setUser_id(userId);
		duoperson.setUsername(username);
		duoperson.setPhones(duoPhoneService.getAllPhones(userId));
		duoperson.setTablets(duoTabletService.getAllTablets(userId));
		duoperson.setTokens(duoTokenService.getAllTokens(userId));

		if (duoperson.getPhones().size() > 0) {
			model.addAttribute("displayPhones", true);
//			logger.info("This user has " + duoperson.getPhones().size() + " phones");
		}

		if (duoperson.getTablets().size() > 0) {
			model.addAttribute("displayTablets", true);
//			logger.info("This user has " + duoperson.getTablets().size() + " tablets");
		}

		if (duoperson.getTokens().size() > 0) {
			model.addAttribute("displayTokens", true);
//			logger.info("This user has " + duoperson.getTokens().size() + " tokens");
		}


		model.addAttribute("DuoPerson", duoperson);

		logger.info("Username(DevMgmt):" + duoperson.getUsername());
		logger.info("UserID(DevMgmt):" + duoperson.getUser_id());

		//return form view
		return "DuoDeviceMgmt";
	}

	@RequestMapping(method = RequestMethod.POST, params = "removedevice")
	public String removeDevice(
			@ModelAttribute("DuoPerson") DuoPersonObj duoperson,
			BindingResult result, HttpSession session, SessionStatus status, ModelMap model) throws UnsupportedEncodingException, Exception {

		logger.info("Delete Device(PhoneId)!: " + duoperson.getPhone_id());
		logger.info("Delete Device(Type)!: " + duoperson.getChoosenDevice());
		logger.info("Delete Device(TokenId)!: " + duoperson.getTokenId());
		logger.info("Delete Device(TokenSerial)!: " + duoperson.getTokenSerial());
		logger.info("Username(DevMgmt):" + duoperson.getUsername());
		logger.info("UserID(DevMgmt):" + duoperson.getUser_id());

		switch (duoperson.getChoosenDevice()) {
			case "Mobile":
			case "Landline":
				duoPhoneService.deleteObj(duoperson.getPhone_id(), null);
				break;
			case "Tablet":
				duoTabletService.deleteObj(duoperson.getPhone_id(), null);	//In the Duo World, Mobile == Tablet except no phone number, so the delete method is identical between the two service
				break;
			case "Token":
				duoTokenService.deleteObj(duoperson.getTokenId(), duoperson.getUser_id());
				break;
		}

		//return form view
		return "redirect:devicemgmt";
	}

	@RequestMapping(method = RequestMethod.POST, params = "sendsmscode")
	public String sendSMSCode(
			@ModelAttribute("DuoPerson") DuoPersonObj duoperson,
			BindingResult result, HttpSession session, SessionStatus status, ModelMap model) throws UnsupportedEncodingException, Exception {

		logger.info("SMS Device(PhoneId)!: " + duoperson.getPhone_id());
		logger.info("SMS Device(Type)!: " + duoperson.getChoosenDevice());
		logger.info("Device(TokenId)!: " + duoperson.getTokenId());
		logger.info("Device(TokenSerial)!: " + duoperson.getTokenSerial());
		logger.info("Username(DevMgmt):" + duoperson.getUsername());
		logger.info("UserID(DevMgmt):" + duoperson.getUser_id());

		duoPhoneService.objActionById(duoperson.getPhone_id(), "passcodeSMS");

		//return form view
		return "redirect:devicemgmt";
	}

	@RequestMapping(method = RequestMethod.POST, params = "deviceactivation")
	public String deviceActivation(
			@RequestParam(value = "action", defaultValue="activate") final String action, @ModelAttribute("DuoPerson") DuoPersonObj duoperson,
			BindingResult result, HttpSession session, SessionStatus status, ModelMap model) throws UnsupportedEncodingException, Exception {

		logger.info("Device Active(PhoneId)!: " + duoperson.getPhone_id());
		logger.info("Device Active(Type)!: " + duoperson.getChoosenDevice());
		logger.info("Username(Device Active):" + duoperson.getUsername());
		logger.info("UserID(Device Active):" + duoperson.getUser_id());

		duoperson.setQRcode(duoPhoneService.objActionById(duoperson.getPhone_id(), "qrCode"));

		switch (action) {
			case "finish":
				String activeStatus;
				activeStatus = duoPhoneService.getObjStatusById(duoperson.getPhone_id());

				if (activeStatus.equals("false")) {
					if (duoperson.getQRcode() == null) {
						String qrCode = duoPhoneService.objActionById(duoperson.getPhone_id(), "qrCode");
						duoperson.setQRcode(qrCode);
					}
					logger.info("DeviceID:" + duoperson.getPhone_id() + "|| Not Activated");
					model.put("deviceNotActive", true);
					return "DuoReActivation";
				} else {
					return "redirect:devicemgmt";
				}
			case "sendsms":
				duoPhoneService.objActionById(duoperson.getPhone_id(), "activationSMS");
				duoperson.setQRcode(null);
				return "DuoReActivation";
			case "qrcode":
				duoperson.setQRcode(duoPhoneService.objActionById(duoperson.getPhone_id(), "qrCode"));
				break;

		}



		//return form view
		return "DuoReActivation";
		//return "redirect:enrollment/activation";
		//return "forward:enrollment/activation";
		//return new RedirectView("/xxxx", true);
	}

	

	@RequestMapping(method = RequestMethod.POST, params = "resynctoken")
	public String resyncToken(
			@RequestParam("resyncAction")
			final String resyncAction,
			@ModelAttribute("DuoPerson") DuoPersonObj duoperson,
			BindingResult result, HttpSession session, SessionStatus status, ModelMap model, final RedirectAttributes redirectAttributes) throws UnsupportedEncodingException, Exception {


		logger.info("Resync Device Type: " + duoperson.getChoosenDevice());
		logger.info("Resync(TokenId)!: " + duoperson.getTokenId());
		logger.info("Resync(TokenSerial)!: " + duoperson.getTokenSerial());
		logger.info("Username(DevMgmt):" + duoperson.getUsername());
		logger.info("UserID(DevMgmt):" + duoperson.getUser_id());

		switch (resyncAction) {
			case "input":
				return "DuoResyncToken";
			case "resync":
				logger.info("Resync(Code 1)!: " + duoperson.getTokenSyncCode1());
				logger.info("Resync(Code 2)!: " + duoperson.getTokenSyncCode2());
				logger.info("Resync(Code 3)!: " + duoperson.getTokenSyncCode3());

				/*
				 * Calling it manually, if using the @validated automatically, see below
				 * @Validated({DuoPersonObj.TokenResyncValidation.class})
				 */
				validator.validate(duoperson, result, DuoPersonObj.TokenResyncValidation.class);
				if (result.hasErrors()) {
					return "DuoResyncToken";
				}
				break;
		}
		duoTokenService.resyncObj(duoperson.getTokenId(), duoperson.getTokenSyncCode1(), duoperson.getTokenSyncCode2(), duoperson.getTokenSyncCode3());

		redirectAttributes.addFlashAttribute("resyncTokenId", duoperson.getTokenId());
		redirectAttributes.addFlashAttribute("resyncsuccess", true);


		//return form view
		return "redirect:devicemgmt";
	}

	@RequestMapping(method = RequestMethod.POST, params = "cancel")
	public String cancel(ModelMap model, @ModelAttribute("DuoPerson") DuoPersonObj duoperson, HttpSession session, SessionStatus status) {

		status.setComplete();
		return "redirect:devicemgmt";
	}

	@RequestMapping(method = RequestMethod.POST, params = "reset")
	public String reset(ModelMap model, @ModelAttribute("DuoPerson") DuoPersonObj duoperson, HttpSession session, SessionStatus status) {

		status.setComplete();
		return "duo";
	}
}
