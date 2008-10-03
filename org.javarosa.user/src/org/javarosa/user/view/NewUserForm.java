package org.javarosa.user.view;

import java.io.IOException;

import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextField;

import org.javarosa.core.util.UnavailableExternalizerException;
import org.javarosa.user.model.Constants;
import org.javarosa.user.model.User;
import org.javarosa.user.storage.UserRMSUtility;



/**
 * @author Julian Hulme
 *
 */
public class NewUserForm extends Form  {

	private TextField userName;
	private TextField password;
	private TextField confirmPassword;
	private UserRMSUtility userRMS;
	private ChoiceGroup choice = new ChoiceGroup("",Choice.MULTIPLE);

	public NewUserForm(String title)
	{
		super(title);
		userName = new TextField("Name (ie: loginID):", "", 10, TextField.ANY);
	    password = new TextField("User Password:", "", 10, TextField.PASSWORD);
	    confirmPassword = new TextField("Confirm Password:", "", 10, TextField.PASSWORD);
	    choice.append("Give this user admin rights?", null);

	    this.append(userName);
	    this.append(password);
	    this.append(confirmPassword);
	    this.append(choice);

	    userRMS = new UserRMSUtility("LoginMem");
	}

	public String readyToSave()
	{
		boolean nameAlreadyTaken = checkNameExistsAlready();
		if (nameAlreadyTaken == true)
		{
			
			return "Username ("+userName.getString()+") already taken. Please choose another username.";
		}
		else if ((userName.getString().equalsIgnoreCase("")) || (password.getString().equals("")))
		{
			
			return "Please fill in both username and password.";
		}
		else if (!(password.getString().equals(confirmPassword.getString())))
		{
			
			return "Please re-enter your password, the password and password confirmation box did not match.";
		}
		else
		{

			if (choice.isSelected(0) == false)
				userRMS.writeToRMS(new User (userName.getString() ,password.getString()));
			else 
				userRMS.writeToRMS(new User (userName.getString() ,password.getString(), Constants.ADMINUSER));
			
			return "";
		}
	}

	private boolean checkNameExistsAlready()
	{
		///find user in RMS:
		   User discoveredUser = new User();
		   String usernameStr = userName.getString();
		   int index = 1;

		   while (index <= userRMS.getNumberOfRecords() )
		   {
			   try
			   {
				   try {
					userRMS.retrieveFromRMS(index, discoveredUser);
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InstantiationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (UnavailableExternalizerException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			   }
			   catch (IOException ioe) {
				   System.out.println(ioe);
			   }
			   if (discoveredUser.getUsername().equalsIgnoreCase(usernameStr))
				   break;

			   index++;
		   }

		   if (discoveredUser.getUsername().equalsIgnoreCase(usernameStr))
		   	   return true;
		   else return false;


	}



}
