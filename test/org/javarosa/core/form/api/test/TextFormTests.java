package org.javarosa.core.form.api.test;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.javarosa.core.model.Constants;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.QuestionDef;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.test.DummyFormEntryPrompt;
import org.javarosa.core.services.PrototypeManager;
import org.javarosa.core.services.locale.Localizer;
import org.javarosa.core.services.locale.TableLocaleSource;
import org.javarosa.core.test.FormParseInit;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryPrompt;

public class TextFormTests extends TestCase {

	QuestionDef q = null;
	FormEntryPrompt fep = null;
	FormParseInit fpi = null;

	static PrototypeFactory pf;

	static {
		PrototypeManager.registerPrototype("org.javarosa.model.xform.XPathReference");
		pf = ExtUtil.defaultPrototypes();
	}


	public TextFormTests(String name) {
		super(name);
		System.out.println("Running " + this.getClass().getName() + " test: " + name + "...");
	}

	public void setUp(){
		fpi = new FormParseInit();
		q = fpi.getFirstQuestionDef();
		fep = new FormEntryPrompt(fpi.getFormDef(), fpi.getFormEntryModel().getFormIndex());
	}



	public static Test suite() {
		TestSuite suite = new TestSuite();
		System.out.println("Running TextFormTests...");
		for (int i = 1; i <= NUM_TESTS; i++) {
			final int testID = i;
			suite.addTest(new TextFormTests(doTest(testID)));
		}

		return suite;
	}



	public static final int NUM_TESTS = 8;

	public static String doTest (int i) {
		switch (i) {
		case 1: return "testConstructors";
		case 2: return "testPromptsNoLocalizer";
		case 3: return "testPromptIDsNoLocalizer";
		case 4: return "testPromptsWithLocalizer";
		case 5: return "testSelectChoicesNoLocalizer";
		case 6: return "testSelectChoiceIDsNoLocalizer";
		case 7: return "testNonLocalizedText";
		case 8: return "testTextForms";
		}
		throw new IllegalStateException("unexpected index");
	}

	public void testConstructors () {
		QuestionDef q;

		q = new QuestionDef();
		if (q.getID() != -1) {
			fail("QuestionDef not initialized properly (default constructor)");
		}

		q = new QuestionDef(17,Constants.CONTROL_RANGE);
		if (q.getID() != 17) {
			fail("QuestionDef not initialized properly");
		}
		if(q.getControlType() != Constants.CONTROL_RANGE){
			fail("QuestionDef not initialized properly");
		}
	}

	/**
	 * Test that the long and short text forms work as expected
	 * (fallback to default for example).
	 * Test being able to retrieve other exotic forms
	 */
	public void testTextForms(){
		FormEntryController fec = fpi.getFormEntryController();
		fec.jumpToIndex(FormIndex.createBeginningOfFormIndex());
		boolean foundFlag = false;
		Localizer l = fpi.getFormDef().getLocalizer();

		l.setDefaultLocale(l.getAvailableLocales()[0]);
		l.setLocale(l.getAvailableLocales()[0]);
		int state = -99;
		while(state != FormEntryController.EVENT_QUESTION){
			state = fec.stepToNextEvent();
		}
		fep = fec.getModel().getQuestionPrompt();

		if(!fep.getLongText().equals("Patient ID")) {
			fail("getLongText() not returning correct value");
		}
		if(!fep.getShortText().equals("ID")) {
			fail("getShortText() not returning correct value");
		}
		if(!fep.getAudioText().equals("jr://audio/hah.mp3")) {
			fail("getAudioText() not returning correct value");
		}

		state = -99;
		while(state != FormEntryController.EVENT_QUESTION){
			state = fec.stepToNextEvent();
		}

		fep = fec.getModel().getQuestionPrompt();
		if(!fep.getShortText().equals("Name")) {
			fail("getShortText() not returning correct value");
		}
		if(!fep.getLongText().equals("Full Name")) {
			fail("getLongText() not falling back to default text form correctly");
		}
		String v = fep.getSpecialFormQuestionText("long");
		if(fep.getSpecialFormQuestionText("long") != null) {
			fail("getSpecialFormQuestionText() returning incorrect value");
		}

	}

	public void testNonLocalizedText(){
		FormEntryController fec = fpi.getFormEntryController();
		fec.jumpToIndex(FormIndex.createBeginningOfFormIndex());
		boolean testFlag = false;
		Localizer l = fpi.getFormDef().getLocalizer();

		l.setDefaultLocale(l.getAvailableLocales()[0]);
		l.setLocale(l.getAvailableLocales()[0]);

		do{
			if(fpi.getCurrentQuestion()==null) {
				continue;
			}
			QuestionDef q = fpi.getCurrentQuestion();
			fep = fpi.getFormEntryModel().getQuestionPrompt();
			String t = fep.getQuestionText();
			if(t==null) {
				continue;
			}
			if(t.equals("Non-Localized label inner text!")) {
				testFlag = true;
			}


		}while(fec.stepToNextEvent()!=FormEntryController.EVENT_END_OF_FORM);

		if(!testFlag) {
			fail("Failed to fallback to labelInnerText in testNonLocalizedText()");
		}
	}

	public void testSelectChoiceIDsNoLocalizer () {

		QuestionDef q = fpi.getFirstQuestionDef();

		q.addSelectChoice(new SelectChoice("choice1 id", "val1"));
		q.addSelectChoice(new SelectChoice("loc: choice2", "val2", false));

		if (!fep.getSelectChoices().toString().equals("[{choice1 id} => val1, loc: choice2 => val2]")) {
			fail("Could not add individual select choice ID"+fep.getSelectChoices().toString());
		}


		//clean up
		q.removeSelectChoice(q.getChoices().get(0));
		q.removeSelectChoice(q.getChoices().get(0));
	}

	public void testSelectChoicesNoLocalizer () {
		QuestionDef q = fpi.getFirstQuestionDef();
		if (q.getNumChoices() != 0) {
			fail("Select choices not empty on init");
		}

//		fpi.getNextQuestion();

		String onetext = "choice";
		String twotext = "stacey's";
		SelectChoice one = new SelectChoice(null,onetext, "val", false);
		q.addSelectChoice(one);
		SelectChoice two = new SelectChoice(null,twotext, "mom", false);
		q.addSelectChoice(two);


		if(!fep.getSelectChoices().toString().equals("[choice => val, stacey's => mom]")) {
			fail("Could not add individual select choice"+fep.getSelectChoices().toString());
		}

		Object a = onetext;
		Object b = fep.getSelectChoiceText(one);
		assertEquals("Invalid select choice text returned",a, b);

		assertEquals("Invalid select choice text returned", twotext, fep.getSelectChoiceText(two));

		assertNull("Form Entry Caption incorrectly contains Image Text", fep.getSpecialFormSelectChoiceText(one, FormEntryCaption.TEXT_FORM_IMAGE));

		assertNull("Form Entry Caption incorrectly contains Audio Text", fep.getSpecialFormSelectChoiceText(one, FormEntryCaption.TEXT_FORM_AUDIO));

		q.removeSelectChoice(q.getChoice(0));
		q.removeSelectChoice(q.getChoice(0));
	}

	public void testPromptsWithLocalizer () {
		Localizer l = new Localizer();

		TableLocaleSource table = new TableLocaleSource();
		l.addAvailableLocale("locale");
		l.setDefaultLocale("locale");
		table.setLocaleMapping("prompt;long", "loc: long text");
		table.setLocaleMapping("prompt;short", "loc: short text");
		table.setLocaleMapping("help", "loc: help text");
		l.registerLocaleResource("locale", table);

		l.setLocale("locale");


		QuestionDef q = new QuestionDef();

		q.setHelpTextID("help");
		FormEntryPrompt fep = new DummyFormEntryPrompt(l,"prompt",q);

		if (!"loc: long text".equals(fep.getLongText())) {
			fail("Long text did not localize properly");
		}
		if (!"loc: short text".equals(fep.getShortText())){
			fail("Short text did not localize properly");
		}

	}

	public void testPromptIDsNoLocalizer () {
		QuestionDef q = new QuestionDef();

		q.setTextID("long text id");
		if (!"long text id".equals(q.getTextID())) {
			fail("Long text ID getter/setter broken");
		}

		q.setHelpTextID("help text id");
		if (!"help text id".equals(q.getHelpTextID()) || q.getHelpText() != null) {
			fail("Help text ID getter/setter broken");
		}
	}

	public void testPromptsNoLocalizer () {
		QuestionDef q = new QuestionDef();

		q.setLabelInnerText("labelInnerText");
		if (!"labelInnerText".equals(q.getLabelInnerText())) {
			fail("LabelInnerText getter/setter broken");
		}

		q.setHelpText("help text");
		if (!"help text".equals(q.getHelpText())) {
			fail("Help text getter/setter broken");
		}
	}


}