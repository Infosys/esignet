package io.mosip.testrig.apirig.esignet.testscripts;

import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.testng.ITest;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.internal.BaseTestMethod;
import org.testng.internal.TestResult;

import io.mosip.testrig.apirig.dto.OutputValidationDto;
import io.mosip.testrig.apirig.dto.TestCaseDTO;
import io.mosip.testrig.apirig.esignet.utils.EsignetConfigManager;
import io.mosip.testrig.apirig.esignet.utils.EsignetUtil;
import io.mosip.testrig.apirig.testrunner.HealthChecker;
import io.mosip.testrig.apirig.testrunner.JsonPrecondtion;
import io.mosip.testrig.apirig.utils.AdminTestException;
import io.mosip.testrig.apirig.utils.AdminTestUtil;
import io.mosip.testrig.apirig.utils.GlobalConstants;
import io.mosip.testrig.apirig.utils.GlobalMethods;
import io.mosip.testrig.apirig.utils.KernelAuthentication;
import io.mosip.testrig.apirig.utils.OutputValidationUtil;
import io.mosip.testrig.apirig.utils.ReportUtil;
import io.mosip.testrig.apirig.utils.RestClient;
import io.restassured.response.Response;

public class AddIdentity extends EsignetUtil implements ITest {
	private static final Logger logger = Logger.getLogger(AddIdentity.class);
	protected String testCaseName = "";
	public Response response = null;
	private boolean isWaitRequired = false;
	private boolean isvalueReplaced = false;

	/**
	 * get current testcaseName
	 */
	@Override
	public String getTestName() {
		return testCaseName;

	}

	@BeforeClass
	public static void setLogLevel() {
		if (EsignetConfigManager.IsDebugEnabled())
			logger.setLevel(Level.ALL);
		else
			logger.setLevel(Level.ERROR);
	}

	/**
	 * Data provider class provides test case list
	 * 
	 * @return object of data provider
	 */
	@DataProvider(name = "testcaselist")
	public Object[] getTestCaseList(ITestContext context) {
		String ymlFile = context.getCurrentXmlTest().getLocalParameters().get("ymlFile");
		logger.info("Started executing yml: " + ymlFile);
		return getYmlTestData(ymlFile);
	}

	/**
	 * Test method for OTP Generation execution
	 * 
	 * @param objTestParameters
	 * @param testScenario
	 * @param testcaseName
	 * @throws Exception 
	 */
	@Test(dataProvider = "testcaselist")
	public void test(TestCaseDTO testCaseDTO) throws Exception {
		testCaseName = testCaseDTO.getTestCaseName();
		testCaseName = EsignetUtil.isTestCaseValidForExecution(testCaseDTO);
		if (HealthChecker.signalTerminateExecution) {
			throw new SkipException(
					GlobalConstants.TARGET_ENV_HEALTH_CHECK_FAILED + HealthChecker.healthCheckFailureMapS);
		}
		
		if (testCaseDTO.getEndPoint().contains("mock-identity-system")) {
			String individualIDStr = String.valueOf(Calendar.getInstance().getTimeInMillis());
			String phoneStr = "+91" + generateRandomNumberString(10);
			String emailStr = testCaseName + "@mosip.net";
			String passWordStr = properties.getProperty("passwordForAddIdentity");
			String url = ApplnURI.replace("-internal", "") + testCaseDTO.getEndPoint();

			String inputJson = getJsonFromTemplate(testCaseDTO.getInput(), testCaseDTO.getInputTemplate());

			inputJson = EsignetUtil.inputstringKeyWordHandeler(inputJson, testCaseName);

			if (inputJson.contains("$RANDOMINDIVIDUALIDFORMOCKIDENTITY$")) {
				inputJson = replaceKeywordWithValue(inputJson, "$RANDOMINDIVIDUALIDFORMOCKIDENTITY$", individualIDStr);
				writeAutoGeneratedId(testCaseName, "UIN", individualIDStr);
			}
			
			if (inputJson.contains("$EMAILIDFORMOCKIDENTITY$")) {
				inputJson = replaceKeywordWithValue(inputJson, "$EMAILIDFORMOCKIDENTITY$", emailStr);
				writeAutoGeneratedId(testCaseName, "EMAIL", emailStr);
			}
			if (inputJson.contains("$PASSWORDFORMOCKIDENTITY$")) {
				inputJson = replaceKeywordWithValue(inputJson, "$PASSWORDFORMOCKIDENTITY$", passWordStr);
				writeAutoGeneratedId(testCaseName, "PASSWORD", passWordStr);
			}
			if (inputJson.contains("$PHONEFORMOCKIDENTITY$")) {
				inputJson = replaceKeywordWithValue(inputJson, "$PHONEFORMOCKIDENTITY$", phoneStr);
				writeAutoGeneratedId(testCaseName, "PHONE", phoneStr);
			}
			
			GlobalMethods.reportRequest(null, inputJson, url);

			response = RestClient.post(url, inputJson);
			
			GlobalMethods.reportResponse(response.getHeaders().asList().toString(), url, response);
			if (isvalueReplaced == false) {
				writeAutoGeneratedId("ESignet_GenerateApiKey_Valid_Smoke_sid", "apiKey",
						generateRandomNumberString(20));
				writeAutoGeneratedId("ESignet_GenerateApiKeyKyc_Valid_Smoke_sid", "apiKey",
						generateRandomNumberString(20));

				writeAutoGeneratedId("Esignet_PartnerSelfRegistration_All_Valid_Smoke_sid", "partnerId",
						generateRandomNumberString(20));

				writeAutoGeneratedId("Esignet_PartnerSelfRegistration_Kyc_Valid_Smoke_sid", "partnerId",
						generateRandomNumberString(20));

				isvalueReplaced = true;
			}
		} else {
			isWaitRequired = true;
			testCaseDTO.setInputTemplate(AdminTestUtil.modifySchemaGenerateHbs(testCaseDTO.isRegenerateHbs()));
			String uin = JsonPrecondtion.getValueFromJson(
					RestClient.getRequestWithCookie(ApplnURI + "/v1/idgenerator/uin", MediaType.APPLICATION_JSON,
							MediaType.APPLICATION_JSON, COOKIENAME,
							new KernelAuthentication().getTokenByRole(testCaseDTO.getRole())).asString(),
					"response.uin");

			DateFormat dateFormatter = new SimpleDateFormat("yyyyMMddHHmmss");
			Calendar cal = Calendar.getInstance();
			String timestampValue = dateFormatter.format(cal.getTime());
			String genRid = "27847" + generateRandomNumberString(10) + timestampValue;

			String jsonInput = testCaseDTO.getInput();

			String inputJson = getJsonFromTemplate(jsonInput, testCaseDTO.getInputTemplate(), false);

			inputJson = inputJson.replace("$UIN$", uin);
			inputJson = inputJson.replace("$RID$", genRid);
			String phoneNumber = "";
			String email = testCaseName + "@mosip.net";
			if (inputJson.contains("$PHONENUMBERFORIDENTITY$")) {
				if (!phoneSchemaRegex.isEmpty())
					try {
						phoneNumber = genStringAsperRegex(phoneSchemaRegex);
					} catch (Exception e) {
						logger.error(e.getMessage());
					}
				inputJson = replaceKeywordWithValue(inputJson, "$PHONENUMBERFORIDENTITY$", phoneNumber);
				inputJson = replaceKeywordWithValue(inputJson, "$EMAILVALUE$", email);
			}
			
			inputJson = EsignetUtil.inputstringKeyWordHandeler(inputJson, testCaseName);

			response = postWithBodyAndCookie(ApplnURI + testCaseDTO.getEndPoint(), inputJson, COOKIENAME,
					testCaseDTO.getRole(), testCaseDTO.getTestCaseName());
			
			
			if (testCaseDTO.getTestCaseName().contains("_Pos")) {
				writeAutoGeneratedId(testCaseDTO.getTestCaseName(), "UIN", uin);
				writeAutoGeneratedId(testCaseDTO.getTestCaseName(), "RID", genRid);
				writeAutoGeneratedId(testCaseDTO.getTestCaseName(), "EMAIL", testCaseDTO.getTestCaseName() + "@mosip.net");
				writeAutoGeneratedId(testCaseDTO.getTestCaseName(), "PHONE", phoneNumber);
			}
			if (!phoneNumber.isEmpty())
				writeAutoGeneratedId(testCaseDTO.getTestCaseName(), "PHONE", phoneNumber);
		}

		Map<String, List<OutputValidationDto>> ouputValid = OutputValidationUtil.doJsonOutputValidation(
				response.asString(), getJsonFromTemplate(testCaseDTO.getOutput(), testCaseDTO.getOutputTemplate()),
				testCaseDTO, response.getStatusCode());
		Reporter.log(ReportUtil.getOutputValidationReport(ouputValid));

		if (!OutputValidationUtil.publishOutputResult(ouputValid))
			throw new AdminTestException("Failed at output validation");

	}

	/**
	 * The method ser current test name to result
	 * 
	 * @param result
	 */
	@AfterMethod(alwaysRun = true)
	public void setResultTestName(ITestResult result) {
		try {
			Field method = TestResult.class.getDeclaredField("m_method");
			method.setAccessible(true);
			method.set(result, result.getMethod().clone());
			BaseTestMethod baseTestMethod = (BaseTestMethod) result.getMethod();
			Field f = baseTestMethod.getClass().getSuperclass().getDeclaredField("m_methodName");
			f.setAccessible(true);
			f.set(baseTestMethod, testCaseName);
		} catch (Exception e) {
			Reporter.log("Exception : " + e.getMessage());
		}
	}

	@AfterClass(alwaysRun = true)
	public void waittime() {

		try {
			if (EsignetUtil.getIdentityPluginNameFromEsignetActuator().toLowerCase()
					.contains("idaauthenticatorimpl") == true && isWaitRequired == true) {
				logger.info("waiting for " + properties.getProperty("Delaytime")
						+ " mili secs after UIN Generation In IDREPO");
				Thread.sleep(Long.parseLong(properties.getProperty("Delaytime")));
			}

		} catch (Exception e) {
			logger.error("Exception : " + e.getMessage());
			Thread.currentThread().interrupt();
		}

	}
}
