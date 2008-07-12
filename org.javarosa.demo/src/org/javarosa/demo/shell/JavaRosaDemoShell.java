package org.javarosa.demo.shell;

import java.util.Hashtable;

import javax.microedition.lcdui.Displayable;
import javax.microedition.midlet.MIDlet;

import org.javarosa.communication.http.HttpTransportMethod;
import org.javarosa.communication.http.HttpTransportProperties;
import org.javarosa.core.Context;
import org.javarosa.core.JavaRosaServiceProvider;
import org.javarosa.core.api.Constants;
import org.javarosa.core.api.IActivity;
import org.javarosa.core.api.IShell;
import org.javarosa.core.model.FormData;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.storage.FormDataRMSUtility;
import org.javarosa.core.model.storage.FormDefRMSUtility;
import org.javarosa.core.services.properties.JavaRosaPropertyRules;
import org.javarosa.core.util.WorkflowStack;
import org.javarosa.demo.activity.SplashScreenModule;
import org.javarosa.demo.properties.DemoAppProperties;
import org.javarosa.formmanager.activity.FormListActivity;
import org.javarosa.formmanager.activity.FormTransportActivity;
import org.javarosa.formmanager.activity.ModelListActivity;
import org.javarosa.formmanager.utility.TransportContext;
import org.javarosa.formmanager.view.Commands;
import org.javarosa.services.properties.activity.PropertyScreenActivity;
import org.javarosa.xform.util.XFormUtils;

/**
 * This is the shell for the JavaRosa demo that handles switching all of the views
 * @author Brian DeRenzi
 *
 */
public class JavaRosaDemoShell implements IShell {
	// List of views that are used by this shell
	MIDlet runningAssembly;
	FormListActivity formActivity = null;
	SplashScreenModule splashScreen = null;
	FormTransportActivity formTransport = null;
	ModelListActivity modelActivity = null;
	PropertyScreenActivity propertyActivity = null;
	
	WorkflowStack stack;
	
	Context context;
	
	IActivity currentActivity;

	public JavaRosaDemoShell() {
		stack = new WorkflowStack(); 
		context = new Context();
	}

	public void exitShell() {
		runningAssembly.notifyDestroyed();
	}
	
	public void run() {
		init();
		this.splashScreen = new SplashScreenModule(this, "/splash.gif");
		this.formActivity = new FormListActivity(this,"Forms List");
		this.formTransport = new FormTransportActivity(this);
		this.modelActivity = new ModelListActivity(this);
		
		this.propertyActivity = new PropertyScreenActivity(this);
		
		currentActivity = splashScreen;
		this.splashScreen.start(context);
	//	switchView(ViewTypes.FORM_LIST);
	}
	
	private void init() {
		
		JavaRosaServiceProvider.instance().getPropertyManager().addRules(new JavaRosaPropertyRules());
		JavaRosaServiceProvider.instance().getPropertyManager().addRules(new DemoAppProperties());
		JavaRosaServiceProvider.instance().getPropertyManager().addRules(new HttpTransportProperties());
		
		JavaRosaServiceProvider.instance().getTransportManager().registerTransportMethod(new HttpTransportMethod());
		
		FormDataRMSUtility formData = new FormDataRMSUtility(FormDataRMSUtility.getUtilityName());
		FormDefRMSUtility formDef = new FormDefRMSUtility(FormDefRMSUtility.getUtilityName());

		// For now let's add the dummy form.
		if (formDef.getNumberOfRecords() == 0) {
			formDef.writeToRMS(XFormUtils
					.getFormFromResource("/hmis-a_draft.xhtml"));
			formDef.writeToRMS(XFormUtils
					.getFormFromResource("/hmis-b_draft.xhtml"));
			formDef.writeToRMS(XFormUtils
					.getFormFromResource("/shortform.xhtml"));
		}
		JavaRosaServiceProvider.instance().getStorageManager().getRMSStorageProvider()
				.registerRMSUtility(formData);
		JavaRosaServiceProvider.instance().getStorageManager().getRMSStorageProvider()
				.registerRMSUtility(formDef);
	}
	
	private void workflow(IActivity lastActivity, String returnCode, Hashtable returnVals) {
		//TODO: parse any returnvals into context
		if(stack.size() != 0) {
			IActivity activity = stack.pop();
			this.currentActivity = activity;
			activity.resume(context);
		}
		else {
			// TODO Auto-generated method stub
			if (lastActivity == this.splashScreen) {
				currentActivity = formActivity;
				this.formActivity.start(context);
			}
			if (lastActivity == this.modelActivity) {
				if (returnCode == Constants.ACTIVITY_NEEDS_RESOLUTION) {
					Object returnVal = returnVals.get(ModelListActivity.returnKey);
					if (returnVal == ModelListActivity.CMD_MSGS) {
						// Go to the FormTransport Activity look at messages.
						TransportContext msgContext = new TransportContext(
								context);
						msgContext.setRequestedTask(TransportContext.MESSAGE_VIEW);
						currentActivity = formTransport;
						formTransport.start(msgContext);
					}
				}
				if (returnCode == Constants.ACTIVITY_COMPLETE) {
					// A Model was selected for some purpose
					Object returnVal = returnVals
							.get(ModelListActivity.returnKey);
					if (returnVal == ModelListActivity.CMD_EDIT) {
						// Load the Form Entry Activity, and feed it the form data
						FormDef form = (FormDef) returnVals.get("form");
						FormData data = (FormData) returnVals.get("data");
					}
					if (returnVal == ModelListActivity.CMD_SEND) {
						FormData data = (FormData) returnVals.get("data");
						formTransport.setData(data);
						TransportContext msgContext = new TransportContext(
								context);
						msgContext.setRequestedTask(TransportContext.SEND_DATA);
						currentActivity = formTransport;
						formTransport.start(msgContext);
					}
				}
			}
			if (lastActivity == this.formTransport) {
				if (returnCode == Constants.ACTIVITY_NEEDS_RESOLUTION) {
					String returnVal = (String)returnVals.get(FormTransportActivity.RETURN_KEY);
					if(returnVal == FormTransportActivity.VIEW_MODELS) {
						currentActivity = this.modelActivity;
						this.modelActivity.start(context);
					}
				}
				if (returnCode == Constants.ACTIVITY_COMPLETE) {
					
				}
			}
			if (lastActivity == this.formActivity) {
				if (returnCode == Constants.ACTIVITY_NEEDS_RESOLUTION) {
					String returnVal = (String)returnVals.get("command");
					if(returnVal == Commands.CMD_VIEW_DATA) {
						currentActivity = this.modelActivity;
						this.modelActivity.start(context);
					}
					if(returnVal == Commands.CMD_SETTINGS) {
						currentActivity = this.propertyActivity;
						this.propertyActivity.start(context);
					}
				}
				if (returnCode == Constants.ACTIVITY_COMPLETE) {
					
				}
			}
		}
		if(currentActivity == lastActivity) {
			//We didn't launch anything. Go to default
			currentActivity = formActivity;
			formActivity.start(context);
		}
	}

	/* (non-Javadoc)
	 * @see org.javarosa.shell.IShell#activityCompeleted(org.javarosa.activity.IActivity)
	 */
	public void returnFromActivity(IActivity activity, String returnCode, Hashtable returnVals) {
		activity.halt();
		workflow(activity, returnCode, returnVals);
		if(returnCode == Constants.ACTIVITY_SUSPEND || returnCode == Constants.ACTIVITY_NEEDS_RESOLUTION) {
			stack.push(activity);
		}
	}

	public void setDisplay(IActivity callingActivity, Displayable display) {
		if(callingActivity == currentActivity) {
			JavaRosaServiceProvider.instance().getDisplay().setCurrent(display);
		}
		else {
			//#if debug.output==verbose
			System.out.println("Activity: " + callingActivity + " attempted, but failed, to set the display");
			//#endif
		}
	}
	
	public void setRunningAssembly(MIDlet assembly) {
		this.runningAssembly = assembly;
	}
}
