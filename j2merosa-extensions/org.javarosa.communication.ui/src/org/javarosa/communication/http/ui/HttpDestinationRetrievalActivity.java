/*
 * Copyright (C) 2009 JavaRosa
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
 */

package org.javarosa.communication.http.ui;

import java.util.Hashtable;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;

import org.javarosa.communication.http.HttpTransportDestination;
import org.javarosa.communication.http.HttpTransportMethod;
import org.javarosa.core.Context;
import org.javarosa.core.api.Constants;
import org.javarosa.core.services.DataCaptureServiceRegistry;
import org.javarosa.core.services.transport.TransportMethod;

public class HttpDestinationRetrievalActivity implements IActivity,
		CommandListener {
	
	IShell shell;
	GetURLForm form;

	public void contextChanged(Context globalContext) {
		// TODO Auto-generated method stub

	}

	public void destroy() {
		// TODO Auto-generated method stub

	}

	public Context getActivityContext() {
		// TODO Auto-generated method stub
		return null;
	}

	public void halt() {
		// TODO Auto-generated method stub

	}

	public void resume(Context globalContext) {
		shell.setDisplay(this, form);
	}

	public void start(Context context) {
		form = new GetURLForm(((HttpTransportMethod) DataCaptureServiceRegistry.instance().getTransportManager().getTransportMethod(
				new HttpTransportMethod().getId())).getDefaultDestination());
		form.setCommandListener(this);
		shell.setDisplay(this, form);
	}

	public void commandAction(Command arg0, Displayable arg1) {
		Hashtable returnArgs = new Hashtable();
		if(arg0 == GetURLForm.CMD_OK) {
			returnArgs.put(TransportMethod.DESTINATION_KEY, new HttpTransportDestination(form.getDestination()));
		} else {
			returnArgs.put(TransportMethod.DESTINATION_KEY, null);
		}
		form = null;
		shell.returnFromActivity(this, Constants.ACTIVITY_COMPLETE, returnArgs);
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.javarosa.core.api.IActivity#setShell(org.javarosa.core.api.IShell)
	 */
	public void setShell(IShell shell) {
		this.shell = shell;
	}
	/* (non-Javadoc)
	 * @see org.javarosa.core.api.IActivity#annotateCommand(org.javarosa.core.api.ICommand)
	 */
	public void annotateCommand(ICommand command) {
		throw new RuntimeException("The Activity Class " + this.getClass().getName() + " Does Not Yet Implement the annotateCommand Interface Method. Please Implement It.");
	}
}
