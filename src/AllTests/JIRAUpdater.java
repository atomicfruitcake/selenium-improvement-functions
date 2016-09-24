package AllTests;

import static AllTests.Properties.JIRA;
import static AllTests.Properties.JIRABOTPASSWORD;
import static AllTests.Properties.JIRABOTUSERNAME;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;

/**
 * @author atomicfruitcake
 *
 */
public class JIRAUpdater {

	private static final Logger LOGGER = Logger.getLogger(JIRAUpdater.class
			.getName());

	// Creates an HTTP URL Connection to a JIRA Instance
	public static HttpURLConnection connectJiraAPI(String jiraURL)
			throws IOException {
		URL urlObject = new URL(jiraURL);
		HttpURLConnection httpURLConnection = (HttpURLConnection) urlObject
				.openConnection();
		httpURLConnection
				.setRequestProperty("Content-Type", "application/json");
		httpURLConnection.setDoOutput(true);
		httpURLConnection.setRequestMethod("POST");
		httpURLConnection.setReadTimeout(30 * 1000); // Timeout after 30s
		String usernamePassword = JIRABOTUSERNAME + ":" + JIRABOTPASSWORD;
		String basicAuth = "Basic "
				+ javax.xml.bind.DatatypeConverter
						.printBase64Binary(usernamePassword.getBytes("UTF-8"));
		httpURLConnection.setRequestProperty("Authorization", basicAuth);
		httpURLConnection.setRequestProperty("Accept", "*/*");
		LOGGER.info("Created URL Connection to: " + httpURLConnection);
		return httpURLConnection;

	}

	// Send data through an HTTP Connection
	public static void sendDataHTTPConnection(String data,
			HttpURLConnection httpURLConnection) throws IOException {
		try (OutputStreamWriter out = new OutputStreamWriter(
				httpURLConnection.getOutputStream())) {
			out.write(data);
			out.close();
			LOGGER.info("Response code from JIRA: "
			+ httpURLConnection.getResponseCode());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	// Transitions a Jira ticket
	public static void transitionJiraTicket(String jiraID, String moveToID)
			throws IOException {
		String jiraURL = JIRA + jiraID
				+ "/transitions?expand=transitions.fields";

		HttpURLConnection httpURLConnection = connectJiraAPI(jiraURL);
		httpURLConnection.connect();

		String transitionJSON = "{\"transition\":{\"id\":\"" + moveToID
				+ "\"}}";

		sendDataHTTPConnection(transitionJSON, httpURLConnection);
	}

	// Adds a comment to a JIRA ticket
	public static void commentJiraTicket(String jiraid, String comment)
			throws IOException {

		String jiraURL = JIRA + jiraid + "/comment";
		HttpURLConnection httpURLConnection = connectJiraAPI(jiraURL);
		httpURLConnection.connect();
		String data = "{\"body\":\"" + comment + "\"}";
		LOGGER.info("Updating Jira ticket: " 
				+ jiraid 
				+ " with comment "
				+ comment);
		
		sendDataHTTPConnection(data, httpURLConnection);
	}

	/*
	 * moveToID notes 11014 = Ready for Execution 41 = In Progress 51 = Passed
	 * 61 = Failed 81 = Retest
	 */

	public static void resetTicket(String jiraID) throws IOException,
			InterruptedException {
		try {
			transitionJiraTicket(jiraID, "81");
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			transitionJiraTicket(jiraID, "11041");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void PassTicket(String jiraID) throws IOException,
			InterruptedException, TimeoutException {
		resetTicket(jiraID);
		transitionJiraTicket(jiraID, "41");
		transitionJiraTicket(jiraID, "51");
		commentJiraTicket(jiraID, "Tested by Automation and passed on "
				+ CommonFunctions.getEnvironment());
	}

	public static void FailTicket(String jiraID) throws IOException,
			InterruptedException, TimeoutException {
		resetTicket(jiraID);
		transitionJiraTicket(jiraID, "41");
		transitionJiraTicket(jiraID, "61");
		commentJiraTicket(jiraID, "Tested by Automation and failed on "
				+ CommonFunctions.getEnvironment());
	}

	public static void updateJiraTicket(ITestResult result, Method method,
			WebDriver driver) throws Exception, IOException,
			InterruptedException {
		String testName = method.getName();
		@SuppressWarnings(value = {})
		String jiraTicketFull = "FN-" + testName.substring(2);
		String updateJiraResult = CommonFunctions.getUpdateJIRA();
		switch (updateJiraResult) {
		case "Yes":
			if (result.getStatus() == ITestResult.FAILURE) {
				JIRAUpdater.FailTicket(jiraTicketFull);
				LOGGER.info(jiraTicketFull + " : FAILED");
				CommonFunctions.screenshot(jiraTicketFull + " Fail", driver);

			} else if (result.getStatus() == ITestResult.SUCCESS) {
				JIRAUpdater.PassTicket(jiraTicketFull);
				LOGGER.info(jiraTicketFull + " : PASSED");
			}
		case "No":
			if (result.getStatus() == ITestResult.FAILURE) {
				LOGGER.info(jiraTicketFull + " : FAILED");
				CommonFunctions.screenshot(jiraTicketFull + " Fail", driver);

			} else if (result.getStatus() == ITestResult.SUCCESS) {
				LOGGER.info(jiraTicketFull + " : PASSED");
			}
		}
	}
}
